# SEC-03: explicit access policies and temporary admin elevation

## Endpoint access

The request filter allows only the listed HTTP method/path combinations.
All other requests are denied. Each application controller mapping must also
have `@PreAuthorize`, directly or on its class. `EndpointSecurity` rejects
mapped methods without that annotation, even if a route is accidentally added
to the filter allowlist. Public methods use `@PreAuthorize("permitAll()")`.
The advisor covers application classes in `com.evp.*`; keep controllers there.
Do not make mapped methods final, since method security uses Spring proxies.

| Endpoint | Policy |
| --- | --- |
| GET /api/health | Public |
| POST /api/public/enrolment-check | Public |
| GET /api/tokens/public-key | Public |
| POST /api/ballots | Public, single-use anonymous token validated by the ballot service |
| GET /api/me, GET /api/candidates | Authenticated |
| POST /api/session/start, /activity, /end; GET /api/session/status | Authenticated; existing bound-session checks apply |
| POST /api/session/prepare-cast, POST /api/tokens | VOTER |
| GET /api/change-requests | DELEGATE or AUDITOR |
| POST /api/change-requests and its approval/rejection routes | DELEGATE |
| GET /api/admin/elevation | ADMIN with an active elevation grant |
| Any other method/path | Denied |

An ADMIN is not automatically a DELEGATE or VOTER. AEC_OFFICER has access to
the common authenticated endpoints only: there is no officer-specific operation
in the current application. Add one only with an explicit policy and tests.
Existing dual-control and eligibility rules remain in their services.
Multi-role accounts receive the union of their explicitly assigned roles.

## Admin grant contract

A realm ADMIN role is eligibility, not standing application privilege.
The verified access token must also contain both numeric Unix-second claims:

- `admin_elevated_at`: the time an authorised operator approved the grant.
- `admin_elevation_expires_at`: the fixed expiry approved with that grant.

The backend requires positive start <= current time < expiry, with expiry -
start <= 14,400 seconds. Expiry is exclusive: at exactly four hours the maximum
window has ended. Missing, malformed, future, reversed or overlong grants do
not produce ROLE_ADMIN. Other valid roles remain usable.

These checks run when each request is authenticated. The admin status endpoint
also checks the grant at invocation. `iat`, `auth_time`, login, access-token
expiry and refresh do not create or extend elevation. A new access token
carrying the old grant remains restricted by the original fixed expiry.

`GET /api/admin/elevation` reports an existing grant and its expiry. It cannot
create or extend one. There are no other administrative business operations
in this implementation yet. Future admin endpoints must require ROLE_ADMIN.
`/api/me` reports effective authorities, so expired ADMIN is not advertised.

## Keycloak configuration

The live setup below was completed in the issuing `evp` realm on
27 October 2026. The updated configuration was exported to
`keycloak/evp-realm.json`.

The managed User Profile defines two optional, single-valued attributes:

- `admin_elevated_at`
- `admin_elevation_expires_at`

Both attributes have admin-only view and edit permissions. They have no
default grant values. Application users cannot edit these managed attributes.

The `evp-frontend` dedicated client scope contains two User Attribute mappers:

| Mapper | User attribute and token claim | JSON type |
| --- | --- | --- |
| admin-elevation-start | admin_elevated_at | long |
| admin-elevation-expiry | admin_elevation_expires_at | long |

Both mappers add the claim to the access token only. ID token, UserInfo,
introspection and lightweight-token inclusion are disabled. Multivalued
mapping is disabled. Neither mapper calculates a new timestamp when a token
is issued.

The grant belongs on the linked local `evp` user, where application access
tokens are issued. Do not import these attributes from an untrusted external
profile or accept them from frontend input. Do not give application ADMIN
users Keycloak realm-management permissions or permission to edit their grant.

## Grant operation

An authorised Keycloak operator records the approved start and fixed expiry
on the selected eligible account. Use a short grant for the demo; never
pre-populate permanent grants or future default timestamps in exports.

For a ten-minute demo window, the operator can calculate values in PowerShell:

```powershell
$grantStart = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$grantEnd = $grantStart + 600
Write-Host "admin_elevated_at=$grantStart"
Write-Host "admin_elevation_expires_at=$grantEnd"
```

This command only calculates values. The operator must enter and save them
on the intended local `evp` account. The user then signs in again to obtain
a signed token containing the grant. The grant does not replace staff WebAuthn
or the application's DPoP/session checks.

Refresh does not update the fixed values. To approve another window, the
operator must make a new explicit decision. The database/session layer is
not a grant issuer.

Record who approved the grant and why using the team's administrative
process. Enabling Keycloak admin events can retain operator actions.
This implementation does not provide an approval UI or an independent
approval service.

Removing attributes/roles affects subsequently issued tokens. Already issued
signed tokens can retain their grant until the earlier of grant expiry or
access-token expiry; this implementation does not add immediate grant revocation.
The Keycloak infrastructure operator remains a separate trusted configuration
administrator; this policy time-boxes application ADMIN, not the master realm.

The export preserves the User Profile definitions and protocol mappers.
User-specific attribute values, account links, passwords and WebAuthn
credentials need separate local provisioning. A configuration-only export
does not recreate the tested users or their credentials.

## Automated checks

The backend suite passed locally with 168 tests, zero failures/errors/skips.
New coverage includes:

- An actual unannotated test endpoint is denied for all five roles; direct
  invocation of its Spring-managed handler is also denied by the advisor.
- Common reads, delegate/auditor reads, and cross-role denials for proposal,
  approval, rejection, voting-token issuance and cast preparation.
- Signed RS256 access tokens and real DPoP proofs for valid and expired admin
  grants, standing ADMIN without a grant, and a fresh token with an old grant.
- A fresh grant without the ADMIN role cannot elevate a voter.
- Four-hour boundary, malformed claims, future start, invalid ranges, and
  rechecking an unchanged token after its grant expires.
- Existing successful voter issuance and delegate dual-control tests still pass.

The role matrix uses mocked authentication to isolate authorisation rules.
The added JwtValidationTests exercise the real signature/DPoP/converter path.
The full backend suite also passed on the applied patch in the Windows
development environment.

## Manual elevation checks on 27 October 2026

We tested `admin-test`, which has the ADMIN realm role in `evp` and signs in
through the linked staff account. We inspected the application's `/api/me`
response after a fresh sign-in for each case.

| Case | Grant values | Observed effective roles |
| --- | --- | --- |
| No grant | Both attributes empty | `[]` |
| Active ten-minute grant | Start 1793070345, expiry 1793070945 | `["ADMIN"]` |
| Expired four-hour grant | Start 1793056245, expiry 1793070645 | `[]` |

The expired grant was deliberately backdated. Its expiry was 14,400 seconds
after its start and was already in the past when tested. Signing in again
did not restore ADMIN from that expired grant.

These checks confirm that the live attribute mappers and backend role
conversion produced the expected effective roles for the tested account.
They did not involve waiting four hours or directly testing an HTTP 403
response from `/api/admin/elevation` in the browser. Endpoint rejection and
the exact four-hour boundary are covered by the automated tests.

After the checks, both grant values were cleared and saved on `admin-test`.
The updated `evp` realm configuration was then exported.

## References

- Spring Security method security:
  https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html
- Keycloak managed user-profile permissions and protocol mappers:
  https://www.keycloak.org/docs/latest/server_admin/index.html