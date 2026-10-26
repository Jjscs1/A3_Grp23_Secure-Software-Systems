/**
 * SEC-11: browser side of the anonymous, single-use voting token
 * (RFC 9474, RSABSSA-SHA384-PSS-Deterministic, via @cloudflare/blindrsa-ts).
 *
 *  1. Pick a random 32 byte serial. It stays in the browser.
 *  2. Blind it with the election public key and send only the blinded value, while
 *     logged in. The server checks eligibility (SEC-10), sets has-voted and signs.
 *  3. Unblind. The result is a normal signature on the serial that the server has
 *     never seen, so it cannot link the token back to this voter.
 *  4. Submit the ballot with the serial and signature, without the login token.
 */
import { RSABSSA } from '@cloudflare/blindrsa-ts';
import { apiFetch } from '../api/http';
import { fromBase64, toBase64 } from './base64';

export const SUITE_NAME = 'RSABSSA-SHA384-PSS-Deterministic';
const suite = RSABSSA.SHA384.PSS.Deterministic();

export interface VotingToken {
  serial: Uint8Array;
  signature: Uint8Array;
}

/** Sends a blinded value to be signed. Swappable in tests. */
export type BlindSignRequester = (division: string, blindedMessage: Uint8Array) => Promise<Uint8Array>;

/** Sends a finished ballot. Swappable in tests. */
export type BallotSender = (body: { serial: string; signature: string; encryptedBallot: string }) => Promise<void>;

export async function importElectionKey(spkiBase64: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('spki', fromBase64(spkiBase64), { name: 'RSA-PSS', hash: 'SHA-384' }, true, [
    'verify',
  ]);
}

export async function fetchElectionKey(): Promise<CryptoKey> {
  const response = await apiFetch<{ suite: string; spki: string }>('/api/tokens/public-key', { auth: false });
  if (response.suite !== SUITE_NAME) {
    throw new Error(`Unsupported token suite ${response.suite}`);
  }
  return importElectionKey(response.spki);
}

export const requestBlindSignature: BlindSignRequester = async (division, blindedMessage) => {
  const response = await apiFetch<{ blindSignature: string }>('/api/tokens', {
    method: 'POST',
    json: { division, blindedMessage: toBase64(blindedMessage) },
  });
  return fromBase64(response.blindSignature);
};

/**
 * Runs steps 1 to 3. finalize() also checks the server's signature, so a wrong or
 * tampered response is rejected here rather than when the ballot is submitted.
 */
export async function obtainVotingToken(
  division: string,
  electionKey: CryptoKey,
  requestSignature: BlindSignRequester = requestBlindSignature,
): Promise<VotingToken> {
  const serial = crypto.getRandomValues(new Uint8Array(32));
  const prepared = suite.prepare(serial); // the deterministic variant leaves it unchanged
  const { blindedMsg, inv } = await suite.blind(electionKey, prepared);
  const blindSignature = await requestSignature(division, blindedMsg);
  const signature = await suite.finalize(electionKey, prepared, blindSignature, inv);
  return { serial: prepared, signature };
}

export const postBallot: BallotSender = async (body) => {
  // Anonymous on purpose: no access token, no cookies.
  await apiFetch('/api/ballots', { method: 'POST', auth: false, credentials: 'omit', json: body });
};

/** Step 4. The encrypted envelope comes from SEC-12 (Ballot casting and cryptography member). */
export async function submitBallot(
  token: VotingToken,
  encryptedBallot: string,
  send: BallotSender = postBallot,
): Promise<void> {
  await send({ serial: toBase64(token.serial), signature: toBase64(token.signature), encryptedBallot });
}
