# SEC-09: local Keycloak login gateway

This adds a local OpenResty gateway in front of Keycloak. The app stays
Spring Boot + React + PostgreSQL. OpenResty is proxy infrastructure, not a
second login system. Keycloak still validates passwords, WebAuthn and OIDC.

## Status and scope

The policy has 12 pure-Lua checks and 8 access-adapter checks. All 20
passed on Windows using the pinned OpenResty image, and the NGINX
configuration check passed. The gateway migration then completed successfully.

Live voter login, staff passkey login, discovery, IP throttling and automatic
recovery were checked. The actual observations and their scope are recorded
at the end of this document.

Test-Gateway.ps1 checks policy behaviour and configuration without changing
running containers. Automated coverage does not imply that every limit or
login route was manually tested.

## Limits

Each rolling window lasts 60 seconds, separately for each realm and endpoint
group. Covered realms include evp, evp-staff and master.

- 30 requests per socket IP per endpoint group.
- 10 requests per submitted username AND socket IP per endpoint group,
  when the form contains a username.
- 1000 requests total per realm/endpoint group.
- HTTP 429 includes Retry-After rounded up, no-store, and a generic JSON error.
- Rejected requests do not extend a full budget's expiration.

The groups cover OIDC authorization, token, registration and device/extension
routes, plus all login-actions routes (including password and WebAuthn form
submissions). Different query strings, session codes and login-action steps
do not create fresh login-actions budgets.

Logout, discovery, signing keys, static files and preflight are exempt.
The application session/logout and ballot limits are separate Java features.

## Account-limit tradeoff: do not overclaim

An unauthenticated username is only a claim. A global counter keyed only by
that name would let a caller exhaust a victim's allowance from another IP.
This gateway deliberately scopes that counter to account plus source IP.
It never disables an account, resets credentials or calls an admin lock API.
The existing realm exports have bruteForceProtected=false and
permanentLockout=false; this migration does not change either setting.

**This is not a global per-account login limit across IP addresses.** If the
requirement is interpreted to require that exact behaviour, it remains a gap
requiring a design decision. Do not mark it complete without acknowledging
this difference. Shared networks/NAT can still share a throttle. Endpoint
ceilings can affect all users during overload. Temporary 429 is not a promise
that users can never be denied service by a distributed attacker.

Forms without a username (e.g. later WebAuthn steps and code exchange), JSON
requests, and username/email aliases use the IP and endpoint budgets; account
canonicalization is not performed against Keycloak. ASCII case and whitespace
are normalized, but Unicode aliases are not resolved. No unverified name is
used to alter a Keycloak account. Global per-verified-account limits already
exist in Java for token issuance/session setup after authentication.

## Data and transport

The gateway does not store passwords or tokens. It temporarily parses form
parameters, uses only the submitted username for a SHA-256 fingerprint, and
keeps only bucket keys/timestamps in memory. Fingerprints are pseudonyms, not
anonymous data. Access logging is disabled. Error logging is restricted to
critical failures; never enable request/body/query debug logging during the
demo. Malformed/duplicate username fields and overlarge forms fail closed.

Use only one worker: limiter operations have no yielding or I/O and are atomic
within that worker. State is bounded to 10,000 keys; expired keys are removed.
When capacity is exhausted, new buckets get 429 instead of evicting live
limits. Restart/reload resets state; overlapping graceful-reload workers and
multiple replicas do not share limits. A production deployment needs a shared
atomic counter store, authenticated proxy boundaries, TLS and capacity review.

The gateway uses the socket peer IP. It ignores attacker-supplied forwarding
headers and replaces them before forwarding to Keycloak. Docker Desktop may
present several local callers as the same source IP. Local tests do not prove
production end-user IP attribution.

## Local topology

- evp-keycloak: original container, stopped and retained for rollback.
- evp-keycloak-internal: same Keycloak 26.8.1, same evp-keycloak-data volume,
  port 8080 inside the dedicated Docker network, NO host port publication.
- evp-keycloak-gateway: publishes only 127.0.0.1:8081, proxies to internal 8080.
- Browser/app issuer remains http://localhost:8081/realms/evp.
- Staff issuer remains http://localhost:8081/realms/evp-staff.

Only the two new containers are attached to the dedicated evp-keycloak-sec09
network by the supplied script. Do not attach untrusted containers or publish
the internal Keycloak port. Docker/host administrators remain trusted and can
bypass the gateway; this is not isolation from a compromised host.

The existing staff broker token/userinfo/JWKS URLs use localhost:8080 inside
Keycloak. They remain trusted internal backchannel calls and bypass the public
gateway. Browser login and external token requests use public port 8081.
Do not change these URLs blindly or replace them with Docker names in the
browser. The script fixes the public hostname and enables xforwarded headers.

## Files and image

The image digest was obtained by docker pull and docker image inspect on the
user's machine, rather than inferred from a version tag:

`openresty/openresty@sha256:8e223fc9644c0b12de445165ecc6161786d9f6aa1064ea5e77c9c88ba5d494b6`

The source files are mounted read-only at /etc/evp-gateway. The scripts use
$PSScriptRoot, so they work even if PowerShell starts in another directory.

## Step 1: validate without changing Keycloak

From the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\keycloak\gateway\Test-Gateway.ps1
```

This execution-policy override is limited to this script process; it does not
change the machine's persistent execution policy. Review the supplied scripts.
Expected: 12 policy checks, 8 adapter checks and nginx configuration success.
If any check fails, stop here. Leave evp-keycloak running.

## Step 2: migrate only after the checks pass

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\keycloak\gateway\Enable-Gateway.ps1
```

The script verifies the original container/image/mount, refuses to overwrite
existing gateway containers/networks, and tests the pinned image again. It
stops the original briefly and creates a consistent tar backup of the whole
data volume under the user's .evp-demo directory. This is outside Git and may
contain credentials and keys: do not upload or commit it.

It then starts the new internal Keycloak and gateway, using the same data
volume and no bootstrap-admin password. It checks both discovery issuers.
On failure it attempts to stop/remove the new containers and restart the
original. It does not delete the original container or the data volume.

**Never run both Keycloak containers against this H2 volume at the same time.**
Changing containers does not migrate users between realms or erase passkeys.
A backup is retained for recovery, but restore is a separate deliberate action;
this script never overwrites the volume from an archive automatically.

## Step 3: live checks

- Confirm both discovery issuers and inspect docker ps/port: the internal
  container must not publish a host port.
- Fresh voter login should succeed with DPoP and the existing app session.
- Fresh staff broker login must still require its WebAuthn authenticator.
- Sign out should still work. Test both normal and staff routes.
- Run the burst check below, then wait 60 seconds and confirm normal recovery.
- Confirm live Keycloak account/realm settings; exports are not proof of every
  live account's state. Record actual observations rather than assuming passes.

For a rate-limit test without submitting real credentials, use an invalid
client ID on the authorization endpoint. This consumes the same IP/endpoint
budget but cannot log in or lock an account. Use a fresh window (wait 60 seconds
since prior authorization requests). In a separate PowerShell window:

```powershell
1..31 | ForEach-Object {
    $attempt = $_
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8081/realms/evp/protocol/openid-connect/auth?client_id=sec09-nonexistent&response_type=code' -ErrorAction Stop
        [pscustomobject]@{Attempt=$attempt;Status=[int]$response.StatusCode;RetryAfter=$response.Headers['Retry-After']}
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $response = $_.Exception.Response
        [pscustomobject]@{Attempt=$attempt;Status=[int]$response.StatusCode;RetryAfter=$response.Headers['Retry-After']}
    }
}
```

Expected: initial requests get a Keycloak invalid-client error, and request
31 gets 429 with Retry-After. After 60 seconds the invalid-client request
should reach Keycloak again. Repeat for evp-staff. This tests the gateway's
IP limit and recovery, not a complete failed-password/account-lockout test.
The username-plus-IP rule is covered by the automated adapter tests; manual
browser evidence remains to be collected without exposing real credentials.

## Rollback

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\keycloak\gateway\Disable-Gateway.ps1
```

This stops gateway and internal Keycloak first, then starts the original.
No volume or backup is deleted. Stopped new containers remain for inspection.
The enable script intentionally refuses to overwrite these on a later run.
Do not manually remove volumes or start both Keycloak processes.

After Docker restart, start internal Keycloak first and then gateway; keep the
original stopped while using the gateway. Public authentication rate limiting
is absent whenever the original direct-port container is used instead.

References:
- https://www.keycloak.org/server/reverseproxy
- https://www.keycloak.org/server/hostname
- https://github.com/openresty/lua-nginx-module
- https://github.com/openresty/docker-openresty

## Manual gateway checks on 27 October 2026

The gateway started successfully and both realm discovery checks passed.
The PowerShell readiness checks used 127.0.0.1; the configured issuers remained localhost.

Voter login succeeded with voter1.
Staff login succeeded with delegate1 after a passkey check.

We sent 31 requests to the evp authorization endpoint using a nonexistent client.
Requests 1-30 returned HTTP 400.
Request 31 returned HTTP 429 with Retry-After: 60.
After waiting, another request returned HTTP 400.

This confirms IP throttling and automatic recovery for the tested evp endpoint.
It does not prove all account and endpoint limits or the absence of account lockout.

## Account lockout checks on 27 October 2026

The live Admin Console showed Brute Force Mode set to Disabled
in both evp and evp-staff. No settings were changed.

For voter1, we entered an incorrect password three times,
then entered the correct password. Login succeeded without
an administrator unlocking the account.

This confirms recovery for the tested account and attempts.
It is not an exhaustive test of every account or authentication route.
