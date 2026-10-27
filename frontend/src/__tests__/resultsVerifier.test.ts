import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { canonicalJson, verifyPublication, type Publication } from '../security/resultsVerifier';

// Signed by the Java backend (ResultsSigning), so this also proves Java and TypeScript agree.
// Path is relative to frontend/, where npm test runs.
const fixture = JSON.parse(readFileSync('src/__tests__/fixtures/signed-results.json', 'utf8'));

/**
 * SEC-18: the browser verifier against publications SIGNED BY THE JAVA BACKEND
 * (fixtures/signed-results.json was produced by ResultsSigning in Java). This proves both
 * sides build exactly the same canonical JSON, including non-ASCII names.
 */
const subtle = webcrypto.subtle as unknown as SubtleCrypto;
const pem = fixture.publicKeyPem;
const v1 = fixture.v1 as Publication;
const v2 = fixture.v2 as Publication;
const now = new Date('2026-05-02T12:30:00Z');

const copy = (p: Publication): Publication => JSON.parse(JSON.stringify(p));

describe('SEC-18 results verifier', () => {
  it('verifies a Java-signed payload with the published public key', async () => {
    const state = await verifyPublication(v1, pem, null, now, subtle);
    expect(state.sequence).toBe(1);
  });

  it('rejects a payload with a lower sequence number (replay)', async () => {
    const afterV2 = await verifyPublication(v2, pem, null, now, subtle);
    await expect(verifyPublication(v1, pem, afterV2, now, subtle)).rejects.toThrow(
      'REPLAY: sequence 1 is lower than the already-accepted sequence 2',
    );
  });

  it('accepts newer versions in order', async () => {
    const afterV1 = await verifyPublication(v1, pem, null, now, subtle);
    expect((await verifyPublication(v2, pem, afterV1, now, subtle)).sequence).toBe(2);
  });

  it('rejects a changed winner', async () => {
    const forged = copy(v2);
    forged.payload.results[0].elected[0].name = 'SOMEONE ELSE';
    await expect(verifyPublication(forged, pem, null, now, subtle)).rejects.toThrow('SIGNATURE INVALID');
  });

  it('rejects an edited sequence number', async () => {
    const bumped = copy(v1);
    bumped.payload.sequence = 99;
    await expect(verifyPublication(bumped, pem, null, now, subtle)).rejects.toThrow('SIGNATURE INVALID');
  });

  it('rejects a payload signed by another key', async () => {
    const other = await subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']);
    const spki = new Uint8Array(await subtle.exportKey('spki', (other as CryptoKeyPair).publicKey));
    const otherPem = `-----BEGIN PUBLIC KEY-----\n${btoa(String.fromCharCode(...spki))}\n-----END PUBLIC KEY-----\n`;
    await expect(verifyPublication(v1, otherPem, null, now, subtle)).rejects.toThrow('different key');
  });

  it('builds canonical JSON with sorted keys and no spaces', () => {
    expect(canonicalJson({ b: 1, a: [{ d: 'x', c: null }] })).toBe('{"a":[{"c":null,"d":"x"}],"b":1}');
  });
});