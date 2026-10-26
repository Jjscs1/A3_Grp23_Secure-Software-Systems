import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { InMemoryWebStorage, WebStorageStateStore } from 'oidc-client-ts'
import { VerifiedUserManager } from './VerifiedUserManager'
import { OIDC_CLIENT_ID, OIDC_ISSUER } from './idTokenValidation'
import './index.css'
import App from './App.tsx'

const userManager = new VerifiedUserManager({
  authority: OIDC_ISSUER,
  client_id: OIDC_CLIENT_ID,
  redirect_uri: 'http://localhost:5173/auth/callback',
  post_logout_redirect_uri: 'http://localhost:5173/',
  response_type: 'code',
  scope: 'openid profile email',
  disablePKCE: false,
  automaticSilentRenew: false,
  monitorSession: false,
  loadUserInfo: false,

  userStore: new WebStorageStateStore({
    store: new InMemoryWebStorage(),
  }),

  stateStore: new WebStorageStateStore({
    store: window.sessionStorage,
  }),
})

function onSigninCallback() {
  window.history.replaceState({}, document.title, '/')
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider
      userManager={userManager}
      onSigninCallback={onSigninCallback}
    >
      <App />
    </AuthProvider>
  </StrictMode>,
)