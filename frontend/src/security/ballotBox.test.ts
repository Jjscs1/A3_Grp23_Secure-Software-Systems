import { describe, expect, it } from 'vitest';
import {
  bytesToHex, checkpointMessage, envelopeHash, hexToBytes, leafHash, leafHex, makeTrackingCode, nodeHash,
  parseTrackingCode, verifyInclusion, verifyTrackingCode, type Checkpoint, type Envelope, type InclusionProof, type SignerInfo,
} from './ballotBox';

const ENV: Envelope = { v: 1, alg: 'A', kid: 'k', ek: 'e', iv: 'i', ct: 'c' };
const te = new TextEncoder();

// Produced by the Java side (BallotLeafTest.printVectorForBrowserTest): proves both sides compute the same leaf.
const JAVA_ENV_HASH = 'e497aec97676062ab964e58d28caaa451dd1404d955765cd576fa5c5be2a764b';
const JAVA_LEAF = 'ed32b1ecb4dbe4c454bfcf7388fd237fe1ff43a95793a89c68915340ca9ff0db';

async function tree(leaves: Uint8Array[]): Promise<{ root: Uint8Array; path: (i: number) => Promise<Uint8Array[]> }> {
  const split = (n: number) => { let k = 1; while (k * 2 < n) k *= 2; return k; };
  const root = async (l: Uint8Array[]): Promise<Uint8Array> =>
    l.length === 1 ? l[0] : nodeHash(await root(l.slice(0, split(l.length))), await root(l.slice(split(l.length))));
  const path = async (m: number, l: Uint8Array[]): Promise<Uint8Array[]> => {
    if (l.length === 1) return [];
    const k = split(l.length);
    return m < k ? [...(await path(m, l.slice(0, k))), await root(l.slice(k))] : [...(await path(m - k, l.slice(k))), await root(l.slice(0, k))];
  };
  return { root: await root(leaves), path: (i) => path(i, leaves) };
}

async function signedProof(n: number, idx: number, receipt: string) {
  const kp = (await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify'])) as CryptoKeyPair;
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', kp.publicKey));
  const signer: SignerInfo = { kid: 'bb-1', alg: 'Ed25519', publicKey: btoa(String.fromCharCode(...spki)), fingerprint: 'ff' };
  const mine = await leafHex(receipt, await envelopeHash(ENV));
  const leaves: Uint8Array[] = [];
  for (let i = 0; i < n; i++) leaves.push(i === idx ? hexToBytes(mine) : await leafHash(te.encode('other-' + i)));
  const t = await tree(leaves);
  const cp: Checkpoint = { alg: 'Ed25519', kid: 'bb-1', size: n, root: bytesToHex(t.root), ts: '2026-10-27T00:00:00Z', sig: '' };
  const sig = new Uint8Array(await crypto.subtle.sign({ name: 'Ed25519' }, kp.privateKey, te.encode(checkpointMessage(cp))));
  cp.sig = btoa(String.fromCharCode(...sig));
  const proof: InclusionProof = { index: idx, size: n, leaf: mine, path: (await t.path(idx)).map(bytesToHex), checkpoint: cp };
  return { proof, signer, kp };
}

describe('SEC-14 browser verification', () => {
  it('computes the same envelope hash and leaf as the Java server', async () => {
    expect(await envelopeHash(ENV)).toBe(JAVA_ENV_HASH);
    expect(await leafHex('r1', JAVA_ENV_HASH)).toBe(JAVA_LEAF);
  });

  it('tracking code round-trips and rejects junk', async () => {
    const code = await makeTrackingCode('REC-123', ENV);
    expect(parseTrackingCode(code)).toEqual({ receipt: 'REC-123', envelopeHash: JAVA_ENV_HASH });
    expect(parseTrackingCode('nodot')).toBeNull();
    expect(parseTrackingCode('r.' + 'zz'.repeat(32))).toBeNull();
    expect(parseTrackingCode('r.abcd')).toBeNull();
  });

  it('code contains nothing from the ciphertext', async () => {
    expect(await makeTrackingCode('R', ENV)).not.toContain(ENV.ct + ENV.ek);
  });

  it('every position in trees of size 1..12 verifies', async () => {
    for (let n = 1; n <= 12; n++) {
      const leaves = await Promise.all(Array.from({ length: n }, (_, i) => leafHash(te.encode('b' + i))));
      const t = await tree(leaves);
      for (let i = 0; i < n; i++) expect(await verifyInclusion(leaves[i], i, n, await t.path(i), t.root)).toBe(true);
    }
  });

  it('accepts a genuine signed proof', async () => {
    const { proof, signer } = await signedProof(9, 4, 'R1');
    const r = await verifyTrackingCode(await makeTrackingCode('R1', ENV), proof, signer);
    expect(r.ok).toBe(true);
  });

  it('rejects a proof for someone else\'s ballot', async () => {
    const { proof, signer } = await signedProof(9, 4, 'R1');
    const r = await verifyTrackingCode(await makeTrackingCode('R2', ENV), proof, signer);
    expect(r).toMatchObject({ ok: false });
  });

  it('rejects a tampered path, root, size, signature and wrong signer key', async () => {
    const { proof, signer } = await signedProof(9, 4, 'R1');
    const code = await makeTrackingCode('R1', ENV);
    const flip = (h: string) => (h[0] === '0' ? '1' : '0') + h.slice(1);
    expect((await verifyTrackingCode(code, { ...proof, path: [flip(proof.path[0]), ...proof.path.slice(1)] }, signer)).ok).toBe(false);
    expect((await verifyTrackingCode(code, { ...proof, checkpoint: { ...proof.checkpoint, root: flip(proof.checkpoint.root) } }, signer)).ok).toBe(false);
    expect((await verifyTrackingCode(code, { ...proof, size: 10, checkpoint: { ...proof.checkpoint, size: 10 } }, signer)).ok).toBe(false);
    const sigBytes = atob(proof.checkpoint.sig);
    const badSig = btoa(String.fromCharCode(sigBytes.charCodeAt(0) ^ 1) + sigBytes.slice(1));
    expect((await verifyTrackingCode(code, { ...proof, checkpoint: { ...proof.checkpoint, sig: badSig } }, signer)).ok).toBe(false);
    const other = await signedProof(9, 4, 'R1');
    expect((await verifyTrackingCode(code, proof, other.signer)).ok).toBe(false);
  });

  it('rejects an unexpected signing-key fingerprint when one is pinned', async () => {
    const { proof, signer } = await signedProof(5, 2, 'R1');
    const code = await makeTrackingCode('R1', ENV);
    expect((await verifyTrackingCode(code, proof, signer, 'ff')).ok).toBe(true);
    expect((await verifyTrackingCode(code, proof, signer, 'aa')).ok).toBe(false);
  });

  it('rejects a downgraded signature algorithm', async () => {
    const { proof, signer } = await signedProof(5, 2, 'R1');
    const r = await verifyTrackingCode(await makeTrackingCode('R1', ENV), { ...proof, checkpoint: { ...proof.checkpoint, alg: 'none' } }, signer);
    expect(r.ok).toBe(false);
  });
});
