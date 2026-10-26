import { IndexedDbDPoPStore, InMemoryWebStorage, WebStorageStateStore } from 'oidc-client-ts'
import { VerifiedUserManager } from './VerifiedUserManager'
import { OIDC_CLIENT_ID, OIDC_ISSUER } from './idTokenValidation'

export const userManager = new VerifiedUserManager({
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
  dpop: {
    bind_authorization_code: true,
    store: new IndexedDbDPoPStore(),
  },

  userStore: new WebStorageStateStore({
    store: new InMemoryWebStorage(),
  }),

  stateStore: new WebStorageStateStore({
    store: window.sessionStorage,
  }),
})
