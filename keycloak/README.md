# Keycloak Local Setup

This guide explains how to run and test the login feature on your computer.

The shared project uses:

- Keycloak to manage user sign-in.
- React for the frontend.
- Spring Boot for the backend.
- PostgreSQL for the application database.

The `evp-realm.json` file contains the Keycloak settings for this project.

## 1. Requirements

Before starting, install:

- Docker Desktop with Linux containers.
- Java 21.
- Node.js 24 and npm.

You also need a local copy of this repository.

Use PowerShell for the commands below.

The repository root means the main project folder that contains
`frontend`, `backend`, and `keycloak`.

## 2. Start Keycloak

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

Do not delete your existing data volume just to update the settings.

## 3. Create demo users

Open http://localhost:8081/admin/ and sign in with your local
Keycloak admin account.

Select the `evp` realm.

The export contains roles and settings, but it does not contain
user accounts, passwords, or registered passkeys.

Each team member needs to create the demo accounts on their
own Keycloak setup.

Create these accounts under Users:

| Username | First name | Last name | Realm role |
| --- | --- | --- | --- |
| `voter1` | Jane | Citizen | `VOTER` |
| `teen1` | Sam | Young | `VOTER` |
| `wills1` | Alex | Brown | `VOTER` |
| `visitor1` | Chris | Visitor | `VOTER` |
| `delegate1` | Demo | DelegateOne | `DELEGATE` |
| `delegate2` | Demo | DelegateTwo | `DELEGATE` |
| `delegate3` | Demo | DelegateThree | `DELEGATE` |
| `officer1` | Demo | Officer | `AEC_OFFICER` |
| `admin-test` | Demo | Administrator | `ADMIN` |

For each account:

1. Use an email such as `voter1@example.com`.
2. Leave Email verified Off.
3. Leave Required user actions empty.
4. Create the user.
5. Open Credentials and set a local test password.
6. Set Temporary to Off.
7. Open Role mapping.
8. Choose Assign role, then Realm.
9. Assign the role shown in the table.

The username alone does not give a user a role.

The four voter usernames match the shared application's demo
voter records. The backend uses `preferred_username` to connect
these accounts to those records.

The `ADMIN` role belongs to the EVP application. It does not
automatically give access to the Keycloak administration console.

The older `voter-test` account can still be used for basic login
checks. It is not one of the four seeded demo voters.

## 4. Staff passkey setup

The realm uses the browser flow `evp-browser-staff-mfa`.

The following roles include the helper role `REQUIRE_WEBAUTHN`:

- `DELEGATE`
- `AEC_OFFICER`
- `ADMIN`

The browser flow checks for `REQUIRE_WEBAUTHN` after the
username and password step.

Inside the conditional second-factor flow:

- Condition - user role is Required.
- Its selected role is `REQUIRE_WEBAUTHN`.
- Negate output is Off.
- WebAuthn Authenticator is Required.
- Condition - user configured is Disabled.
- Condition - credential is Disabled.
- OTP Form is Disabled.
- Recovery Authentication Code Form is Disabled.

The WebAuthn settings use:

- Relying party ID: `localhost`
- User verification: Required

A staff user without a registered passkey is asked to register one.

For the Windows demo:

1. Sign in through the application.
2. On Passkey registration, click Register.
3. Choose Windows Hello.
4. Complete the fingerprint or device PIN check.
5. If asked for a name, use a label such as
   `delegate1-windows-hello`.

The Windows Hello PIN is the device PIN. It is not the
Keycloak account password.

Each staff account needs its own passkey registration.
A passkey registered for `delegate1` does not register one
for `delegate2`.

The `VOTER` role does not include `REQUIRE_WEBAUTHN`.
Do not add the helper role to the realm's default roles.

The helper role selects the login check. It is not intended
to grant access to application features.

## 5. Start PostgreSQL and the backend

Follow the repository root README section "Running it for the demo"
to prepare the database password, lookup secret, and signing key.

Keep local passwords, secrets, and private keys out of Git.

From the repository root, start PostgreSQL:

```powershell
docker compose up -d db
docker compose ps
```

The database container should show `healthy`.

Before starting Spring Boot, set the required environment variables
in the same PowerShell window:

- `EVP_DB_PASSWORD`
- `EVP_LOOKUP_SECRET`
- `EVP_TOKEN_KEY_PATH`
- `EVP_ELECTION_DAY`

For the demo voter records, also set:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'demo'
```

The local `.env` file is used by Docker Compose.
It does not automatically set variables for a separate
Spring Boot process.

Start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Keep this window open.

The backend runs at http://localhost:8080.

The demo profile creates the four demo voter records.
Keycloak accounts must still be created separately.

## 6. Start the frontend

Open another PowerShell window at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

Keep this window open.

Open http://localhost:5173 and click "Sign in with Keycloak".

Sign in with one of the demo accounts.

The page should show the signed-in username.

The frontend connects the access token to the shared API
helper using `setAccessTokenProvider`.

The current page shows the voting panel to users with `VOTER`
and the change-request panel to users with `DELEGATE`.

An account with only `AEC_OFFICER` or `ADMIN` does not see
those two panels. A message saying that voting or delegate
access is not assigned is expected for these accounts.

Hiding a panel is not a replacement for backend access checks.

Tokens are kept in memory. Refreshing the page clears the
frontend login state, but it does not end the Keycloak session.

Use "Sign out" to end the session.

The shared app still has a placeholder for SEC-12 ballot
encryption in this version. Do not treat a successful login
as proof that ballot encryption is complete.

## 7. How token checks work

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

The shared security configuration keeps these public endpoints:

- GET `/api/health`
- POST `/api/public/enrolment-check`
- GET `/api/tokens/public-key`
- POST `/api/ballots`

Public access does not mean that the endpoint accepts any input.
The ballot endpoint still checks the submitted voting token
and request through the ballot service.

GET `/api/me` needs a valid access token.

Other `/api/**` requests require authentication.
Some endpoints also check roles using method-level security.

The backend reads realm roles from `realm_access.roles`.
It maps them to Spring Security authorities with the `ROLE_` prefix.

CORS allows the configured local frontend origin.
The current configuration allows GET, POST, and OPTIONS.

The API uses bearer tokens and does not use a server-side
login session.

## 8. Keycloak client settings

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
5. Choose Configure a new mapper, or Add mapper > By configuration.
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

## 9. Run the tests

### Backend tests

Open PowerShell at the repository root:

```powershell
cd backend
.\mvnw.cmd test
```

The identity tests are under:

```text
backend/src/test/java/com/evp/identity/
```

They cover:

- Application startup.
- Public and protected API access.
- Missing and malformed access tokens.
- Returned user details and roles.
- A blocked HTTP method.
- Voter restrictions and auditor read-only access.
- JWT signature, expiry, issuer, and audience checks.

Some API and role tests use simulated authentication.
They do not prove that a real Keycloak token was validated.

The JWT validation tests use temporary RSA keys and signed tokens.
They check:

- A valid token is accepted.
- A wrong signature is rejected.
- An expired token is rejected.
- A wrong issuer is rejected.
- A wrong audience is rejected.

The shared backend tests also cover the other team members'
implemented features.

### Frontend tests

Open PowerShell at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
```

The last reported frontend run passed 27 tests across seven files.

The identity part contains 12 tests.

Seven tests use signed test tokens to check:

- A valid token is accepted.
- A wrong signature is rejected.
- A wrong issuer is rejected.
- A wrong audience is rejected.
- A wrong nonce is rejected.
- An expired token is rejected.
- A token without an expiry time is rejected.

Five tests simulate the verification result and check
the user manager and storage:

- The user is saved after successful verification.
- The user is not saved while verification is still running.
- Failed verification removes a previous local user.
- Missing nonce state is rejected.
- A different user ID is rejected.

### Build and lint

From the frontend folder:

```powershell
npm.cmd run build
npm.cmd run lint
```

Both checks passed in the last reported run.

The build reported a browser compatibility warning about the
`crypto` import in the blind RSA dependency. A successful build
alone does not prove that the full browser voting flow works.

The automated tests do not replace the manual Windows Hello checks.

## 10. Manual API checks

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

For a valid request, sign in through the frontend.
The signed-in page requests `/api/me` through the shared API helper.

The response contains:

- `subject`: the user's unique Keycloak ID.
- `username`: the preferred username.
- `roles`: the roles mapped by the backend.

The current shared page does not use the old
"Check protected API" button.

## 11. Manual staff login checks

The following checks were completed on the local setup:

| Account | Role | Result |
| --- | --- | --- |
| `delegate1` | `DELEGATE` | Windows Hello was requested and sign-in succeeded |
| `officer1` | `AEC_OFFICER` | Passkey registration was requested, and a later sign-in required Windows Hello |
| `admin-test` | `ADMIN` | Passkey registration was requested, and a later sign-in required Windows Hello |
| `voter1` | `VOTER` | Sign-in succeeded with the password without a passkey prompt |

The staff checks used Sign out before starting another sign-in.

For `delegate1`, cancelling the Windows Hello check showed
a Passkey Error and did not complete sign-in.
Retrying and completing the check allowed sign-in.

The `delegate1` and `voter1` checks were repeated after the
condition was changed to use `REQUIRE_WEBAUTHN`.

These results show the tested login behaviour for these accounts.
They do not prove that every SEC-02 requirement is complete.

The WebAuthn checks for `delegate2` and `delegate3` have not
been recorded as completed.

## 12. Manual PKCE test

We started a sign-in attempt and changed one character in its
saved `code_verifier` in browser session storage. We kept the
same sign-in request and completed the login.

Keycloak rejected the token request with HTTP 400:

```json
{
  "error": "invalid_grant",
  "error_description": "PKCE verification failed: Code mismatch"
}
```

The frontend showed "Sign-in could not be completed".

We then started a new sign-in attempt without changing anything.
Sign-in worked, and `/api/me` returned HTTP 200 with the test
user's ID and username.

Result: the incorrect PKCE verifier was rejected, and a new
normal sign-in still worked.

This was a manual test.

## 13. Save changes to the realm settings

After changing Keycloak settings:

1. Select the `evp` realm.
2. Open Realm settings.
3. Open Action > Partial export.
4. Turn Include groups and roles On.
5. Turn Include clients On.
6. Click Export.
7. Check the downloaded file.
8. Copy it to `keycloak/evp-realm.json`.
9. Review the Git diff before committing.

The latest exported file was checked for:

- The `evp-browser-staff-mfa` browser flow.
- The `REQUIRE_WEBAUTHN` condition.
- The helper role linked to `DELEGATE`, `AEC_OFFICER`, and `ADMIN`.
- Required WebAuthn user verification.
- Disabled OTP and recovery-code steps inside the staff second-factor flow.

The original realm export was successfully imported into a
separate, fresh Keycloak 26.8.1 container.
Its discovery endpoint returned the expected issuer.

The latest export containing the staff MFA changes still needs
a fresh import test. The earlier import test does not validate
these new changes.

## 14. Work still needed

This is a local demo using Keycloak's development mode and
development database. It is not ready for production.

PostgreSQL stores the application data.
The current Keycloak container uses its own development database.

The JSON file is a partial configuration export.
It is not a full backup and does not include user accounts
or their passkeys.

Work still needed includes:

- Finish checking all SEC-01 requirements.
- Complete the remaining SEC-02 checks and document their results.
- Test the updated realm export in a fresh setup.
- Finish the full role and permission rules for SEC-03.
- Add DPoP.
- Complete the session security requirements.
- Add rate limiting.

Some role checks and staff MFA are now implemented and tested,
but the full identity and access work is not complete.

Existing Keycloak sessions can be reused through SSO.
The manual tests above used sign-out first.
Session behaviour after a role or policy change still needs review.

CSRF protection is currently disabled because the API uses
bearer tokens only. This must be reviewed if cookie-based
authentication is added.