import { useState } from 'react';
import { checkTrackingCode, type VerifyResult } from '../security/ballotBox';

/** "Check my ballot": the voter pastes the tracking code; the browser verifies the proof itself. */
export function TrackingCodeChecker({ pinnedFingerprint }: { pinnedFingerprint?: string }) {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<VerifyResult | null>(null);

  async function onCheck() {
    setBusy(true);
    setResult(null);
    setResult(await checkTrackingCode(code, pinnedFingerprint));
    setBusy(false);
  }

  return (
    <section aria-labelledby="tc-h">
      <h2 id="tc-h">Check my ballot</h2>
      <label htmlFor="tc-in">Tracking code</label>
      <input
        id="tc-in"
        value={code}
        maxLength={300}
        autoComplete="off"
        spellCheck={false}
        onChange={(e) => setCode(e.target.value)}
      />
      <button type="button" disabled={busy || code.trim() === ''} onClick={onCheck}>
        {busy ? 'Checking…' : 'Check'}
      </button>
      <div role="status" aria-live="polite">
        {result?.ok === true && (
          <p>
            Verified in your browser: your ballot is number {result.index + 1} of {result.size} in the signed ballot box
            (sealed {result.ts}). Root {result.root.slice(0, 16)}…
          </p>
        )}
        {result?.ok === false && <p>{result.reason}</p>}
      </div>
    </section>
  );
}
