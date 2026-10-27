/**
 * SEC-18: sample verifier for signed election results, in the browser.
 *
 * Same rules as the Java SampleResultsVerifier, so a media outlet can check results
 * without trusting this server:
 *   1. keyId matches the commission's published public key
 *   2. the Ed25519 signature over the canonical JSON payload verifies
 *   3. issuedAt is not in the future (5 minutes clock skew allowed)
 *   4. the sequence is not LOWER than the newest one already accepted (replay)
 *   5. the same sequence never comes with different content
 */

export interface ResultsPayload {
  schema: string;
  electionId: number;
  sequence: number;
  issuedAt: string;
  keyId: string;
  results: {
    tallyId: number;
    contest: string;
    region: string;
    elected: { id: number; name: string }[];
    documentSha256: string;
  }[];
  auditHead: { seq: number; hash: string };
}

export interface Publication {
  payload: ResultsPayload;
  signature: string;
}

/** What the verifier remembers about the newest accepted publication of an election. */
export interface AcceptedState {
  sequence: number;
  issuedAt: string;
  digest: string;
}

export class RejectedPublication extends Error {
  constructor(reason: string) {
    super(reason);
    this.name = 'RejectedPublication';
  }
}

const CLOCK_SKEW_MS = 5 * 60 * 1000;

/** Keys sorted, no spaces: must match ResultsSigning.canonicalBytes in Java exactly. */
export function canonicalJson(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(',')}]`;
  }
  if (value !== null && typeof value === 'object') {
    const entries = Object.keys(value as Record<string, unknown>)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${canonicalJson((value as Record<string, unknown>)[k])}`);
    return `{${entries.join(',')}}`;
  }
  return JSON.stringify(value);
}

function bytes(text: string): Uint8Array {
  return new TextEncoder().encode(text);
}

function fromBase64(b64: string): Uint8Array {
  const raw = atob(b64);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

async function sha256Hex(subtle: SubtleCrypto, data: Uint8Array): Promise<string> {
  const digest = new Uint8Array(await subtle.digest('SHA-256', data));
  return Array.from(digest, (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Same fingerprint as ResultsSigning.keyId in Java. */
export async function keyId(publicKeyPem: string, subtle: SubtleCrypto = crypto.subtle): Promise<string> {
  return (await sha256Hex(subtle, bytes(publicKeyPem.trim()))).slice(0, 16);
}

/**
 * Returns the new state to remember if the publication is genuine and fresh; otherwise
 * throws RejectedPublication with the reason.
 */
export async function verifyPublication(
  publication: Publication,
  trustedPublicKeyPem: string,
  previous: AcceptedState | null,
  now: Date = new Date(),
  subtle: SubtleCrypto = crypto.subtle,
): Promise<AcceptedState> {
  const { payload, signature } = publication ?? ({} as Publication);
  if (!payload || typeof payload !== 'object' || typeof signature !== 'string') {
    throw new RejectedPublication("not a results publication (needs 'payload' and 'signature')");
  }

  // 1 + 2: genuine?
  if (payload.keyId !== (await keyId(trustedPublicKeyPem, subtle))) {
    throw new RejectedPublication("signed with a different key than the commission's published key");
  }
  const der = fromBase64(trustedPublicKeyPem.replace(/-----[^-]+-----/g, '').replace(/\s/g, ''));
  const key = await subtle.importKey('spki', der, { name: 'Ed25519' }, false, ['verify']);
  const message = bytes(canonicalJson(payload));
  let valid = false;
  try {
    valid = await subtle.verify({ name: 'Ed25519' }, key, fromBase64(signature), message);
  } catch {
    valid = false;
  }
  if (!valid) {
    throw new RejectedPublication('SIGNATURE INVALID - the payload was forged or modified');
  }

  // 3 + 4 + 5: fresh?
  const issuedAt = Date.parse(payload.issuedAt);
  if (Number.isNaN(issuedAt) || issuedAt - now.getTime() > CLOCK_SKEW_MS) {
    throw new RejectedPublication('issuedAt is in the future');
  }
  const digest = await sha256Hex(subtle, message);
  if (previous) {
    if (payload.sequence < previous.sequence) {
      throw new RejectedPublication(
        `REPLAY: sequence ${payload.sequence} is lower than the already-accepted sequence ${previous.sequence}`,
      );
    }
    if (payload.sequence === previous.sequence && digest !== previous.digest) {
      throw new RejectedPublication(`CONFLICT: a different payload with sequence ${payload.sequence} exists`);
    }
    if (issuedAt < Date.parse(previous.issuedAt)) {
      throw new RejectedPublication('issuedAt is earlier than the last accepted publication');
    }
  }
  return { sequence: payload.sequence, issuedAt: payload.issuedAt, digest };
}