import { ChangeRequestPanel } from './components/ChangeRequestPanel';

/**
 * Shared app shell. Each requirement adds its screen here as it is built.
 * Sign-in (SEC-01) supplies the access token through setAccessTokenProvider in api/http.ts.
 */
const DIVISION = 'MELBOURNE';

export function App() {
  return (
    <main>
      <h1>Electronic Voting Platform</h1>
      <ChangeRequestPanel division={DIVISION} />
    </main>
  );
}
