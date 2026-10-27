import { useState } from 'react'
import { ApiError } from '../api/http'
import { fetchElectionKey, obtainVotingToken, type VotingToken } from '../security/blindToken'

export const ELIGIBILITY_MESSAGES: Record<string, string> = {
  NOT_ENROLLED: 'You are not on the electoral roll, so a ballot cannot be issued.',
  NOT_A_CITIZEN: 'Only Australian citizens can vote in federal elections.',
  UNDER_18: 'You must be 18 or over on polling day to vote.',
  WRONG_DIVISION: 'You are enrolled in a different division. Choose your enrolled division.',
  ALREADY_VOTED: 'Our records show a ballot has already been issued to you.',
};

export interface AnonymousBallot {
  token: VotingToken
  encryptedBallot: string
}
interface Props {
  division: string
  buildEncryptedBallot: () => Promise<string>
  castReady: boolean
  reauthenticate: () => Promise<void>
  onReady: (ballot: AnonymousBallot) => void
}

export function VotingPanel({ division, buildEncryptedBallot, castReady, reauthenticate, onReady }: Props) {
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  async function verify() {
    setBusy(true); setMessage('')
    try { await reauthenticate() }
    catch { setMessage('Verification could not start. Please sign in again.'); setBusy(false) }
  }
  async function prepare() {
    setBusy(true); setMessage('')
    try {
      // Encryption must succeed BEFORE issuing the single-use voting token.
      const encryptedBallot = await buildEncryptedBallot()
      if (!encryptedBallot) throw new Error('Encryption unavailable')
      const key = await fetchElectionKey()
      const token = await obtainVotingToken(division, key)
      // The backend has now committed session closure with token issuance.
      onReady({ token, encryptedBallot })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'NOT_ELIGIBLE' && error.detail) {
        setMessage(ELIGIBILITY_MESSAGES[error.detail] ?? 'A ballot cannot be issued.')
      } else if (error instanceof ApiError && ['REAUTH_REQUIRED', 'SESSION_ENDED'].includes(error.code)) {
        setMessage('Verification expired. Please sign in and verify your identity again.')
      } else {
        setMessage('Your ballot could not be prepared. Check that ballot encryption is connected. If a token was already issued, ask a polling official before retrying.')
      }
    } finally { setBusy(false) }
  }
  return <section className="card" aria-labelledby="vote-heading">
    <h2 id="vote-heading">Vote: {division}</h2>
    {castReady
      ? <><p>Identity verified. Prepare your encrypted ballot within 60 seconds of verification.</p>
          <button type="button" onClick={() => void prepare()} disabled={busy}>
            {busy ? 'Preparing...' : 'Prepare my ballot'}
          </button>
          <button type="button" onClick={() => void verify()} disabled={busy}>Verify identity again</button></>
      : <><p>Verify your identity again before preparing a ballot.</p>
          <button type="button" onClick={() => void verify()} disabled={busy}>
            {busy ? 'Opening verification...' : 'Verify identity to vote'}
          </button></>}
    <p role="status" className="result">{message}</p>
  </section>
}
