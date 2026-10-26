import { useState } from 'react';
import { ApiError } from '../api/http';
import { fetchElectionKey, obtainVotingToken, submitBallot, type VotingToken } from '../security/blindToken';

/** SEC-10: plain language for each reason the server can refuse a ballot. */
export const ELIGIBILITY_MESSAGES: Record<string, string> = {
  NOT_ENROLLED: 'You are not on the electoral roll, so a ballot cannot be issued.',
  NOT_A_CITIZEN: 'Only Australian citizens can vote in federal elections.',
  UNDER_18: 'You must be 18 or over on polling day to vote.',
  WRONG_DIVISION: 'You are enrolled in a different division. Choose your enrolled division.',
  ALREADY_VOTED: 'Our records show a ballot has already been issued to you.',
};

interface Props {
  division: string;
  /**
   * Returns the encrypted ballot envelope. Supplied by the SEC-12 implementation
   * (Ballot casting and cryptography member). Preferences must never be sent in plain text.
   */
  buildEncryptedBallot: () => Promise<string>;
}

/** SEC-10 and SEC-11: get an anonymous token after the eligibility check, then cast anonymously. */
export function VotingPanel({ division, buildEncryptedBallot }: Props) {
  const [token, setToken] = useState<VotingToken | null>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [cast, setCast] = useState(false);

  async function getToken() {
    setBusy(true);
    setMessage('');
    try {
      const key = await fetchElectionKey();
      setToken(await obtainVotingToken(division, key));
      setMessage('You are eligible. Your ballot is ready.');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'NOT_ELIGIBLE' && e.detail) {
        setMessage(ELIGIBILITY_MESSAGES[e.detail] ?? 'A ballot cannot be issued.');
      } else if (e instanceof ApiError && e.status === 401) {
        setMessage('Please sign in first.');
      } else {
        setMessage('Something went wrong getting your ballot. Please try again or ask a polling official.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function castBallot() {
    if (!token) {
      return;
    }
    setBusy(true);
    try {
      await submitBallot(token, await buildEncryptedBallot());
      setToken(null); // single use: forget it immediately
      setCast(true);
      setMessage('Your vote has been recorded. Thank you.');
    } catch (e) {
      setMessage(
        e instanceof ApiError && e.code === 'TOKEN_ALREADY_USED'
          ? 'This ballot has already been cast.'
          : 'Your vote could not be submitted. Please try again.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card" aria-labelledby="vote-heading">
      <h2 id="vote-heading">Vote: {division}</h2>
      {!token && !cast && (
        <button type="button" onClick={getToken} disabled={busy}>
          {busy ? 'Checking eligibility…' : 'Get my ballot'}
        </button>
      )}
      {token && (
        <button type="button" onClick={castBallot} disabled={busy}>
          {busy ? 'Submitting…' : 'Cast my vote'}
        </button>
      )}
      <p role="status" className="result">
        {message}
      </p>
    </section>
  );
}
