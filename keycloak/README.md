# Keycloak Local Setup

This guide explains how to run and test the login feature on your computer.

The project uses:
- Keycloak to manage user sign-in.
- React for the frontend.
- Spring Boot for the backend.

The `evp-realm.json` file contains the Keycloak settings for this project.

## Requirements

Before starting, install:
- Docker Desktop with Linux containers.
- Java 21.
- Node.js and npm.

You also need a local copy of this repository.

Use PowerShell for the commands below.
The repository root means the main project folder that contains
`frontend`, `backend`, and `keycloak`.

## 1. Start Keycloak

For a new setup, open PowerShell at the repository root.

Choose a local admin password:

```powershell
$adminCredential = Get-Credential -UserName admin -Message "Choose a local Keycloak admin password"
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
```

Create and start the Keycloak container:

```powershell
docker run -d --name evp-keycloak -p 127.0.0.1:8081:8080 -v evp-keycloak-data:/opt/keycloak/data --mount "type=bind,source=$($PWD.Path)\keycloak,target=/opt/keycloak/data/import,readonly" -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD quay.io/keycloak/keycloak:26.8.1 start-dev --import-realm
```

Remove the password variables from this PowerShell window:

```powershell
Remove-Item Env:KC_BOOTSTRAP_ADMIN_PASSWORD
Remove-Variable adminCredential
```

The password still exists in the Docker container settings.
Use a password only for this local demo. Do not put passwords in Git.

Check the startup messages:

```powershell
docker logs --tail 60 evp-keycloak
```

On a new database, these messages should appear:

```text
Realm 'evp' imported
Import finished successfully
```

Docker saves the Keycloak data in `evp-keycloak-data`.

If the container already exists, use this command instead of
creating it again:

```powershell
docker start evp-keycloak
```

Keycloak does not import the JSON again if the `evp` realm already
exists. Editing the JSON does not change an existing realm.

## 2. Create a test user

Open http://localhost:8081/admin/ and sign in with your admin account.

Select the `evp` realm. Open Users and create this account:

- Username: `voter-test`
- Email: `voter-test@example.com`
- First name: `Test`
- Last name: `Voter`
- Email verified: Off
- Required user actions: None

Open Credentials and set a password for this test account.
Set Temporary to Off.

The realm export does not include users or their passwords.
Each team member needs to create their own test user.

The name `voter-test` does not automatically give the user
the VOTER role.

## 3. Start the frontend

Open PowerShell at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

Keep this window open.

Open http://localhost:5173 and click "Sign in with Keycloak".

Use the test account to sign in. The page should show:

```text
Signed in as: voter-test
```

Click "Sign out", then try signing in again.
Keycloak should ask for your login details again.

Tokens are kept in memory. Refreshing the page clears the
frontend login state, but it does not end the Keycloak session.
Use "Sign out" to end the session.

## 4. Start the backend

Open another PowerShell window at the repository root:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Keep this window open.

The backend runs at http://localhost:8080.

With all three services running, sign in through the frontend
and click "Check protected API".

The response should look like this:

```json
{
  "subject": "<user-id>",
  "username": "voter-test"
}
```

The subject is the user's unique ID.

## 5. How token checks work

The project uses two types of tokens:

- ID token: tells the frontend who signed in.
- Access token: allows the frontend to call the backend.

### Frontend checks

Before saving the signed-in user, the frontend checks:
- The ID token signature.
- The issuer: the Keycloak realm that created the token.
- The audience: the client the token is meant for.
- The expiry time.
- The nonce: a random value linked to the sign-in attempt.
- The user ID and token issue time.
- The token lifetime and authorized party when needed.

If a check fails, the frontend does not save the user.
It also removes any previous local user.

### Backend checks

The frontend sends the access token in the Authorization header.

The backend checks its RS256 signature, issuer, timestamps,
and `evp-backend` audience.

The current API rules are:
- GET `/api/health` is public.
- GET `/api/me` needs a valid access token.
- All other requests are denied.
- CORS allows GET requests from `http://localhost:5173`.

The API uses bearer tokens and does not use a server-side
login session.

## 6. Keycloak settings

- Realm: `evp`
- Issuer: `http://localhost:8081/realms/evp`
- Client ID: `evp-frontend`
- Client type: Public
- Standard flow: Enabled
- PKCE: `S256`
- Implicit flow: Disabled
- Direct access grants: Disabled
- Redirect URI: `http://localhost:5173/auth/callback`
- Post-logout redirect URI: `http://localhost:5173/`
- Web origin: `http://localhost:5173`
- Backend audience: `evp-backend`

The export includes the `evp-backend-audience` mapper.
It adds `evp-backend` to access tokens, not ID tokens.

### Add the audience mapper to an older setup

Only follow these steps if your existing realm does not
already have this mapper.

1. Open Clients and select `evp-frontend`.
2. Open Client scopes.
3. Select `evp-frontend-dedicated`.
4. Open Mappers.
5. Choose Configure a new mapper, or Add mapper → By configuration.
6. Select Audience.
7. Enter these settings:

| Setting | Value |
| --- | --- |
| Name | `evp-backend-audience` |
| Included Client Audience | Leave unselected |
| Included Custom Audience | `evp-backend` |
| Add to ID token | Off |
| Add to access token | On |
| Add to lightweight access token | Off |
| Add to token introspection | On |

Save the mapper. Sign out of the frontend and sign in again
to get a new token.

If the mapper already exists, check its settings.
Do not create another copy.

## 7. Run the tests

### Backend tests

Open PowerShell at the repository root:

```powershell
cd backend
.\mvnw.cmd test
```

All 11 backend tests passed:
- One test checks that the application starts.
- Five tests check API access rules.
- Five tests check access-token validation.

The API tests check public access, missing tokens, malformed
tokens, user details, and a blocked HTTP method.

The user-details and blocked-method tests use simulated
authentication.

The five token-validation tests use temporary RSA keys and
signed tokens. They check:
- A valid token is accepted.
- A wrong signature is rejected.
- An expired token is rejected.
- A wrong issuer is rejected.
- A wrong audience is rejected.

### Frontend tests

Open PowerShell at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd exec -- vitest run tests/idTokenValidation.test.ts tests/VerifiedUserManager.test.ts
```

All 12 frontend tests passed.

Seven tests use signed test tokens to check:
- A valid token is accepted.
- A wrong signature is rejected.
- A wrong issuer is rejected.
- A wrong audience is rejected.
- A wrong nonce is rejected.
- An expired token is rejected.
- A token without an expiry time is rejected.

Five tests simulate the verification result and check
the actual user manager and storage:
- The user is saved after successful verification.
- The user is not saved while verification is still running.
- Failed verification removes a previous local user.
- Missing nonce state is rejected.
- A different user ID is rejected.

### Build the frontend

From the frontend folder:

```powershell
npm.cmd run build
```

The frontend build passed.

## 8. Manual API checks

Keep the backend running.
Use another PowerShell window for these commands.

Check the public endpoint:

```powershell
curl.exe -i http://localhost:8080/api/health
```

Expected: HTTP 200 with `{"status":"UP"}`.

Check the protected endpoint without a token:

```powershell
curl.exe -i http://localhost:8080/api/me
```

Expected: HTTP 401.

Check it with a malformed token:

```powershell
curl.exe -i -H "Authorization: Bearer invalid-token" http://localhost:8080/api/me
```

Expected: HTTP 401.

To check a valid request, sign in through the frontend
and click "Check protected API".

## 9. Checks completed

- Sign-in with `voter-test` worked.
- The protected API returned the user's ID and username.
- After sign-out, signing in again required login details.
- Missing and malformed access tokens returned HTTP 401.
- All 11 backend tests passed.
- All 12 frontend tests passed.
- The frontend build passed.
- The initial realm export was imported into a separate,
  fresh Keycloak 26.8.1 container.
- That realm's discovery endpoint returned the expected issuer.
- The audience mapper was checked in the later updated export.

## 10. Work still needed

This is a local demo using Keycloak's development mode and
development database. It is not ready for production.

The JSON file is a partial configuration export.
It is not a full backup and does not include user accounts.

The following work is still needed:
- Test that an incorrect PKCE code verifier is rejected.
- Finish checking all SEC01 requirements.
- Add role-based access rules.
- Add staff MFA.
- Add DPoP.
- Complete the session security requirements.
- Add rate limiting.

SEC01 is not complete yet.

CSRF protection is currently disabled because the API uses
bearer tokens only. This must be reviewed if cookie-based
authentication is added.