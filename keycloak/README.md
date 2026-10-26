# Keycloak Local Setup

This guide explains how to run and test the login feature on your computer.

The shared project uses:

- Keycloak for sign-in.
- React for the frontend.
- Spring Boot for the backend.
- PostgreSQL for application data.

There are two Keycloak configuration files:

- `evp-realm.json`: the application realm, voter login, and staff identity provider.
- `evp-staff-realm.json`: the staff realm and its WebAuthn login flow.

The frontend and backend still use `evp` as their token issuer.
Staff can use the "Staff sign-in" option to sign in through `evp-staff`.

The staff connection has been tested with `staff-broker-test`.
Moving all staff accounts to this route is still unfinished.

## 1. Requirements

Install:

- Docker Desktop with Linux containers.
- Java 21.
- Node.js 24 and npm.

You also need a local copy of this repository.

Use PowerShell for the commands below.

The repository root is the folder containing `frontend`, `backend`,
and `keycloak`.

## 2. Start Keycloak

For a new setup, open PowerShell at the repository root.

Choose a local admin password:

```powershell
$adminCredential = Get-Credential -UserName admin -Message "Choose a local Keycloak admin password"
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
```

Create and start the container:

```powershell
docker run -d --name evp-keycloak -p 127.0.0.1:8081:8080 -v evp-keycloak-data:/opt/keycloak/data --mount "type=bind,source=$($PWD.Path)\keycloak,target=/opt/keycloak/data/import,readonly" -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD quay.io/keycloak/keycloak:26.8.1 start-dev --import-realm
```

Remove the password variables from this PowerShell window:

```powershell
Remove-Item Env:KC_BOOTSTRAP_ADMIN_PASSWORD
Remove-Variable adminCredential
```

The bootstrap password remains in the container settings.
Use a password dedicated to this local demo.

Check startup:

```powershell
docker logs --tail 100 evp-keycloak
```

On a fresh database, check that both `evp` and `evp-staff` are imported.
Both updated exports passed a fresh import test.
See section 15 for the results and remaining checks.

Docker saves Keycloak data in `evp-keycloak-data`.

If the container already exists, start it instead:

```powershell
docker start evp-keycloak
```

Startup import skips a realm that already exists.
Changing an exported JSON file does not update the running realm.

Do not delete the existing data volume to update settings.

Open http://localhost:8081/admin/ and sign in with the local admin account.

The exports do not include users, passwords, or registered passkeys.
They also hide client secrets. Complete the staff connection below
before testing staff login on a new setup.

## 3. Connect the staff realm

The connection uses:

- Application realm: `evp`
- Staff realm: `evp-staff`
- Identity provider alias in `evp`: `evp-staff`
- Login button label: `Staff sign-in`
- Client in `evp-staff`: `evp-staff-broker`

The application starts login through `evp`.
Choosing "Staff sign-in" sends the user to `evp-staff`.
After successful staff authentication, `evp` completes application login.

The application receives tokens issued by `evp`.
It does not directly accept `evp-staff` tokens.

### Staff realm settings

In `evp-staff`, open Realm settings.

Check:

- Frontend URL: `http://localhost:8081`
- Browser flow: `staff-webauthn`

The exported `evp` realm also uses `http://localhost:8081`
as its Frontend URL.

### Staff broker client

In `evp-staff`, open Clients > `evp-staff-broker`.

Check:

- Client authentication: On
- Standard flow: On
- Direct access grants: Off
- PKCE method: `S256`

Valid redirect URI:

```text
http://localhost:8081/realms/evp/broker/evp-staff/endpoint
```

Valid post-logout redirect URI:

```text
http://localhost:8081/realms/evp/broker/evp-staff/endpoint/logout_response
```

The post-logout URI is needed to return from staff logout.
Without it, the tested setup showed "Invalid redirect uri".

### Identity provider settings

In `evp`, open Identity providers > `evp-staff`.

Check these settings:

| Setting | Value |
| --- | --- |
| Display name | `Staff sign-in` |
| Client ID | `evp-staff-broker` |
| Client authentication | Client secret sent in the request body |
| Issuer | `http://localhost:8081/realms/evp-staff` |
| Authorization URL | `http://localhost:8081/realms/evp-staff/protocol/openid-connect/auth` |
| Token URL | `http://localhost:8080/realms/evp-staff/protocol/openid-connect/token` |
| User Info URL | `http://localhost:8080/realms/evp-staff/protocol/openid-connect/userinfo` |
| Logout URL | `http://localhost:8081/realms/evp-staff/protocol/openid-connect/logout` |
| Validate signatures | On |
| Use JWKS URL | On |
| JWKS URL | `http://localhost:8080/realms/evp-staff/protocol/openid-connect/certs` |
| PKCE | On |
| PKCE method | `S256` |

These URLs are for both realms running inside the same Keycloak container.

Browser requests use host port 8081.
Keycloak's internal requests use container port 8080.

Port 8080 in the internal URLs means Keycloak inside its container.
It does not mean the Spring Boot server running on the Windows host.

The issuer remains the public address on port 8081.

### Set the shared client secret after import

The partial exports hide the client secret.
The masked value is not a usable setup secret.

On a fresh setup:

1. Select `evp-staff`.
2. Open Clients > `evp-staff-broker` > Credentials.
3. Generate a new client secret.
4. Copy it locally.
5. Select `evp`.
6. Open Identity providers > `evp-staff`.
7. Paste the same value into Client Secret.
8. Save.

Do not put the actual secret in the repository.

Do not regenerate a working setup's secret unless you also update
the identity provider to match.

## 4. Create demo users

Each team member must create users on their own Keycloak setup.

### Existing application accounts

The following accounts were used in the `evp` realm:

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

These local staff accounts belong to the earlier role-based setup.
They have not all been moved to the separate staff realm.

For each account:

1. Open Users and create the account.
2. Use an email such as `voter1@example.com`.
3. Leave Email verified Off.
4. Set a local test password under Credentials.
5. Set Temporary to Off.
6. Open Role mapping > Assign role > Realm.
7. Assign the role in the table.

The username alone does not grant a role.

The four voter usernames match the application's seeded demo records.
The backend uses `preferred_username` to connect them to those records.

The application `ADMIN` role does not give access to the Keycloak
administration console.

### Staff broker test account

In `evp-staff`, create:

- Username: `staff-broker-test`
- Email: `staff-broker-test@example.com`
- First name: `Staff`
- Last name: `Broker Test`
- Email verified: Off

Set a local password with Temporary Off.
Add `Webauthn Register` as a required user action for initial setup.

Start login from the application and choose "Staff sign-in".
Complete passkey registration when asked.

The tested account has no voting or delegate role in `evp`.
The application therefore shows:

```text
No voting or delegate access is assigned to this account.
```

That message is expected for this test account.

Staff authentication and application permissions are separate.
Role mapping for the final staff accounts still needs to be completed.

## 5. Staff WebAuthn settings

### Separate staff realm

The `evp-staff` realm uses the browser flow `staff-webauthn`.

Its forms flow contains:

- Username Password Form: Required
- Staff second-factor subflow: Required
- WebAuthn Authenticator: Required
- OTP Form: Disabled
- Recovery Authentication Code Form: Disabled

The export also contains the original condition executions inside
the required second-factor subflow. Their configuration still needs
review as part of the final flow review.

The current setup uses a password followed by WebAuthn.
It is not a passwordless-only login flow.

### Earlier role-based checks in evp

The `evp` realm still uses `evp-browser-staff-mfa`.

These roles include the helper role `REQUIRE_WEBAUTHN`:

- `DELEGATE`
- `AEC_OFFICER`
- `ADMIN`

Its conditional second-factor flow uses:

- Condition - user role: Required
- Selected role: `REQUIRE_WEBAUTHN`
- Negate output: Off
- WebAuthn Authenticator: Required
- Condition - user configured: Disabled
- Condition - credential: Disabled
- OTP Form: Disabled
- Recovery Authentication Code Form: Disabled

The helper role selects the login check.
It does not itself grant access to application features.

The `VOTER` role does not include it.
Do not add it to the realm's default roles.

The existing local staff route remains in place.
The separate staff realm has not yet become the only staff login route.

### Registering passkeys

The local WebAuthn setup uses `localhost` as the relying party ID
and requires user verification.

For Windows Hello:

1. Start sign-in through the application.
2. Choose Register on the passkey registration page.
3. Select Windows Hello.
4. Complete the device PIN or fingerprint check.
5. Give the credential a useful label if asked.

The Windows Hello PIN is not the Keycloak account password.

Each account needs its own passkey registration.

For a virtual authenticator, use Chrome DevTools WebAuthn with:

- Protocol: `ctap2`
- Transport: `usb`
- Supports resident keys: enabled
- Supports user verification: enabled

Keep the test tab and virtual authenticator environment open.
In our test, disabling and re-enabling that environment lost
the virtual credentials.

A virtual authenticator is a test tool.
It is not a permanent credential backup.

## 6. Start PostgreSQL and the backend

Follow the root README section "Running it for the demo" to prepare
the database password, lookup secret, and signing key.

Keep local secrets and private keys out of Git.

From the repository root:

```powershell
docker compose up -d db
docker compose ps
```

The database should show `healthy`.

Before starting Spring Boot, set these environment variables
in the same PowerShell window:

- `EVP_DB_PASSWORD`
- `EVP_LOOKUP_SECRET`
- `EVP_TOKEN_KEY_PATH`
- `EVP_ELECTION_DAY`

Enable the demo voter records:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'demo'
```

Docker Compose reads the local `.env` file.
A separately started Spring Boot process does not automatically
load those variables from it.

Start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Keep the window open.

The backend runs at http://localhost:8080.
The demo profile creates four voter records in PostgreSQL.
Create the corresponding Keycloak accounts separately.

## 7. Start the frontend

Open another PowerShell window at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

Open http://localhost:5173.

Click "Sign in with Keycloak".

- For a local voter, enter the voter account details.
- For the staff broker test, choose "Staff sign-in".

The page should show the signed-in username.

The frontend passes the access token to the shared API helper
using `setAccessTokenProvider`.

The voting panel is shown to users with `VOTER`.
The change-request panel is shown to users with `DELEGATE`.

Hiding panels does not replace backend permission checks.

Tokens are kept in memory.
Refreshing the page clears local frontend authentication state,
but does not end the Keycloak session.

Use "Sign out" before testing a fresh login.
An existing SSO session may otherwise complete login without
asking for credentials again.

The shared app still has a placeholder for SEC-12 ballot encryption
in this version. Successful login does not prove ballot encryption works.

## 8. Token and API checks

The frontend uses an ID token to identify the signed-in user.
It uses an access token to call the backend.

Before storing the user, the frontend checks:

- ID token signature.
- Issuer.
- Audience.
- Expiry.
- Nonce.
- Subject.
- Issue time.
- Token lifetime.
- Authorized party when needed.

Failed validation prevents storage and clears the previous local user.

The backend checks access-token signature, issuer, timestamps,
and the `evp-backend` audience.

Application issuer:

```text
http://localhost:8081/realms/evp
```

This stays the same for brokered staff login.

Public API endpoints:

- GET `/api/health`
- POST `/api/public/enrolment-check`
- GET `/api/tokens/public-key`
- POST `/api/ballots`

The public ballot endpoint still checks its voting token
and request through the ballot service.

GET `/api/me` requires authentication.
Other `/api/**` routes require authentication, with additional
method-level role checks where implemented.

The backend maps `realm_access.roles` to authorities starting
with `ROLE_`.

CORS allows the local frontend origin.
The current allowed methods are GET, POST, and OPTIONS.

The API uses bearer tokens rather than a server-side login session.

## 9. Frontend client settings

In `evp`:

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

The `evp-backend-audience` mapper adds the backend audience
to access tokens, not ID tokens.

If an older setup is missing the mapper:

1. Open Clients > `evp-frontend`.
2. Open Client scopes > `evp-frontend-dedicated` > Mappers.
3. Add an Audience mapper.

Use:

| Setting | Value |
| --- | --- |
| Name | `evp-backend-audience` |
| Included Client Audience | Leave unselected |
| Included Custom Audience | `evp-backend` |
| Add to ID token | Off |
| Add to access token | On |
| Add to lightweight access token | Off |
| Add to token introspection | On |

Do not create a duplicate if it already exists.
Sign out and sign in again after changing token mappers.

## 10. Automated tests

From the repository root, run backend tests:

```powershell
cd backend
.\mvnw.cmd test
```

Identity tests are under:

```text
backend/src/test/java/com/evp/identity/
```

They cover application startup, API access, returned identity and roles,
blocked methods, voter restrictions, auditor access, and JWT validation.

Some tests use simulated authentication.
They do not prove that a real Keycloak token was validated.

The signed JWT tests check valid tokens and reject wrong signatures,
expired tokens, wrong issuers, and wrong audiences.

In another PowerShell window at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

The last reported frontend run passed 27 tests across seven files.
Build and lint also passed.

The identity part contains 12 frontend tests:

- Seven signed-token validation tests.
- Five user-manager and storage tests.

The build reported a browser compatibility warning about the
`crypto` import in the blind RSA dependency.
A successful build does not prove the complete voting flow works.

These automated checks do not replace manual WebAuthn testing.
They were not a fresh automated test of the latest broker exports.

## 11. Manual API checks

Keep the backend running and use a separate PowerShell window.

Public endpoint:

```powershell
curl.exe -i http://localhost:8080/api/health
```

Expected: HTTP 200 with `{"status":"UP"}`.

Protected endpoint without a token:

```powershell
curl.exe -i http://localhost:8080/api/me
```

Expected: HTTP 401.

Malformed token:

```powershell
curl.exe -i -H "Authorization: Bearer invalid-token" http://localhost:8080/api/me
```

Expected: HTTP 401.

For authenticated access, sign in through the frontend.
The page calls `/api/me` using the shared API helper.

The response includes:

- `subject`
- `username`
- `roles`

The current page does not use the earlier "Check protected API" button.

## 12. Earlier local staff tests in evp

These checks were completed before the separate staff broker setup:

| Account | Result |
| --- | --- |
| `delegate1` | Windows Hello was requested and login succeeded |
| `delegate2` | Virtual passkey registration and later login succeeded |
| `officer1` | Registration and later Windows Hello login succeeded |
| `admin-test` | Registration and later Windows Hello login succeeded |
| `voter1` | Password login succeeded without a passkey prompt |

Cancelling Windows Hello for `delegate1` produced Passkey Error.
Completing the check on retry allowed login.

The `delegate1` and `voter1` checks were repeated after
the helper-role condition was introduced.

### Virtual authenticator

For `delegate2`, a virtual passkey named `delegate2-virtual-test`
was registered.

After sign-out, another login required the password and passkey.
The application showed `Signed in as: delegate2`.
The recorded signature count increased from 1 to 3.

### Password-only attempt

In a fresh Incognito window without a virtual authenticator,
the correct password still led to a passkey prompt.

Cancelling verification produced Passkey Error.
That login attempt did not complete.

### OTP fallback check

Configure OTP was temporarily added as a required action
for `delegate2`.

After successful passkey authentication, OTP setup was completed.
The account then had Password, Webauthn, and Otp credentials.

A fresh login still required a passkey and offered no OTP alternative.
Cancelling the passkey check did not complete login.

No TOTP code was submitted during that login attempt.
This checked that OTP was not offered as a fallback.
It was not a direct test of a submitted TOTP code being rejected.

These checks apply to the earlier `evp` flow.
They do not establish the same result for every staff-realm route.

The WebAuthn check for `delegate3` has not been recorded as completed.

## 13. Staff broker tests

The following checks used the local instance on 27 October 2026:

- Frontend: `http://localhost:5173`
- Keycloak: `http://localhost:8081`
- Staff account: `staff-broker-test` in `evp-staff`
- Identity provider: `evp-staff` in `evp`

### Registration and login

We selected "Staff sign-in" and registered a virtual passkey.

The first broker attempt failed during the user-info request.
The log reported an issuer mismatch between ports 8080 and 8081.

Setting the `evp-staff` Frontend URL to `http://localhost:8081`
resolved the observed mismatch.

Login then reached the application as `staff-broker-test`.

### Fresh passkey login

We signed out and started another staff login.

After the password, Keycloak showed Passkey login.
Selecting "Sign in with Passkey" completed login.

The recorded credential signature count increased from 1 to 2.

This confirmed a fresh login using the virtual passkey.

### Logout

The first logout attempt showed "Invalid redirect uri".

We added this post-logout redirect URI to `evp-staff-broker`:

```text
http://localhost:8081/realms/evp/broker/evp-staff/endpoint/logout_response
```

Logout then returned to the application sign-in page.

Starting staff login again showed the username and password form.

### Cancelled passkey check

After entering the correct password, we cancelled the passkey check.

Keycloak showed Passkey Error.
That login attempt did not reach the application.

This demonstrates the tested password-only attempt did not complete.
It does not prove that all alternative authentication routes are blocked.

### OTP fallback check for the staff broker account

On 27 October 2026, we added Configure OTP as a required action
for staff-broker-test in evp-staff.

We completed passkey authentication, then registered OTP.
The Credentials page confirmed Password, Webauthn, and Otp.

We started another staff login in an Incognito window without
enabling a virtual authenticator there.

After the correct password, Keycloak required the registered
passkey. It did not offer OTP as an alternative.

Cancelling the passkey check showed Passkey Error.
The login attempt did not reach the application.

Result: registered OTP did not provide a fallback in the tested
staff broker browser flow.

We did not submit a TOTP code during that login attempt because
no OTP form was offered. This was not a direct test of a submitted
TOTP code being rejected.

### Virtual credential recovery

During the cancellation test, the DevTools virtual authenticator
environment was disabled.

Re-enabling it left the virtual credential tables empty.

We recovered the test account by:

1. Adding `Webauthn Register` as a required action.
2. Removing only the unavailable old WebAuthn credential.
3. Keeping the account password.
4. Registering a replacement passkey named `staff-broker-virtual-v2`.

After registration, we signed out and tested login again.
Keycloak requested a passkey and login succeeded.

This was a test-account recovery through the admin console.
It was not an application self-service recovery feature.

Previously registered virtual credentials on other accounts may also
need replacement if their virtual authenticator data was lost.

### Evidence

Screenshots were captured for:

- Passkey registration.
- Successful staff broker login.
- The passkey prompt.
- The signature count after authentication.
- Logout returning to the application.
- A fresh username and password prompt after logout.
- Passkey Error after cancellation.
- The replacement WebAuthn credential.

Keep passwords, tokens, OTP setup QR codes, and private keys
out of screenshots committed to Git.

## 14. Manual PKCE test

For an earlier application login test, we changed one character
in the saved `code_verifier` in browser session storage.

Keycloak rejected the token request with HTTP 400:

```json
{
  "error": "invalid_grant",
  "error_description": "PKCE verification failed: Code mismatch"
}
```

The frontend showed "Sign-in could not be completed".

A new attempt without changes succeeded.
`/api/me` returned HTTP 200 with the user's ID and username.

This tested the frontend-to-evp PKCE flow.

The staff broker is configured to use S256 too, but a separate
tampered-verifier test for the broker connection has not been performed.

## 15. Export configuration changes

Export both realms after changing their settings.

For each realm:

1. Select the realm.
2. Open Realm settings > Action > Partial export.
3. Enable Include groups and roles.
4. Enable Include clients.
5. Click Export.
6. Check the `realm` value in the downloaded JSON.
7. Copy it to the matching repository file.
8. Review the Git diff.

Use:

| Realm | Repository file |
| --- | --- |
| `evp` | `keycloak/evp-realm.json` |
| `evp-staff` | `keycloak/evp-staff-realm.json` |

The latest exports were checked for:

- The correct realm names.
- The enabled `evp-staff` identity provider.
- Broker URLs and issuer.
- Signature verification and JWKS settings.
- Broker S256 PKCE settings.
- The confidential `evp-staff-broker` client.
- Login and logout redirect URIs.
- The `staff-webauthn` browser flow.
- Masked client secrets.

The exports contain configuration, not users or passkeys.
They are not complete backups.

A new setup must create users, register passkeys, and configure
a matching broker client secret.

### Import tests already completed

Earlier `evp` exports were imported successfully into fresh
Keycloak 26.8.1 test containers.

The updated role-based MFA export also passed import.
Its discovery endpoint returned:

```text
http://localhost:8082/realms/evp
```

Those checks covered the earlier export, not the latest pair
containing the completed broker connection.

The latest evp and evp-staff exports were successfully imported
into a fresh Keycloak 26.8.1 test container.

Both discovery endpoints responded through port 8082.
They returned these issuers:

- http://localhost:8081/realms/evp
- http://localhost:8081/realms/evp-staff

These values match the Frontend URL saved in the exports.

This confirms successful import and discovery responses.
A complete login test on the fresh setup is still needed,
including users, passkeys, and a matching broker client secret.

## 16. Work still needed

This is a local development demo.
Keycloak uses `start-dev` and its development database.
PostgreSQL stores the application data.

Remaining work includes:

- Finish checking all SEC-01 acceptance requirements.
- Finish reviewing the staff browser flow.
- Complete staff application role mapping.
- Decide how existing local staff accounts move to the staff realm.
- Ensure the intended staff authentication route cannot be bypassed.
- Review first broker login and account-linking behaviour.
- Review other authentication routes beyond the tested staff browser flow.
- Test the latest pair of exports on a fresh setup.
- Finish SEC-03 role and permission coverage.
- Add DPoP.
- Complete session security requirements.
- Add rate limiting.

SSO can reuse existing sessions.
Policy changes do not by themselves prove that all existing sessions
have been rechecked.

The API currently disables CSRF protection because authentication
uses bearer tokens rather than cookies.
Review this if cookie-based authentication is introduced.

The successful tests describe the accounts and routes tested.
The full identity and access scope is not yet complete.