import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError, apiFetch } from '../api/http';

/** SEC-05: Commissioner's delegate console for the four-eyes workflow. */
export interface ChangeRequestView {
  id: number;
  type: 'CANDIDATE_DETAILS' | 'GROUP_ORDERING' | 'RECOUNT_EXCLUSION' | 'BULK_ENROLMENT_CHANGE';
  payload: string;
  proposer: string;
  courtOrderReference?: string | null;
  status: 'PENDING' | 'APPLIED' | 'REJECTED';
  approvers: string[];
  requiredApprovals: number;
}

export const CHANGE_ERRORS: Record<string, string> = {
  SELF_APPROVAL_FORBIDDEN: 'You cannot approve a change you proposed. Another delegate must approve it.',
  CONFLICT: 'You have already approved this change, or it was decided by someone else just now.',
  INVALID_CHANGE_REQUEST: 'That change is not valid. Check the details (and court order reference for exclusions).',
  FORBIDDEN: 'Only Commissioner’s delegates can do this.',
};

function errorText(e: unknown): string {
  return e instanceof ApiError ? (CHANGE_ERRORS[e.code] ?? 'The request failed.') : 'The request failed.';
}

export function ChangeRequestPanel({ division }: { division: string }) {
  const [pending, setPending] = useState<ChangeRequestView[]>([]);
  const [name, setName] = useState('');
  const [party, setParty] = useState('');
  const [candidateId, setCandidateId] = useState('');
  const [courtOrder, setCourtOrder] = useState('');
  const [message, setMessage] = useState('');

  const refresh = useCallback(async () => {
    try {
      setPending(await apiFetch<ChangeRequestView[]>('/api/change-requests'));
    } catch (e) {
      setMessage(errorText(e));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function propose(body: object, done: string) {
    try {
      await apiFetch('/api/change-requests', { method: 'POST', json: body });
      setMessage(done);
      await refresh();
    } catch (e) {
      setMessage(errorText(e));
    }
  }

  function proposeCandidate(event: FormEvent) {
    event.preventDefault();
    void propose(
      { type: 'CANDIDATE_DETAILS', payload: JSON.stringify({ division, name, party }) },
      'Candidate proposed. Another delegate must approve it before it appears on the ballot.',
    );
  }

  function proposeExclusion(event: FormEvent) {
    event.preventDefault();
    void propose(
      {
        type: 'RECOUNT_EXCLUSION',
        payload: JSON.stringify({ division, candidateId: Number(candidateId) }),
        courtOrderReference: courtOrder,
      },
      'Exclusion proposed. Two other delegates must approve it.',
    );
  }

  async function approve(id: number) {
    try {
      const updated = await apiFetch<ChangeRequestView>(`/api/change-requests/${id}/approvals`, { method: 'POST' });
      setMessage(updated.status === 'APPLIED' ? 'Approved and applied.' : 'Approval recorded. Waiting for another delegate.');
      await refresh();
    } catch (e) {
      setMessage(errorText(e));
    }
  }

  return (
    <section className="card" aria-labelledby="cr-heading">
      <h2 id="cr-heading">Delegate console: {division}</h2>

      <form onSubmit={proposeCandidate}>
        <h3>Propose a candidate</h3>
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} required />
        </label>
        <label>
          Party
          <input value={party} onChange={(e) => setParty(e.target.value)} maxLength={120} />
        </label>
        <button type="submit">Propose</button>
      </form>

      <form onSubmit={proposeExclusion}>
        <h3>Propose a recount exclusion</h3>
        <label>
          Candidate ID
          <input value={candidateId} onChange={(e) => setCandidateId(e.target.value)} inputMode="numeric" required />
        </label>
        <label>
          Court order reference
          <input value={courtOrder} onChange={(e) => setCourtOrder(e.target.value)} maxLength={64} required />
        </label>
        <button type="submit">Propose exclusion</button>
      </form>

      <p role="status" className="result">
        {message}
      </p>

      <h3>Waiting for approval</h3>
      {pending.length === 0 ? (
        <p className="muted">Nothing pending.</p>
      ) : (
        <ul className="change-list" aria-label="Pending changes">
          {pending.map((cr) => (
            <li key={cr.id}>
              <div>
                <strong>{cr.type}</strong> proposed by <span>{cr.proposer}</span>
              </div>
              <code>{cr.payload}</code>
              {cr.courtOrderReference ? <div>Court order: {cr.courtOrderReference}</div> : null}
              <div>
                Approvals {cr.approvers.length} of {cr.requiredApprovals}
              </div>
              <button type="button" onClick={() => void approve(cr.id)}>
                Approve
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
