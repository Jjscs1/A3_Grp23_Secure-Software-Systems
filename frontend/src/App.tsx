import { useState } from 'react'
import { useAuth } from 'react-oidc-context'

function App() {
  const auth = useAuth()
  const [actionError, setActionError] = useState('')

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

    try {
      await auth.signoutRedirect()
    } catch {
      setActionError('Unable to sign out. Please try again.')
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
          <button onClick={() => void logout()}>
            Sign out
          </button>
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