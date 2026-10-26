/**
 * SEC-07: candidate and party names are rendered as text. JSX escapes them, so a name
 * like <script>alert(1)</script> shows up literally and never runs. ESLint fails the
 * build on dangerouslySetInnerHTML (eslint.config.js), so this cannot regress quietly.
 */
export interface CandidateView {
  id: number;
  name: string;
  party?: string | null;
}

export function CandidateList({ candidates }: { candidates: CandidateView[] }) {
  if (candidates.length === 0) {
    return <p className="muted">No candidates have been approved for this division yet.</p>;
  }
  return (
    <ul className="candidate-list" aria-label="Candidates">
      {candidates.map((c) => (
        <li key={c.id}>
          <span className="candidate-name">{c.name}</span>
          {c.party ? <span className="candidate-party">{c.party}</span> : null}
        </li>
      ))}
    </ul>
  );
}
