import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { apiFetch, setAccessTokenProvider } from './api/http'
import {
  CandidateList,
  type CandidateView,
} from './components/CandidateList'
import { ChangeRequestPanel } from './components/ChangeRequestPanel'
import { EnrolmentCheckForm } from './components/EnrolmentCheckForm'
import { VotingPanel } from './components/VotingPanel'

const DIVISION = 'MELBOURNE'

interface UserInfo {
  subject: string
  username: string
  roles: string[]
}

// Temporary placeholder from the shared app.
// SEC-12's owner must replace this with actual ballot encryption.
async function pendingSec12Envelope(): Promise<string> {
  return JSON.stringify({
    alg: 'pending-SEC-12',
    note: 'replace with the client-side encryption envelope',
  })
}

function SignedInPanels() {
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

    setReadyToken(accessToken)

    return () => {
      setAccessTokenProvider(() => undefined)
    }
  }, [accessToken, user])

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

      <section className="card" aria-label="Sign-in">
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
      </section>

      <EnrolmentCheckForm />

      {accessToken && readyToken === accessToken && (
        <SignedInPanels key={accessToken} />
      )}
    </main>
  )
}