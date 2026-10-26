import { fireEvent, render, screen } from '@testing-library/react';
import { ChangeRequestPanel } from '../components/ChangeRequestPanel';

const pending = [
  {
    id: 5,
    type: 'CANDIDATE_DETAILS',
    payload: '{"division":"MELBOURNE","name":"<script>alert(1)</script>"}',
    proposer: 'alice',
    courtOrderReference: null,
    status: 'PENDING',
    approvers: [],
    requiredApprovals: 1,
  },
];

afterEach(() => vi.unstubAllGlobals());

describe('SEC-05 ChangeRequestPanel', () => {
  it('lists pending changes with payloads shown as text', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(pending), { status: 200 })));
    const { container } = render(<ChangeRequestPanel division="MELBOURNE" />);

    expect(await screen.findByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByText('Approvals 0 of 1')).toBeInTheDocument();
  });

  it('explains when a proposer tries to approve their own change', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === 'POST'
          ? new Response(JSON.stringify({ error: 'SELF_APPROVAL_FORBIDDEN' }), { status: 403 })
          : new Response(JSON.stringify(pending), { status: 200 }),
      ),
    );
    render(<ChangeRequestPanel division="MELBOURNE" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));

    expect(await screen.findByText(/cannot approve a change you proposed/)).toBeInTheDocument();
  });
});
