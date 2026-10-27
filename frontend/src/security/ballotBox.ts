/**
 * SEC-14 browser-side verification. The voter's browser recomputes everything itself, so it does not have to
 * trust the server: leaf hash, Merkle path (RFC 6962 / 9162) and the Ed25519 signature on the tree head.
 * Nothing here reveals the vote: the tracking code is a receipt plus a hash of the sealed envelope.
 */

export interface Checkpoint {
  alg: string;
  kid: string;
  size: number;
  root: string;
  ts: string;
  sig: string;
}

export interface SignerInfo {
  kid: string;
  alg: string;
  publicKey: string; // base64 X.509 SPKI
  fingerprint: string;
}

export interface InclusionProof {
  index: number;
  size: number;
  leaf: string;
  path: string[];
  checkpoint: Checkpoint;
}

export interface Envelope {
  v: number;
  alg: string;
  kid: string;
  ek: string;
  iv: string;
  ct: string;
}

export type VerifyResult =
  | { ok: true; index: number; size: number; root: string; ts: string; keyFingerprint: string }
  | { ok: false; reason: string };

const enc = new TextEncoder();

export function hexToBytes(hex: string): Uint8Array {
  if (!/^([0-9a-f]{2})+$/i.test(hex)) throw new Error('bad hex');
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

export function bytesToHex(b: Uint8Array): string {
  return Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

async function sha256(data: Uint8Array): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', data as BufferSource));
}

export const leafHash = (data: Uint8Array) => sha256(concat(new Uint8Array([0]), data));
export const nodeHash = (l: Uint8Array, r: Uint8Array) => sha256(concat(new Uint8Array([1]), l, r));

/** Same canonical form as the server: JSON array [v, alg, kid, ek, iv, ct]. */
export async function envelopeHash(env: Envelope): Promise<string> {
  const canonical = JSON.stringify([env.v, env.alg, env.kid, env.ek, env.iv, env.ct]);
  return bytesToHex(await sha256(enc.encode(canonical)));
}

export async function leafHex(receipt: string, envHashHex: string): Promise<string> {
  return bytesToHex(await leafHash(enc.encode(`evote-ballot-v1|${receipt}|${envHashHex}`)));
}

/** The code shown to the voter after casting. Safe to keep: it cannot reveal the preferences. */
export async function makeTrackingCode(receipt: string, env: Envelope): Promise<string> {
  return `${receipt}.${await envelopeHash(env)}`;
}

export function parseTrackingCode(code: string): { receipt: string; envelopeHash: string } | null {
  const i = code.trim().lastIndexOf('.');
  if (i < 1) return null;
  const receipt = code.trim().slice(0, i);
  const h = code.trim().slice(i + 1).toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(h) || receipt.length > 200) return null;
  return { receipt, envelopeHash: h };
}

export async function verifyInclusion(
  leaf: Uint8Array,
  index: number,
  size: number,
  path: Uint8Array[],
  root: Uint8Array,
): Promise<boolean> {
  if (!Number.isInteger(index) || !Number.isInteger(size) || index < 0 || index >= size) return false;
  let fn = index;
  let sn = size - 1;
  let r = leaf;
  for (const p of path) {
    if (sn === 0) return false;
    if ((fn & 1) === 1 || fn === sn) {
      r = await nodeHash(p, r);
      if ((fn & 1) === 0) {
        while ((fn & 1) === 0 && fn !== 0) {
          fn >>= 1;
          sn >>= 1;
        }
      }
    } else {
      r = await nodeHash(r, p);
    }
    fn >>= 1;
    sn >>= 1;
  }
  return sn === 0 && bytesToHex(r) === bytesToHex(root);
}

export function checkpointMessage(c: Checkpoint): string {
  return `ballotbox-v1|${c.alg}|${c.kid}|${c.size}|${c.root}|${c.ts}`;
}

function b64(s: string): Uint8Array {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export async function verifyCheckpointSignature(c: Checkpoint, signer: SignerInfo): Promise<boolean> {
  if (c.alg !== 'Ed25519' || signer.alg !== 'Ed25519' || c.kid !== signer.kid) return false;
  try {
    const key = await crypto.subtle.importKey('spki', b64(signer.publicKey) as BufferSource, { name: 'Ed25519' }, false, ['verify']);
    return await crypto.subtle.verify({ name: 'Ed25519' }, key, b64(c.sig) as BufferSource, enc.encode(checkpointMessage(c)));
  } catch {
    return false;
  }
}

/**
 * Pure verification of a server answer: recompute the leaf from the voter's own code, check the Merkle path
 * to the root, check the root is the one in the signed checkpoint, check the signature with the public key.
 * `pinnedFingerprint` (optional) is the key fingerprint published out-of-band by the AEC / scrutineers.
 */
export async function verifyTrackingCode(
  code: string,
  proof: InclusionProof,
  signer: SignerInfo,
  pinnedFingerprint?: string,
): Promise<VerifyResult> {
  const parsed = parseTrackingCode(code);
  if (!parsed) return { ok: false, reason: 'That tracking code is not valid.' };
  const mine = await leafHex(parsed.receipt, parsed.envelopeHash);
  if (proof.leaf !== mine) return { ok: false, reason: 'The server returned a proof for a different ballot.' };
  if (pinnedFingerprint && signer.fingerprint.toLowerCase() !== pinnedFingerprint.toLowerCase())
    return { ok: false, reason: 'The signing key does not match the published fingerprint.' };
  if (proof.checkpoint.size !== proof.size) return { ok: false, reason: 'Proof and signed tree size disagree.' };
  let ok: boolean;
  try {
    ok = await verifyInclusion(hexToBytes(mine), proof.index, proof.size, proof.path.map(hexToBytes), hexToBytes(proof.checkpoint.root));
  } catch {
    ok = false;
  }
  if (!ok) return { ok: false, reason: 'Your ballot is NOT proven to be in the ballot box. Contact the scrutineers.' };
  if (!(await verifyCheckpointSignature(proof.checkpoint, signer)))
    return { ok: false, reason: 'The ballot box signature is invalid. Contact the scrutineers.' };
  return { ok: true, index: proof.index, size: proof.size, root: proof.checkpoint.root, ts: proof.checkpoint.ts, keyFingerprint: signer.fingerprint };
}

async function getJson<T>(url: string): Promise<T> {
  const r = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'omit' });
  if (!r.ok) throw new Error(String(r.status));
  return (await r.json()) as T;
}

/** Looks the ballot up (leaf hash only, never the code itself) and verifies the answer locally. */
export async function checkTrackingCode(code: string, pinnedFingerprint?: string): Promise<VerifyResult> {
  const parsed = parseTrackingCode(code);
  if (!parsed) return { ok: false, reason: 'That tracking code is not valid.' };
  const leaf = await leafHex(parsed.receipt, parsed.envelopeHash);
  try {
    const [proof, signer] = await Promise.all([
      getJson<InclusionProof>(`/api/ballotbox/proof?leaf=${leaf}`),
      getJson<SignerInfo>('/api/ballotbox/key'),
    ]);
    return await verifyTrackingCode(code, proof, signer, pinnedFingerprint);
  } catch {
    return { ok: false, reason: 'Not found yet. New ballots are added every 5 minutes; try again shortly.' };
  }
}
