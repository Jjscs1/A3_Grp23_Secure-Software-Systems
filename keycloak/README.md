# Keycloak Local Development Setup

This directory contains the `evp` realm configuration for the EVP prototype.

## Requirements

- Docker Desktop running with Linux containers.
- Java 21 installed.
- Node.js and npm installed.
- This repository cloned locally.

Run the commands below in PowerShell. Start from the repository root unless otherwise stated.

## 1. Start Keycloak on a new local setup

Choose a local administrator password when prompted.
Do not commit passwords to Git.

```powershell
$adminCredential = Get-Credential -UserName admin -Message "Choose a local Keycloak admin password"
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
```

Start Keycloak from the repository root:

```powershell
docker run -d --name evp-keycloak -p 127.0.0.1:8081:8080 -v evp-keycloak-data:/opt/keycloak/data --mount "type=bind,source=$($PWD.Path)\keycloak,target=/opt/keycloak/data/import,readonly" -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD quay.io/keycloak/keycloak:26.8.1 start-dev --import-realm
```

Clear the password variable from the current PowerShell session:

```powershell
Remove-Item Env:KC_BOOTSTRAP_ADMIN_PASSWORD
Remove-Variable adminCredential
```

The bootstrap password remains part of the container configuration.
Use a password dedicated to this local development environment.

Check startup:

```powershell
docker logs --tail 60 evp-keycloak
```

On a fresh database, the log should include:

```text
Realm 'evp' imported
Import finished successfully
```

The named volume `evp-keycloak-data` preserves local Keycloak data.

Keycloak skips startup import if the realm already exists.
Changing the JSON file does not automatically update an existing realm.

If this container already exists, start it instead of repeating `docker run`:

```powershell
docker start evp-keycloak
```

## 2. Create a test user

Open http://localhost:8081/admin/ and sign in with the administrator credentials chosen above.

Select the `evp` realm, then open Users and create:

- Username: `voter-test`
- Email: `voter-test@example.com`
- First name: `Test`
- Last name: `Voter`
- Email verified: Off
- Required user actions: None

Under Credentials, set a local test password.
Set Temporary to Off for this demo account.

Users and their passwords are not included in this realm export.
The username does not grant the VOTER role.

## 3. Run the frontend

Open PowerShell at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

Keep this window open.

Open http://localhost:5173 and select "Sign in with Keycloak".

Sign in using the test user and the password you configured.

Expected result:

```text
Signed in as: voter-test
```

Select "Sign out", then start sign-in again.
Keycloak should ask for credentials again.

The frontend keeps user tokens in memory.
Reloading the page clears its local authentication state, but does not itself end the Keycloak session.
Use Sign out to end the session.

## 4. Run the backend

Open a separate PowerShell window at the repository root:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Keep this window open.

The backend listens on http://localhost:8080.

With Keycloak and the frontend running, sign in and select "Check protected API".

The response should contain the authenticated user's subject and username:

```json
{
  "subject": "<user-id>",
  "username": "voter-test"
}
```

The frontend sends the access token in the Authorization header.
The backend is configured to validate the RS256 signature, issuer, token timestamps, and the `evp-backend` audience.

## Current OIDC configuration

- Realm: `evp`
- Issuer: `http://localhost:8081/realms/evp`
- Client ID: `evp-frontend`
- Public client with Standard flow enabled
- PKCE method: `S256`
- Implicit flow and Direct access grants disabled
- Redirect URI: `http://localhost:5173/auth/callback`
- Post-logout redirect URI: `http://localhost:5173/`
- Web origin: `http://localhost:5173`
- Backend audience: `evp-backend`

The realm export includes the `evp-backend-audience` mapper.
It adds `evp-backend` to access tokens, not ID tokens.

### Updating an existing realm

Startup import skips existing realms.

If your realm was created before the audience mapper was added:

1. Open Clients and select `evp-frontend`.
2. Open Client scopes.
3. Select `evp-frontend-dedicated`.
4. Under Mappers, choose Configure a new mapper, or Add mapper followed by By configuration.
5. Select Audience.
6. Configure:

   - Name: `evp-backend-audience`
   - Included Client Audience: Leave unselected
   - Included Custom Audience: `evp-backend`
   - Add to ID token: Off
   - Add to access token: On
   - Add to lightweight access token: Off
   - Add to token introspection: On

7. Save the mapper.
8. Sign out of the frontend and sign in again to obtain a new token.

If the mapper already exists, check its settings instead of creating a duplicate.

## API access rules

- GET `/api/health` is public.
- GET `/api/me` requires a valid access token.
- All other requests are denied by default.
- CORS allows GET requests from `http://localhost:5173`.
- The API uses stateless bearer authentication.

## Run automated checks

From a separate PowerShell window at the repository root, run the backend tests:

```powershell
cd backend
.\mvnw.cmd test
```

Six tests passed: the application context test and five API tests.

The API tests cover:

- Public health access.
- Rejection of requests without a token.
- Rejection of a malformed token.
- Returning the authenticated user's identity.
- Rejection of an unapproved HTTP method for an authenticated user.

The identity and method tests use simulated JWT authentication.
They do not test cryptographic signature validation.

To build the frontend, open PowerShell at the repository root:

```powershell
cd frontend
npm.cmd run build
```

## Manual API checks

Keep the backend running and execute these commands in another PowerShell window.

Public health endpoint:

```powershell
curl.exe -i http://localhost:8080/api/health
```

Expected: HTTP 200 with `{"status":"UP"}`.

Protected endpoint without a token:

```powershell
curl.exe -i http://localhost:8080/api/me
```

Expected: HTTP 401.

Protected endpoint with a malformed token:

```powershell
curl.exe -i -H "Authorization: Bearer invalid-token" http://localhost:8080/api/me
```

Expected: HTTP 401.

To test an authenticated request, sign in through the frontend and select "Check protected API".

## Validation performed

- Frontend TypeScript compilation and Vite build passed.
- Manual sign-in with the test user succeeded.
- Manual sign-out followed by a new sign-in required credentials.
- The initial realm export was imported into a separate, fresh Keycloak 26.8.1 container.
- The imported realm's discovery endpoint returned the expected issuer.
- The later audience mapper was verified in the updated export.
- A frontend request with a Keycloak access token returned user data from `/api/me`.
- Requests without a token or with a malformed token returned HTTP 401.
- All six backend tests passed.

## Scope and limitations

This is a local development prototype using `start-dev` and the development database.
It is not a production deployment.

The JSON is an Admin Console partial export, not a complete backup or user migration.

Backend JWT validation is configured, but dedicated negative tests for invalid signatures, expired tokens, wrong issuers, and wrong audiences remain to be added.

Full SEC01 verification, role-based authorization, staff MFA, DPoP, session hardening, and rate limiting remain unfinished.
SEC01 is not yet complete.

CSRF protection is currently disabled for the bearer-only API.
This must be reviewed when cookie-based authentication is introduced.