import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, setAccessTokenProvider, setDPoPProofProvider } from '../src/api/http'

const response = () => ({ ok: true, text: async () => '{}' })
afterEach(() => {
  setAccessTokenProvider(() => undefined)
  setDPoPProofProvider(undefined)
  vi.unstubAllGlobals()
})

describe('DPoP API requests', () => {
  it('binds proof to the actual origin, path and method, excluding query', async () => {
    const send = vi.fn().mockResolvedValue(response())
    const proof = vi.fn().mockResolvedValue('signed-proof')
    vi.stubGlobal('fetch', send)
    setAccessTokenProvider(() => 'bound-token')
    setDPoPProofProvider(proof)
    await apiFetch('/api/candidates?division=MELBOURNE')
    expect(proof).toHaveBeenCalledWith(`${window.location.origin}/api/candidates`, 'GET')
    const options = send.mock.calls[0][1]
    expect(options.headers.get('Authorization')).toBe('DPoP bound-token')
    expect(options.headers.get('DPoP')).toBe('signed-proof')
    expect(options.credentials).toBe('omit')
  })
  it('uses POST when signing a POST request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response()))
    const proof = vi.fn().mockResolvedValue('proof')
    setAccessTokenProvider(() => 'token')
    setDPoPProofProvider(proof)
    await apiFetch('/api/tokens', { method: 'POST', json: {} })
    expect(proof).toHaveBeenCalledWith(`${window.location.origin}/api/tokens`, 'POST')
  })
  it('does not send an access token when no proof is available', async () => {
    const send = vi.fn()
    vi.stubGlobal('fetch', send)
    setAccessTokenProvider(() => 'token')
    await expect(apiFetch('/api/me')).rejects.toThrow('DPoP proof is required')
    expect(send).not.toHaveBeenCalled()
  })
  it('does not fall back to Bearer if proof generation fails', async () => {
    const send = vi.fn()
    vi.stubGlobal('fetch', send)
    setAccessTokenProvider(() => 'token')
    setDPoPProofProvider(async () => { throw new Error('Key unavailable') })
    await expect(apiFetch('/api/me')).rejects.toThrow('Key unavailable')
    expect(send).not.toHaveBeenCalled()
  })
  it('keeps ballots anonymous even when signed in', async () => {
    const send = vi.fn().mockResolvedValue(response())
    const proof = vi.fn()
    vi.stubGlobal('fetch', send)
    setAccessTokenProvider(() => 'token')
    setDPoPProofProvider(proof)
    await apiFetch('/api/ballots', {
      auth: false, method: 'POST', credentials: 'include',
      headers: { Authorization: 'Bearer accidental', DPoP: 'accidental' },
      json: { encryptedBallot: 'ciphertext' },
    })
    const options = send.mock.calls[0][1]
    expect(options.headers.has('Authorization')).toBe(false)
    expect(options.headers.has('DPoP')).toBe(false)
    expect(options.credentials).toBe('omit')
    expect(proof).not.toHaveBeenCalled()
  })
  it('refuses to send credentials to another origin', async () => {
    const send = vi.fn()
    vi.stubGlobal('fetch', send)
    await expect(apiFetch('https://other.example/api/me')).rejects.toThrow('same-origin')
    expect(send).not.toHaveBeenCalled()
  })
})
