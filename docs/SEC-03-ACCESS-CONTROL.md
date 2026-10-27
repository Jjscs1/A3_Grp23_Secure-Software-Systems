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

## Required Keycloak setup (live setup still pending)

Applying this code does not configure the running Keycloak server. Until the
following setup is completed, an ADMIN account can sign in but has no active
ADMIN privilege. Do not mark the live elevation test as completed yet.

In the issuing `evp` realm:

1. Define the two attributes above in the managed User Profile. They are
   single-valued and optional. Set view/edit permissions to **admin only**,
   not user. Do not enable user editing of unmanaged attributes.
2. On the `evp-frontend` dedicated client scope, create one User Attribute
   protocol mapper for each attribute. Use the same attribute and claim names,
   JSON type `long`, Add to access token on, multivalued off. Do not use a
   mapper that calculates the current time on each token issue.
3. Keep the grant on the linked local `evp` user, where access tokens are
   issued. Do not import these attributes from an untrusted external profile
   or accept them from frontend input. Do not give application ADMIN users
   Keycloak realm-management permissions or permission to edit their grant.
4. An authorised Keycloak operator records the approved start and fixed expiry
   on the selected eligible account. Use a short grant for the demo; never
   pre-populate permanent grants or future default timestamps in exports.
5. Sign in again to obtain a signed token containing the grant. The grant
   does not replace staff WebAuthn or the application's DPoP/session checks.
6. Record who approved the grant and why using the team's administrative
   process. Enabling Keycloak admin events can retain operator actions.
   This patch does not implement an approval UI or an independent approval service.

For a short demo window, the operator can calculate values in PowerShell:

```powershell
$grantStart = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$grantEnd = $grantStart + 120
Write-Host "admin_elevated_at=$grantStart"
Write-Host "admin_elevation_expires_at=$grantEnd"
```

Use those fixed values only on the intended demo account after approval.
Refresh does not update them. To approve another window, the operator must
make a new explicit decision. The database/session layer is not a grant issuer.

Removing attributes/roles affects subsequently issued tokens. Already issued
signed tokens can retain their grant until the earlier of grant expiry or
access-token expiry; this patch does not add immediate grant revocation.
The Keycloak infrastructure operator remains a separate trusted configuration
administrator; this policy time-boxes application ADMIN, not the master realm.

After configuration, export the realm configuration again. User attributes,
links, passwords and WebAuthn credentials need separate local provisioning;
configuration-only export is not proof that another installation has those users.

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
This is backend evidence, not a claim that the live Keycloak mapper and demo
have already been configured or tested. Repeat `mvnw.cmd test` on the applied patch.

## References

- Spring Security method security:
  https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html
- Keycloak managed user-profile permissions and protocol mappers:
  https://www.keycloak.org/docs/latest/server_admin/index.html
