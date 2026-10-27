// @vitest-environment node
import { describe, expect, it } from 'vitest';
import {
  buildEncryptedBallot,
  fetchElectionKey,
  registerSealer,
  sealBallot,
  supportedAlgorithms,
  validatePreferences,
} from './ballotEnvelope';
import type { ElectionKeyInfo } from './ballotEnvelope';

const subtle = globalThis.crypto.subtle;
const b64 = (u: ArrayBuffer | Uint8Array) => Buffer.from(u instanceof Uint8Array ? u : new Uint8Array(u)).toString('base64');
const unb64 = (s: string) => new Uint8Array(Buffer.from(s, 'base64'));
const hex = (u: ArrayBuffer) => Buffer.from(u).toString('hex');

async function newKey(kid = 'k1', alg = 'RSA-OAEP-256+A256GCM') {
  const pair = await subtle.generateKey(
    { name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['encrypt', 'decrypt'],
  );
  const spki = await subtle.exportKey('spki', pair.publicKey);
  const info: ElectionKeyInfo = { kid, alg, publicKey: b64(spki), fingerprint: hex(await subtle.digest('SHA-256', spki)) };
  return { info, priv: pair.privateKey };
}

async function open(env: { v: number; alg: string; kid: string; ek: string; iv: string; ct: string }, priv: CryptoKey) {
  const cek = await subtle.decrypt({ name: 'RSA-OAEP' }, priv, unb64(env.ek));
  const key = await subtle.importKey('raw', cek, { name: 'AES-GCM' }, false, ['decrypt']);
  const pt = await subtle.decrypt(
    { name: 'AES-GCM', iv: unb64(env.iv), additionalData: new TextEncoder().encode(`${env.v}|${env.alg}|${env.kid}`) },
    key,
    unb64(env.ct),
  );
  return JSON.parse(new TextDecoder().decode(pt));
}

describe('sealBallot', () => {
  it('produces an envelope that names its algorithm and key and opens with the private key', async () => {
    const { info, priv } = await newKey();
    const env = await sealBallot([3, 1, 2], info);
    expect(env).toMatchObject({ v: 1, alg: 'RSA-OAEP-256+A256GCM', kid: 'k1' });
    expect(Object.keys(env).sort()).toEqual(['alg', 'ct', 'ek', 'iv', 'kid', 'v']);
    expect(await open(env, priv)).toEqual([3, 1, 2]);
  });

  it('never exposes the plaintext preferences', async () => {
    const { info } = await newKey();
    const env = await sealBallot([111, 222, 333], info);
    const raw = JSON.stringify(env);
    expect(raw).not.toContain('111');
    expect(raw).not.toContain('preferences');
  });

  it('uses a fresh content key and IV every time', async () => {
    const { info } = await newKey();
    const a = await sealBallot([1, 2, 3], info);
    const b = await sealBallot([1, 2, 3], info);
    expect(a.ct).not.toBe(b.ct);
    expect(a.ek).not.toBe(b.ek);
    expect(a.iv).not.toBe(b.iv);
  });

  it('has the sizes the server checks: ek = modulus bytes, iv = 12', async () => {
    const { info } = await newKey();
    const env = await sealBallot([1, 2, 3], info);
    expect(unb64(env.ek).length).toBe(256);
    expect(unb64(env.iv).length).toBe(12);
  });

  it('tampering with the ciphertext is detected (GCM)', async () => {
    const { info, priv } = await newKey();
    const env = await sealBallot([1, 2, 3], info);
    const ct = unb64(env.ct);
    ct[0] ^= 1;
    await expect(open({ ...env, ct: b64(ct) }, priv)).rejects.toThrow();
  });

  it('relabelling the algorithm or key id breaks decryption (header is authenticated)', async () => {
    const { info, priv } = await newKey();
    const env = await sealBallot([1, 2, 3], info);
    await expect(open({ ...env, alg: 'OTHER-ALG' }, priv)).rejects.toThrow();
    await expect(open({ ...env, kid: 'k9' }, priv)).rejects.toThrow();
  });

  it('a different election key cannot open it', async () => {
    const a = await newKey();
    const b = await newKey();
    const env = await sealBallot([1, 2, 3], a.info);
    await expect(open(env, b.priv)).rejects.toThrow();
  });

  it('refuses an algorithm it does not know', async () => {
    const { info } = await newKey('k1', 'ROT13');
    await expect(sealBallot([1, 2, 3], info)).rejects.toThrow('Unsupported encryption algorithm');
  });

  it('refuses an empty or non-integer ballot', async () => {
    const { info } = await newKey();
    await expect(sealBallot([], info)).rejects.toThrow('Invalid ballot');
    await expect(sealBallot([1.5], info)).rejects.toThrow('Invalid ballot');
  });

  it('crypto-agility: a new algorithm is added by registering one sealer', async () => {
    const { info } = await newKey('k2', 'RSA-OAEP-256+A256GCM-V2');
    await expect(sealBallot([1], info)).rejects.toThrow();
    registerSealer('RSA-OAEP-256+A256GCM-V2', async (_p, k) => ({ v: 1, alg: k.alg, kid: k.kid, ek: 'AAAA', iv: 'AAAA', ct: 'AAAA' }));
    expect((await sealBallot([1], info)).alg).toBe('RSA-OAEP-256+A256GCM-V2');
    expect(supportedAlgorithms()).toContain('RSA-OAEP-256+A256GCM');
  });
});

describe('validatePreferences', () => {
  it('requires every candidate exactly once', () => {
    expect(validatePreferences([3, 1, 2], [1, 2, 3])).toBe(true);
    expect(validatePreferences([1, 2], [1, 2, 3])).toBe(false);
    expect(validatePreferences([1, 1, 2], [1, 2, 3])).toBe(false);
    expect(validatePreferences([1, 2, 9], [1, 2, 3])).toBe(false);
  });
});

describe('fetching the election key', () => {
  const fakeFetch = (status: number, body: unknown) =>
    (async () => ({ ok: status === 200, status, json: async () => body })) as unknown as typeof fetch;

  it('returns the public key info and builds the sealed ballot with its fingerprint', async () => {
    const { info, priv } = await newKey();
    const { envelope, fingerprint } = await buildEncryptedBallot([2, 1], fakeFetch(200, info));
    expect(fingerprint).toBe(info.fingerprint);
    expect(await open(envelope, priv)).toEqual([2, 1]);
  });

  it('rejects an error response or a malformed key', async () => {
    await expect(fetchElectionKey(fakeFetch(500, {}))).rejects.toThrow('Could not load the election key');
    await expect(fetchElectionKey(fakeFetch(200, { kid: 'k', alg: 'a', publicKey: 'x', fingerprint: 'zz' }))).rejects.toThrow('Bad election key');
  });
});
