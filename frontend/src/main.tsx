import { userManager } from './oidcUserManager'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { App } from './App'
import './styles.css'

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
