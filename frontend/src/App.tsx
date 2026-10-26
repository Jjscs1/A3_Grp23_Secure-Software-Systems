import { useEffect, useState } from 'react';
import { apiFetch } from './api/http';
import { CandidateList, type CandidateView } from './components/CandidateList';
import { ChangeRequestPanel } from './components/ChangeRequestPanel';
import { EnrolmentCheckForm } from './components/EnrolmentCheckForm';
import { VotingPanel } from './components/VotingPanel';

/**
 * Demo shell for SEC-05, 07, 08, 10 and 11. In the team app, these components slot into
 * the shared layout and routing. Sign-in (SEC-01) supplies the access token through
 * setAccessTokenProvider in api/http.ts.
 */
const DIVISION = 'MELBOURNE';

/**
 * Placeholder until SEC-12 lands. It deliberately sends no preferences at all, so nothing
 * readable leaves the browser in the meantime.
 */
async function pendingSec12Envelope(): Promise<string> {
  return JSON.stringify({ alg: 'pending-SEC-12', note: 'replace with the client-side encryption envelope' });
}

export function App() {
  const [candidates, setCandidates] = useState<CandidateView[]>([]);

  useEffect(() => {
    apiFetch<CandidateView[]>(`/api/candidates?division=${encodeURIComponent(DIVISION)}`)
      .then(setCandidates)
      .catch(() => setCandidates([]));
  }, []);

  return (
    <main>
      <h1>Electronic Voting Platform</h1>
      <EnrolmentCheckForm />
      <section className="card" aria-labelledby="cand-heading">
        <h2 id="cand-heading">Candidates: {DIVISION}</h2>
        <CandidateList candidates={candidates} />
      </section>
      <VotingPanel division={DIVISION} buildEncryptedBallot={pendingSec12Envelope} />
      <ChangeRequestPanel division={DIVISION} />
    </main>
  );
}
