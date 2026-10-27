import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { render, screen } from '@testing-library/react';
import { ResultsPanel } from '../components/ResultsPanel';

// Signed by the Java backend (ResultsSigning), so this also proves Java and TypeScript agree.
// Path is relative to frontend/, where npm test runs.
const fixture = JSON.parse(readFileSync('src/__tests__/fixtures/signed-results.json', 'utf8'));

/** SEC-18: the public results screen only shows results that pass verification. */
function serve(latest: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      url.endsWith('/public-key')
        ? new Response(JSON.stringify({ algorithm: 'Ed25519', publicKeyPem: fixture.publicKeyPem }), { status: 200 })
        : new Response(JSON.stringify(latest), { status: 200 }),
    ),
  );
}

beforeEach(() => {
  vi.stubGlobal('crypto', webcrypto);
  localStorage.clear();
});
afterEach(() => vi.unstubAllGlobals());

describe('SEC-18 ResultsPanel', () => {
  it('shows verified results with their version', async () => {
    serve(fixture.v2);
    render(<ResultsPanel electionId={1} />);
    expect(await screen.findByText(/Signature verified\. Version 2/)).toBeInTheDocument();
    expect(screen.getByText(/SINGH, Maya/)).toBeInTheDocument();
  });

  it('refuses an older version after a newer one was seen (replay)', async () => {
    serve(fixture.v2);
    const first = render(<ResultsPanel electionId={1} />);
    await screen.findByText(/Version 2/);
    first.unmount();

    serve(fixture.v1); // an attacker now serves the superseded result
    render(<ResultsPanel electionId={1} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('REPLAY');
    expect(screen.queryByText(/NGUYEN, Ava/)).toBeNull();
  });

  it('refuses tampered results', async () => {
    const forged = JSON.parse(JSON.stringify(fixture.v2));
    forged.payload.results[0].elected[0].name = 'SOMEONE ELSE';
    serve(forged);
    render(<ResultsPanel electionId={1} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('SIGNATURE INVALID');
    expect(screen.queryByText(/SOMEONE ELSE/)).toBeNull();
  });
});