import { userManager } from './oidcUserManager'
import { startSession, watchIdle } from './session'
import { useCallback, useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { ApiError, apiFetch, setAccessTokenProvider, setDPoPProofProvider } from './api/http'
import {
  CandidateList,
  type CandidateView,
} from './components/CandidateList'
import { ChangeRequestPanel } from './components/ChangeRequestPanel'
import { EnrolmentCheckForm } from './components/EnrolmentCheckForm'
import { VotingPanel, type AnonymousBallot } from './components/VotingPanel'
import { AnonymousCastPanel } from './components/AnonymousCastPanel'

const DIVISION = 'MELBOURNE'

interface UserInfo {
  subject: string
  username: string
  roles: string[]
}

// Temporary placeholder from the shared app.
// SEC-12's owner must replace this with actual ballot encryption.
async function pendingSec12Envelope(): Promise<string> {
  throw new Error('SEC-12 ballot encryption is not connected yet.')
}

function SignedInPanels({ castReady, reauthenticate, onReady }: {
  castReady: boolean; reauthenticate: () => Promise<void>; onReady: (ballot: AnonymousBallot) => void
}) {
  const [candidates, setCandidates] = useState<CandidateView[]>([])
  const [candidateError, setCandidateError] = useState('')
  const [roles, setRoles] = useState<string[]>([])
  const [loadingRoles, setLoadingRoles] = useState(true)
  const [rolesError, setRolesError] = useState('')

  useEffect(() => {
    let cancelled = false

    apiFetch<UserInfo>('/api/me', { cache: 'no-store' })
      .then((data) => {
        if (
          !Array.isArray(data.roles) ||
          !data.roles.every((role) => typeof role === 'string')
        ) {
          throw new Error('Invalid roles response.')
        }

        if (!cancelled) {
          setRoles(data.roles)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRoles([])
          setRolesError(
            'Unable to load your access permissions. Please sign out and try again.',
          )
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingRoles(false)
        }
      })

    apiFetch<CandidateView[]>(
      `/api/candidates?division=${encodeURIComponent(DIVISION)}`,
    )
      .then((data) => {
        if (!cancelled) {
          setCandidates(data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCandidateError('Unable to load candidates.')
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  const isVoter = roles.includes('VOTER')
  const isDelegate = roles.includes('DELEGATE')

  return (
    <>
      <section className="card" aria-labelledby="cand-heading">
        <h2 id="cand-heading">Candidates: {DIVISION}</h2>
        {candidateError && <p role="alert">{candidateError}</p>}
        <CandidateList candidates={candidates} />
      </section>

      {loadingRoles && <p>Loading your access permissions...</p>}

      {rolesError && <p role="alert">{rolesError}</p>}

      {!loadingRoles && !rolesError && (
        <>
          {isVoter && (
            <VotingPanel
              division={DIVISION}
              buildEncryptedBallot={pendingSec12Envelope}
              castReady={castReady}
              reauthenticate={reauthenticate}
              onReady={onReady}
            />
          )}

          {isDelegate && (
            <ChangeRequestPanel division={DIVISION} />
          )}

          {!isVoter && !isDelegate && (
            <p>No voting or delegate access is assigned to this account.</p>
          )}
        </>
      )}
    </>
  )
}

export function App() {
  const auth = useAuth()
  const [actionError, setActionError] = useState('')
  const [readyToken, setReadyToken] = useState<string | undefined>()
  const [castReady, setCastReady] = useState(false)
  const [anonymousBallot, setAnonymousBallot] = useState<AnonymousBallot | null>(null)
  const [receipt, setReceipt] = useState('')
  const finishCast = useCallback((message: string) => {
    setAnonymousBallot(null)
    setReceipt(message)
  }, [])

  const user = auth.user
  const accessToken =
    auth.isAuthenticated && user && !user.expired
      ? user.access_token
      : undefined

  useEffect(() => {
    setAccessTokenProvider(() => {
      if (!accessToken || !user || user.expired) {
        return undefined
      }

      return accessToken
    })

    setDPoPProofProvider(async (url, method) => {
      if (!user || user.expired || user.token_type.toLowerCase() !== 'dpop') {
        throw new Error('A new DPoP sign-in is required.')
      }
      return userManager.dpopProof(url, user, method)
    })

    let cancelled = false
    setReadyToken(undefined)
    if (accessToken && user?.id_token) {
      void startSession(accessToken, user.id_token).then((state) => {
        if (!cancelled) { setCastReady(state.castReady); setReadyToken(accessToken) }
      }).catch(() => {
        if (!cancelled) setActionError('Your session could not be opened. Please sign out and sign in again.')
      })
    }

    return () => {
      cancelled = true
      setAccessTokenProvider(() => undefined)
      setDPoPProofProvider(undefined)
    }
  }, [accessToken, user])

  useEffect(() => {
    if (!readyToken) return
    return watchIdle(() => {
      setReadyToken(undefined)
      setActionError('Your session ended after 90 seconds without activity. Please sign in again.')
      // Server independently enforces idle expiry, including when this tab is suspended.
      void apiFetch('/api/session/end', { method: 'POST' }).catch(() => undefined)
        .finally(() => { void auth.removeUser() })
    }, async () => { await apiFetch('/api/session/activity', { method: 'POST' }) })
  }, [readyToken, auth.removeUser])

  async function reauthenticateForVote() {
    const { nonce } = await apiFetch<{ nonce: string }>('/api/session/prepare-cast', { method: 'POST' })
    setReadyToken(undefined)
    await auth.signinRedirect({ nonce, prompt: 'login', max_age: 0 })
  }

  function receiveAnonymousBallot(ballot: AnonymousBallot) {
    setAnonymousBallot(ballot)
    setReadyToken(undefined)
    setAccessTokenProvider(() => undefined)
    setDPoPProofProvider(undefined)
    void auth.removeUser()
  }

  async function login() {
    setActionError('')

    try {
      await auth.signinRedirect()
    } catch {
      setActionError('Unable to start sign-in. Please try again.')
    }
  }

  async function logout() {
    setActionError('')
    setReadyToken(undefined)

    try {
      await apiFetch('/api/session/end', { method: 'POST' })
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) {
        setReadyToken(accessToken)
        setActionError('Unable to confirm session closure. Please try signing out again.')
        return
      }
    }
    setAccessTokenProvider(() => undefined)
    try {
      await auth.signoutRedirect()
    } catch {
      await auth.removeUser()
      setActionError(
        'Local sign-in was cleared, but Keycloak sign-out could not be confirmed.',
      )
    }
  }

  if (auth.isLoading || auth.activeNavigator) {
    return <main><p>Please wait...</p></main>
  }

  if (auth.error) {
    return (
      <main>
        <h1>Sign-in could not be completed</h1>
        <p>Please return to the home page and try again.</p>
        <a href="/">Return home</a>
      </main>
    )
  }

  return (
    <main>
      <h1>Electronic Voting Platform</h1>

      {!anonymousBallot && <section className="card" aria-label="Sign-in">
        {auth.isAuthenticated ? (
          <>
            <p>
              Signed in as:{' '}
              <strong>{user?.profile.preferred_username}</strong>
            </p>

            {!accessToken && (
              <p>Your session has expired. Please sign out and sign in again.</p>
            )}

            <button onClick={() => void logout()}>
              Sign out
            </button>
          </>
        ) : (
          <>
            <p>Sign in to access the voting application.</p>
            <button onClick={() => void login()}>
              Sign in with Keycloak
            </button>
          </>
        )}

        {actionError && <p role="alert">{actionError}</p>}
      </section>}

      {receipt && <p role="status">{receipt}</p>}
      {anonymousBallot && <AnonymousCastPanel ballot={anonymousBallot} finish={finishCast} />}
      <EnrolmentCheckForm />

      {accessToken && readyToken === accessToken && (
        <SignedInPanels key={accessToken} castReady={castReady} reauthenticate={reauthenticateForVote} onReady={receiveAnonymousBallot} />
      )}
    </main>
  )
}
