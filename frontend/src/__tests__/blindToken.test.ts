// @vitest-environment node
import { RSABSSA } from '@cloudflare/blindrsa-ts';
import { fromBase64 } from '../security/base64';
import { obtainVotingToken, submitBallot } from '../security/blindToken';

/**
 * SEC-11 client side. A key pair generated here plays the server, signing with the
 * library's own blindSign. The Java server was checked against this same library
 * separately, so both halves agree on RFC 9474.
 */
const suite = RSABSSA.SHA384.PSS.Deterministic();
let keys: CryptoKeyPair;

beforeAll(async () => {
  keys = await RSABSSA.SHA384.generateKey({ modulusLength: 2048, publicExponent: Uint8Array.from([1, 0, 1]) });
});

describe('SEC-11 voting token (browser side)', () => {
  it('produces a token whose signature verifies against the election key', async () => {
    const token = await obtainVotingToken('MELBOURNE', keys.publicKey, (_d, blinded) =>
      suite.blindSign(keys.privateKey, blinded),
    );
    expect(await suite.verify(keys.publicKey, token.signature, token.serial)).toBe(true);
  });

  it('never sends the serial to the server', async () => {
    let seenByServer: Uint8Array | undefined;
    const token = await obtainVotingToken('MELBOURNE', keys.publicKey, (_d, blinded) => {
      seenByServer = blinded;
      return suite.blindSign(keys.privateKey, blinded);
    });
    expect(seenByServer).toBeDefined();
    expect(Buffer.from(seenByServer!).equals(Buffer.from(token.serial))).toBe(false);
    expect(Buffer.from(seenByServer!).includes(Buffer.from(token.serial))).toBe(false);
  });

  it('rejects a bad signature from the server', async () => {
    const fake = async () => crypto.getRandomValues(new Uint8Array(256));
    await expect(obtainVotingToken('MELBOURNE', keys.publicKey, fake)).rejects.toThrow();
  });

  it('submits serial and signature as base64, with the encrypted envelope', async () => {
    const token = await obtainVotingToken('MELBOURNE', keys.publicKey, (_d, blinded) =>
      suite.blindSign(keys.privateKey, blinded),
    );
    const sent: Array<{ serial: string; signature: string; encryptedBallot: string }> = [];
    await submitBallot(token, '{"alg":"x"}', async (body) => {
      sent.push(body);
    });
    expect(sent).toHaveLength(1);
    expect(fromBase64(sent[0].serial)).toEqual(token.serial);
    expect(fromBase64(sent[0].signature)).toEqual(token.signature);
    expect(sent[0].encryptedBallot).toBe('{"alg":"x"}');
  });
});
