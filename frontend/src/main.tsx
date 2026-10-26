import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { InMemoryWebStorage, WebStorageStateStore } from 'oidc-client-ts'
import './index.css'
import App from './App.tsx'

const oidcConfig = {
  authority: 'http://localhost:8081/realms/evp',
  client_id: 'evp-frontend',
  redirect_uri: 'http://localhost:5173/auth/callback',
  post_logout_redirect_uri: 'http://localhost:5173/',
  response_type: 'code',
  scope: 'openid profile email',
  disablePKCE: false,
  automaticSilentRenew: false,
  loadUserInfo: false,

  userStore: new WebStorageStateStore({
    store: new InMemoryWebStorage(),
  }),

  stateStore: new WebStorageStateStore({
    store: window.sessionStorage,
  }),

  onSigninCallback: () => {
    window.history.replaceState({}, document.title, '/')
  },
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...oidcConfig}>
      <App />
    </AuthProvider>
  </StrictMode>,
)