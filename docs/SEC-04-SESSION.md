# SEC-04: application session and idle timeout

This change adds the session foundation after the DPoP change.
It is not the whole SEC-04 requirement.

## What it does

- Every protected API call needs the DPoP token, its proof and the application cookie.
- The cookie is named `__Host-evp-session`. It has Secure, HttpOnly,
  SameSite=Strict and Path=/. It has no Domain attribute.
- The cookie contains a random identifier, not a JWT or user details.
- The database stores hashes of the cookie and access token.
- Sign-out closes the application session on the server and clears the cookie.
- A closed session cannot be reopened by replaying its token, ID token or a
  refreshed token from the same authentication event.
- The server rejects the session after 90 seconds without reported activity,
  or at access-token expiry, whichever happens first.
- API reads do not extend the idle timeout. The activity endpoint does.
- The frontend reports trusted keyboard, pointer and touch activity, at most
  once every 15 seconds. There is no automatic keepalive while idle.
- The browser also clears its user state at 90 seconds without activity.
- Anonymous requests still use credentials=omit and have no DPoP or access token.
  In particular, /api/ballots does not require a session and receives no cookie
  from our voting frontend.

## Opening a session

The frontend requests prompt=login and max_age=0 during sign-in.
After the callback, it sends the signed ID token to POST /api/session/start,
using the access token and its DPoP proof.

The backend verifies the ID token's RS256 signature, issuer, audience,
expiration, authorized party when present, subject, Keycloak sid and auth_time.
The ID token subject and sid must match the access token. Authentication must
be within the last 60 seconds (at most 5 seconds future clock tolerance).
The access token lifetime must be no longer than 600 seconds.
The existing frontend ID-token nonce verification remains enabled.

Session records are keyed by the issuer, subject, sid and authentication time.
They are stored in identity_bound_session in PostgreSQL. The current demo uses
Hibernate schema update to create this table. No database reset is needed.
A second start request for the same event only succeeds with its existing,
active cookie and the same access token; it never resets idle time.

The frontend waits for session setup before displaying protected panels.
A failed bootstrap requires another sign-in, rather than opening the panels.

## Cookie and CSRF behaviour

Cookies alone cannot authenticate: the application remains stateless at the
Spring Security HTTP-session level and always requires explicit DPoP headers.
The session cookie is an additional binding to the server record.
The session filter also rejects authenticated mutation requests whose Origin
is not the configured application origin. These properties prevent a normal
cross-site form from using the cookie as authentication.

Local application origin defaults to http://localhost:5173. The Vite proxy
preserves this Host and the Origin header. Use localhost consistently, not
127.0.0.1 in the browser. Use HTTPS for deployment. Secure-cookie localhost
behaviour must be checked in the demo browser; do not remove Secure to work
around a browser which refuses it.

Settings:
- evp.session.origin: application origin, default http://localhost:5173
- evp.session.enabled: true by default; keep enabled for the demo/application

The existing feature tests set evp.session.enabled=false to isolate their
business and DPoP tests from session bootstrap. BoundSessionIntegrationTests
explicitly sets it to true and uses real signed access tokens, ID tokens,
DPoP proofs, the filter, controllers and H2 session records. That suite checks
cookie binding, sign-out, replay, ID-token claims, origin, expiry, idle time,
activity and anonymous access. Frontend tests check idle timer behaviour and
cookie omission for ballot requests.

## Local check after applying

1. Run backend tests and frontend tests, build and lint.
2. Restart the backend and frontend.
3. Sign in in a fresh Incognito window as voter1.
4. In Network, /api/session/start should return 204 and /api/me should return 200.
5. In Application > Cookies > localhost, check __Host-evp-session has Secure,
   HttpOnly, SameSite Strict and Path /.
6. Leave the application untouched for at least 90 seconds. It should clear
   the signed-in view. The backend also rejects the old session independently.
7. Sign in again and check normal sign-out. /api/session/end should return 204.

Do not put access tokens, ID tokens or cookie values into screenshots committed
to the repository. Results of these live checks have not yet been recorded.

## Limits and the next change

This stage does not yet add a separate reauthentication step for casting or
close the session as part of casting. SEC-04 must stay open.
The token-issuance flow currently marks a ballot issued before submission.
The next change must preserve the anonymous token across that controlled flow
without attaching a voter identity to /api/ballots.

The idle policy currently applies to every signed-in user of this prototype,
not a separate polling-terminal mode. The server sees reported activity, not
physical activity. Activity batching can end a session earlier than the last
physical interaction plus 90 seconds. This is fail-closed; it does not extend
the session using background reads. The endpoint cannot prove that a human,
rather than code controlling a valid session, generated an activity request.

Idle expiry is enforced on request, not by a scheduled database deletion.
Revoked/expired rows remain as replay tombstones. Retention cleanup and
multi-instance transactional/concurrency review are not implemented yet.
The service serializes updates within one backend process and uses optimistic
locking in the database. The demo runs one backend instance.

Closing this application session does not revoke the user's Keycloak SSO
session by itself. Normal sign-out also requests Keycloak logout; after idle,
the next sign-in explicitly asks for authentication again. A legitimate fresh
authentication can open a new application session. A suspended browser cannot
keep the old server session valid just by delaying its local timer.

References:
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie
- https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
