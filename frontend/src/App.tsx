import { useState } from 'react'
import { useAuth } from 'react-oidc-context'

function App() {
  const auth = useAuth()
  const [actionError, setActionError] = useState('')
  const [apiResult, setApiResult] = useState('')
  const [checkingApi, setCheckingApi] = useState(false)

  async function login() {
    setActionError('')

    try {
      await auth.signinRedirect({
        nonce: crypto.randomUUID(),
      })
    } catch {
      setActionError('Unable to start sign-in. Please try again.')
    }
  }

  async function logout() {
    setActionError('')
    setApiResult('')

    try {
      await auth.signoutRedirect()
    } catch {
      setActionError('Unable to sign out. Please try again.')
    }
  }

  async function checkApi() {
    setActionError('')
    setApiResult('')

    if (!auth.user || auth.user.expired) {
      setActionError('Please sign out and sign in again.')
      return
    }

    setCheckingApi(true)

    try {
      const response = await fetch('http://localhost:8080/api/me', {
        headers: {
          Authorization: `Bearer ${auth.user.access_token}`,
        },
        credentials: 'omit',
        cache: 'no-store',
      })

      if (!response.ok) {
        setActionError(`API request failed: HTTP ${response.status}`)
        return
      }

      const data = await response.json()
      setApiResult(JSON.stringify(data, null, 2))
    } catch {
      setActionError('Cannot reach the API. Check that the backend is running.')
    } finally {
      setCheckingApi(false)
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
    <main style={{ maxWidth: 640, margin: '60px auto', padding: 24 }}>
      <h1>EVP — Electronic Voting Prototype</h1>

      {auth.isAuthenticated ? (
        <>
          <p>
            Signed in as:{' '}
            <strong>{auth.user?.profile.preferred_username}</strong>
          </p>

          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <button
              onClick={() => void checkApi()}
              disabled={checkingApi}
            >
              {checkingApi ? 'Checking...' : 'Check protected API'}
            </button>

            <button onClick={() => void logout()}>
              Sign out
            </button>
          </div>

          {apiResult && (
            <section>
              <h2>Backend response</h2>
              <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {apiResult}
              </pre>
            </section>
          )}
        </>
      ) : (
        <>
          <p>Sign in to continue.</p>
          <button onClick={() => void login()}>
            Sign in with Keycloak
          </button>
        </>
      )}

      {actionError && <p role="alert">{actionError}</p>}
    </main>
  )
}

export default App