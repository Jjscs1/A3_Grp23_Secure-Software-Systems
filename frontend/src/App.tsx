import { useEffect, useState } from 'react';
import { apiFetch } from './api/http';
import { CandidateList, type CandidateView } from './components/CandidateList';
import { ChangeRequestPanel } from './components/ChangeRequestPanel';
import { EnrolmentCheckForm } from './components/EnrolmentCheckForm';

/**
 * Shared app shell. Each requirement adds its screen here as it is built.
 * Sign-in (SEC-01) supplies the access token through setAccessTokenProvider in api/http.ts.
 */
const DIVISION = 'MELBOURNE';

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
      <ChangeRequestPanel division={DIVISION} />
    </main>
  );
}
