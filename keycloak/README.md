# Keycloak Local Development Setup

This directory contains the `evp` realm configuration for the EVP prototype.

## Requirements

- Docker Desktop running with Linux containers.
- Node.js and npm installed.
- This repository cloned locally.

Run the PowerShell commands below from the repository root.

## 1. Start Keycloak on a new local setup

Choose a local administrator password when prompted.
Do not commit passwords to Git.

```powershell
$adminCredential = Get-Credential -UserName admin -Message "Choose a local Keycloak admin password"
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
```

Start Keycloak:

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

Keycloak skips startup import if the realm already exists.
Changing the JSON file does not automatically update an existing realm.

If this container already exists, start it instead of repeating `docker run`:

```powershell
docker start evp-keycloak
```

## 2. Create a test user

Open http://localhost:8081/admin/ and sign in with the administrator
credentials chosen above.

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

From the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

Open http://localhost:5173 and select "Sign in with Keycloak".

Sign in using the test user and the password you configured.

Expected result:

```text
Signed in as: voter-test
```

Select "Sign out", then start sign-in again.
Keycloak should ask for credentials again.

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

The frontend keeps user tokens in memory.
Reloading the page clears its local authentication state, but does
not itself end the Keycloak session. Use Sign out to end the session.

## Validation performed

- Frontend TypeScript compilation and Vite build passed.
- Manual sign-in with the test user succeeded.
- Manual sign-out followed by a new sign-in required credentials.
- Realm import into a separate, fresh Keycloak 26.8.1 container succeeded.
- The imported realm's discovery endpoint returned the expected issuer.

## Scope and limitations

This is a local development prototype using `start-dev` and the
development database. It is not a production deployment.

The JSON was generated using the Admin Console partial export.
It contains configuration, not a complete backup or user migration.

Backend token validation and authorization, staff MFA, DPoP,
session hardening, and rate limiting remain to be implemented and tested.
SEC01 is not yet complete.