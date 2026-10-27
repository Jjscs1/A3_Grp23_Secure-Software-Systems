import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { VotingPanel } from '../src/components/VotingPanel'
import { AnonymousCastPanel } from '../src/components/AnonymousCastPanel'
import { fetchElectionKey, obtainVotingToken, submitBallot } from '../src/security/blindToken'
vi.mock('../src/security/blindToken', () => ({
  fetchElectionKey: vi.fn(), obtainVotingToken: vi.fn(), submitBallot: vi.fn(),
}))
const token = { serial: new Uint8Array([1]), signature: new Uint8Array([2]) }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(fetchElectionKey).mockResolvedValue({} as CryptoKey)
  vi.mocked(obtainVotingToken).mockResolvedValue(token)
})
it('requires verification before displaying preparation', async () => {
  const verify = vi.fn().mockResolvedValue(undefined)
  render(<VotingPanel division="MELBOURNE" castReady={false} reauthenticate={verify}
    buildEncryptedBallot={vi.fn()} onReady={vi.fn()} />)
  expect(screen.queryByText('Prepare my ballot')).not.toBeInTheDocument()
  fireEvent.click(screen.getByText('Verify identity to vote'))
  await waitFor(() => expect(verify).toHaveBeenCalledOnce())
  expect(obtainVotingToken).not.toHaveBeenCalled()
})
it('does not issue a voting token if encryption is unavailable', async () => {
  render(<VotingPanel division="MELBOURNE" castReady reauthenticate={vi.fn()}
    buildEncryptedBallot={async () => { throw new Error('not integrated') }} onReady={vi.fn()} />)
  fireEvent.click(screen.getByText('Prepare my ballot'))
  await screen.findByText(/Check that ballot encryption is connected/)
  expect(obtainVotingToken).not.toHaveBeenCalled()
})
it('hands off only the anonymous token and encrypted ballot after preparation', async () => {
  const ready = vi.fn()
  render(<VotingPanel division="MELBOURNE" castReady reauthenticate={vi.fn()}
    buildEncryptedBallot={async () => 'ciphertext'} onReady={ready} />)
  fireEvent.click(screen.getByText('Prepare my ballot'))
  await waitFor(() => expect(ready).toHaveBeenCalledWith({ token, encryptedBallot: 'ciphertext' }))
  expect(submitBallot).not.toHaveBeenCalled()
})
it('submits only on explicit cast, then discards the anonymous ballot', async () => {
  vi.mocked(submitBallot).mockResolvedValue(undefined)
  const finish = vi.fn()
  render(<AnonymousCastPanel ballot={{ token, encryptedBallot: 'ciphertext' }} finish={finish} />)
  expect(submitBallot).not.toHaveBeenCalled()
  fireEvent.click(screen.getByText('Cast my vote'))
  await waitFor(() => expect(finish).toHaveBeenCalledWith(expect.stringContaining('recorded')))
  expect(submitBallot).toHaveBeenCalledWith(token, 'ciphertext')
})
it('retries the same anonymous token after an uncertain network result', async () => {
  vi.mocked(submitBallot).mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(undefined)
  render(<AnonymousCastPanel ballot={{ token, encryptedBallot: 'ciphertext' }} finish={vi.fn()} />)
  fireEvent.click(screen.getByText('Cast my vote'))
  await screen.findByText(/Retry this same ballot/)
  fireEvent.click(screen.getByText('Cast my vote'))
  await waitFor(() => expect(submitBallot).toHaveBeenCalledTimes(2))
  expect(vi.mocked(submitBallot).mock.calls[0]).toEqual(vi.mocked(submitBallot).mock.calls[1])
})
