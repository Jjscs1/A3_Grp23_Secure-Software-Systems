# SEC-04: DPoP implementation stage

This change adds device-key-bound API authentication to the existing app.
It is one part of SEC-04, not completion of the whole requirement.

## What changed

- The OIDC client creates a non-extractable P-256 private key and keeps it in
  IndexedDB through `IndexedDbDPoPStore`. Login uses DPoP and S256 PKCE.
- Each authenticated API request sends a new DPoP proof and uses the DPoP
  Authorization scheme. There is no fallback to Bearer.
- Spring Security 6.5 validates the access token and the DPoP proof, including
  the signature, key binding, access-token hash, method, URL, time and replay ID.
- The backend rejects Bearer authentication, including a bound token presented
  as Bearer without its proof.
- The development proxy preserves Host. The frontend signs the browser-facing
  URL without its query or fragment, matching the request seen by Spring.
- Anonymous API calls omit Authorization, DPoP and cookies. In particular,
  `/api/ballots` stays anonymous and uses the existing blind-signed voting token.

This uses the project's existing libraries; package versions are unchanged.

## Local setup

1. Apply the patch to the `khaled/identity-access` source used for this review.
2. In Keycloak, select `evp` > Clients > `evp-frontend` > Settings.
3. In Capability config, enable **Require DPoP bound tokens**, then Save.
4. Keep Access Token Lifespan at 10 minutes in `evp`. The client inherits it.
5. Restart the backend and frontend after checking the commands below.
6. Sign out and start a fresh login. Previously issued Bearer tokens no longer work.

Do not change the `evp-staff-broker` client for this step.
The application token still comes from `evp` after a staff login.

Run from the repository root in PowerShell:

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

Use the existing environment variables when restarting the backend.
Run the frontend with `npm.cmd run dev -- --port 5173 --strictPort`.

## Validation

The prepared change passed 83 backend tests and 34 frontend tests, plus the
frontend build and lint. Backend tests ran with Java 21, H2, generated signing
keys and a local test JWKS server; they did not contact the user's Keycloak.
The review environment supplied Mockito as a Java agent because self-attachment
was unavailable there. This did not change the application's Maven configuration.

The 15 JWT/DPoP tests include valid authentication, invalid access-token signature,
issuer, audience and expiry, missing proof, Bearer replay, wrong device key,
wrong method, wrong URL, wrong access-token hash, stale proof, repeated proof,
an unbound token and a browser-facing proxy URL with a query.

Frontend tests check signed proofs with real WebCrypto, a non-extractable private
key, different proof IDs, request binding, failure without a proof, refusal to send
credentials to another origin, and anonymous ballot requests.

A real Keycloak/browser login with this patch still needs to be checked locally.
In DevTools, check `token_type: DPoP`, `expires_in: 600`, and a successful `/api/me`.
Do not commit full token responses or Authorization headers as evidence.

The existing blind-RSA dependency still produces a browser `crypto` warning
at build time. A successful build is not a complete voting-flow test.

## Remaining SEC-04 work

Tokens are still held in memory by the frontend at this stage. This does not
implement the required HttpOnly, Secure, SameSite=Strict, __Host- session cookie.

Cookie-based sessions need server-side session management and CSRF protection.
They must also preserve the anonymous ballot endpoint: never send identity
cookies or an identity-bound proof with the ballot body.

Re-authentication before casting, server-enforced session termination after
casting, and the 90-second polling-terminal idle limit remain to be implemented.
Do not mark these criteria complete based on this DPoP patch.

The default proof replay cache is local to a backend process. A multi-instance
production deployment needs shared replay protection and a reviewed HTTPS/proxy
configuration. DPoP does not protect against code executing inside the same
browser origin using the key, and this software key is not a hardware attestation.

Export the final Keycloak configuration after the remaining settings are finished.

## References

- https://authts.github.io/oidc-client-ts/classes/UserManager.html
- https://github.com/authts/oidc-client-ts/blob/main/docs/protocols/demonstrating-proof-of-possession.md
- https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/dpop-tokens.html
- https://www.keycloak.org/securing-apps/dpop
