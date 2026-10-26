import { useState, type FormEvent } from 'react';
import { ApiError, apiFetch } from '../api/http';

/**
 * SEC-08: public enrolment check. Details go in a POST body, never the URL. The server
 * gives the same shaped answer whether or not the person is enrolled, and this screen
 * shows nothing more than that yes or no.
 */
type Result = { kind: 'idle' } | { kind: 'enrolled' } | { kind: 'not-found' } | { kind: 'error'; message: string };

export function EnrolmentCheckForm() {
  const [fullName, setFullName] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [postcode, setPostcode] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<Result>({ kind: 'idle' });

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      const response = await apiFetch<{ enrolled: boolean }>('/api/public/enrolment-check', {
        method: 'POST',
        auth: false,
        json: { fullName, dateOfBirth, postcode },
      });
      setResult(response.enrolled ? { kind: 'enrolled' } : { kind: 'not-found' });
    } catch (e) {
      const message =
        e instanceof ApiError && e.status === 400
          ? 'Please check the details. Use your full name as enrolled, your date of birth and a 4 digit postcode.'
          : e instanceof ApiError && e.status === 429
            ? 'Too many checks from this device. Please wait a minute and try again.'
            : 'The enrolment check is unavailable right now.';
      setResult({ kind: 'error', message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={onSubmit} aria-labelledby="enrolment-heading">
      <h2 id="enrolment-heading">Check your enrolment</h2>
      <label>
        Full name
        <input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={120} required autoComplete="name" />
      </label>
      <label>
        Date of birth
        <input type="date" value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} required />
      </label>
      <label>
        Postcode
        <input
          value={postcode}
          onChange={(e) => setPostcode(e.target.value)}
          inputMode="numeric"
          pattern="\d{4}"
          maxLength={4}
          required
          autoComplete="postal-code"
        />
      </label>
      <button type="submit" disabled={busy}>
        {busy ? 'Checking…' : 'Check'}
      </button>
      <p role="status" className="result">
        {result.kind === 'enrolled' && 'You are enrolled to vote.'}
        {result.kind === 'not-found' &&
          'We could not find an enrolment matching those exact details. Check the spelling, or enrol with the AEC.'}
        {result.kind === 'error' && result.message}
      </p>
    </form>
  );
}
