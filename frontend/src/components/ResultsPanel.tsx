import { useEffect, useState } from 'react';
import { apiFetch } from '../api/http';
import {
  RejectedPublication,
  verifyPublication,
  type AcceptedState,
  type Publication,
  type ResultsPayload,
} from '../security/resultsVerifier';

/**
 * SEC-18: public results page. Fetches the latest signed publication and checks it IN THE
 * BROWSER against the commission's published key before showing anything as verified.
 * The newest accepted sequence is remembered per election, so an older (replayed)
 * publication served later is shown as rejected.
 *
 * Public: no login needed (GET /api/public/** is open, see SecurityConfig).
 */
type View =
  | { kind: 'loading' }
  | { kind: 'verified'; payload: ResultsPayload }
  | { kind: 'rejected'; reason: string }
  | { kind: 'error' };

const stateKey = (electionId: number) => `evp.results.accepted.${electionId}`;

function loadState(electionId: number): AcceptedState | null {
  try {
    const raw = localStorage.getItem(stateKey(electionId));
    return raw ? (JSON.parse(raw) as AcceptedState) : null;
  } catch {
    return null; // private window or blocked storage: verify without memory
  }
}

function saveState(electionId: number, state: AcceptedState): void {
  try {
    localStorage.setItem(stateKey(electionId), JSON.stringify(state));
  } catch {
    // not fatal: replay protection then lasts only for this page view
  }
}

export function ResultsPanel({ electionId }: { electionId: number }) {
  const [view, setView] = useState<View>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const key = await apiFetch<{ publicKeyPem: string }>('/api/public/results/public-key', { auth: false });
        const publication = await apiFetch<Publication>(`/api/public/results/${electionId}/latest`, { auth: false });
        const accepted = await verifyPublication(publication, key.publicKeyPem, loadState(electionId));
        saveState(electionId, accepted);
        if (!cancelled) setView({ kind: 'verified', payload: publication.payload });
      } catch (e) {
        if (cancelled) return;
        setView(e instanceof RejectedPublication ? { kind: 'rejected', reason: e.message } : { kind: 'error' });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [electionId]);

  return (
    <section className="card" aria-labelledby="results-heading">
      <h2 id="results-heading">Official results</h2>
      {view.kind === 'loading' && <p>Checking the signed results…</p>}
      {view.kind === 'error' && <p role="alert">No results have been published yet, or they could not be loaded.</p>}
      {view.kind === 'rejected' && (
        <p role="alert">
          Not shown: these results failed verification ({view.reason}).
        </p>
      )}
      {view.kind === 'verified' && (
        <>
          <p>
            Signature verified. Version {view.payload.sequence}, issued {view.payload.issuedAt}.
          </p>
          <ul>
            {view.payload.results.map((r) => (
              <li key={r.tallyId}>
                {r.contest === 'house' ? 'House' : 'Senate'}, {r.region}: {r.elected.map((c) => c.name).join(', ')}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}