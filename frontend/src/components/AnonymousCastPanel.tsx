import { useEffect, useState } from 'react'
import { ApiError } from '../api/http'
import { submitBallot } from '../security/blindToken'
import { watchIdle } from '../session'
import type { AnonymousBallot } from './VotingPanel'

/** Identity session is already closed. Only the anonymous one-use capability remains. */
export function AnonymousCastPanel({ ballot, finish }: {
  ballot: AnonymousBallot; finish: (message: string) => void
}) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  useEffect(() => {
    if (busy) return
    return watchIdle(() => finish('The unsubmitted ballot was cleared after inactivity. Please ask a polling official.'),
      async () => undefined)
  }, [busy, finish])
  async function cast() {
    if (busy) return
    setBusy(true)
    try {
      await submitBallot(ballot.token, ballot.encryptedBallot)
      finish('Your vote has been recorded. Your application session is closed.')
    } catch (error) {
      if (error instanceof ApiError && error.code === 'TOKEN_ALREADY_USED') {
        finish('This ballot token has already been used. Your application session is closed.')
      } else {
        setMessage('Submission could not be confirmed. Retry this same ballot; do not request a new token.')
      }
    } finally { setBusy(false) }
  }
  return <section className="card">
    <h2>Submit your encrypted ballot</h2>
    <p>Your sign-in session is closed. This submission does not include your identity.</p>
    <button onClick={() => void cast()} disabled={busy}>{busy ? 'Submitting...' : 'Cast my vote'}</button>
    <p role="status">{message}</p>
  </section>
}
