// SEC-12: seals a ballot IN THE BROWSER to the election public key (WebCrypto) before it is sent.
// The envelope names its algorithm and key id so the cipher can be upgraded later: add one entry to
// SEALERS here and register the same algorithm id on the server (com.evp.crypto.AlgorithmRegistry).
// Must stay byte-compatible with backend/.../crypto/RsaOaepAesGcm.java.

export interface ElectionKeyInfo {
  kid: string;
  alg: string;
  publicKey: string; // base64 SPKI
  fingerprint: string; // hex SHA-256 of the SPKI bytes
}

export interface BallotEnvelope {
  v: 1;
  alg: string;
  kid: string;
  ek: string; // wrapped content key
  iv: string; // AES-GCM nonce
  ct: string; // ciphertext || tag
}

type Sealer = (preferences: number[], key: ElectionKeyInfo) => Promise<BallotEnvelope>;

const enc = new TextEncoder();

const toB64 = (buf: ArrayBuffer | Uint8Array): string => {
  const a = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (const b of a) s += String.fromCharCode(b);
  return btoa(s);
};
// Cast: older and newer TypeScript lib typings disagree on Uint8Array<ArrayBufferLike> vs BufferSource.
const fromB64 = (b64: string): BufferSource => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)) as BufferSource;

/** Header bound into the AES-GCM tag (additional authenticated data): relabelling alg/kid breaks decryption. */
const aad = (v: number, alg: string, kid: string): BufferSource => enc.encode(`${v}|${alg}|${kid}`) as BufferSource;

const RSA_GCM = 'RSA-OAEP-256+A256GCM';

const SEALERS: Record<string, Sealer> = {
  [RSA_GCM]: async (preferences, key) => {
    const subtle = globalThis.crypto.subtle;
    const pub = await subtle.importKey('spki', fromB64(key.publicKey), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt']);
    const cek = await subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt']);
    const iv = globalThis.crypto.getRandomValues(new Uint8Array(12));
    const ct = await subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: aad(1, key.alg, key.kid) },
      cek,
      enc.encode(JSON.stringify(preferences)),
    );
    const ek = await subtle.encrypt({ name: 'RSA-OAEP' }, pub, await subtle.exportKey('raw', cek));
    return { v: 1, alg: key.alg, kid: key.kid, ek: toB64(ek), iv: toB64(iv), ct: toB64(ct) };
  },
};

export const supportedAlgorithms = (): string[] => Object.keys(SEALERS);

/** Test hook / upgrade path: register another sealer without touching callers. */
export function registerSealer(alg: string, sealer: Sealer): void {
  SEALERS[alg] = sealer;
}

/** Preferences are candidate ids, most preferred first. Every candidate must be ranked exactly once. */
export function validatePreferences(preferences: number[], candidateIds: number[]): boolean {
  return (
    preferences.length === candidateIds.length &&
    new Set(preferences).size === preferences.length &&
    preferences.every((p) => Number.isInteger(p) && candidateIds.includes(p))
  );
}

export async function sealBallot(preferences: number[], key: ElectionKeyInfo): Promise<BallotEnvelope> {
  const sealer = SEALERS[key.alg];
  if (!sealer) throw new Error('Unsupported encryption algorithm');
  if (!Array.isArray(preferences) || preferences.length === 0 || !preferences.every(Number.isInteger)) {
    throw new Error('Invalid ballot');
  }
  return sealer(preferences, key);
}

/** Fetches the PUBLIC election key. The caller shows key.fingerprint so voters can compare it with the published one. */
export async function fetchElectionKey(fetchFn: typeof fetch = fetch): Promise<ElectionKeyInfo> {
  const res = await fetchFn('/api/election/key', { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error('Could not load the election key');
  const k = (await res.json()) as ElectionKeyInfo;
  if (!k.kid || !k.alg || !k.publicKey || !/^[0-9a-f]{64}$/.test(k.fingerprint)) throw new Error('Bad election key');
  return k;
}

/** Drop-in for VotingPanel's buildEncryptedBallot: returns the sealed envelope and the key fingerprint used. */
export async function buildEncryptedBallot(
  preferences: number[],
  fetchFn: typeof fetch = fetch,
): Promise<{ envelope: BallotEnvelope; fingerprint: string }> {
  const key = await fetchElectionKey(fetchFn);
  return { envelope: await sealBallot(preferences, key), fingerprint: key.fingerprint };
}
