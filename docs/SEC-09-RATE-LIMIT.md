# SEC-09: API rate limits without account lockout

This is the backend part of SEC-09. Keycloak login endpoints are protected
by the separate gateway described in SEC-09-KEYCLOAK-GATEWAY.md. Both layers
were deployed and checked locally on 27 October 2026.

## Behaviour

The backend counts requests in a rolling 60-second window. The 21st
POST to `/api/public/enrolment-check` from one socket IP is rejected.
The response is HTTP 429, `Retry-After` in whole seconds rounded up,
`Cache-Control: no-store`, and `{"error":"RATE_LIMITED"}`.

| POST endpoint | Per IP | Per authenticated account | Total per endpoint |
| --- | ---: | ---: | ---: |
| /api/public/enrolment-check | 20 | Not used: public lookup | 1000 |
| /api/ballots | 30 | Not used: anonymous casting | 1000 |
| /api/tokens | 30 | 10 | 1000 |
| /api/session/start | 30 | 10 | 1000 |
| /api/session/prepare-cast | 30 | 10 | 1000 |

Only the enrolment limit of 20/minute comes directly from the acceptance
criteria. The other limits are explicit prototype defaults for review.

An IP filter runs before authentication and body parsing. Failed and
malformed attempts count against the IP budget. After authentication,
a second filter uses a hash of the verified JWT issuer and subject for
the account budget. It does not trust a submitted username or parse an
unverified token to identify an account. Limits are separate for each
endpoint. Rejected requests do not extend the full budget's window.

The limiter does not change user records, credentials, account-enabled
flags, voter eligibility or Keycloak lockout settings. Requests work again
after the applicable window expires. A rate limit is a temporary request
restriction, not an account lock. Requests may remain throttled while
new allowed traffic continues, or while a shared IP/endpoint is busy.

No request bodies, passwords, ballot serials, tokens or preferences are
read or logged by the limiter. Anonymous ballot submission stays anonymous.
The account budget applies at authenticated voting-token issuance, not at
anonymous casting. There is no per-account limit on public enrolment lookup.

Health checks, preflight requests, session activity and sign-out are not
rate limited by this policy. Existing DPoP and session checks remain in place.

## IP handling and deployment limits

The backend uses `getRemoteAddr()` and disables forwarded-header rewriting.
Client-supplied `Forwarded` and `X-Forwarded-For` cannot choose a new bucket.
Encoded paths are normalized before matching the rate-limit policy.

The local Vite proxy connects to the backend from loopback. Browsers behind
that proxy share its backend IP bucket. The direct localhost backend test
below proves the acceptance threshold; it does not prove distinct end-user
IP handling through a production proxy.

Before deployment, use a controlled ingress that supplies a verified client
IP and prevent clients from bypassing it. Do not simply trust arbitrary
forwarding headers. Shared networks can still share one IP budget.

Counters are in memory in one Java process. Restarting the backend resets
them. Multiple backend replicas do not share budgets. Production deployment
needs a shared atomic store or rate limiting at a non-bypassable ingress.
The map is capped at 10,000 keys. Expired keys are removed; live keys are not
evicted to make room for new clients. If capacity is full, new keys get a
temporary 429. This bounds memory but can reduce availability under flooding.
The per-endpoint ceiling also intentionally affects all callers under load.

## Automated checks

`RequestRateLimiterTests` checks the exact threshold, Retry-After rounding,
rolling-window recovery, rejected-request behaviour, IP/endpoint separation,
aggregate endpoint limits, concurrent requests and bounded memory.

`RateLimitApiTests` uses the real Spring application and filter chain. It
checks HTTP 429 and headers, recovery, anonymous ballot limits, forged
forwarding headers, encoded-path handling, account limits across IPs,
invalid authentication, untrusted usernames, health and CORS preflight.
Account tests use authenticated mock JWTs; signature/DPoP verification is
covered by the separate existing cryptographic tests.

The standard test profile disables rate limiting so existing feature tests
do not consume each other's budgets. `RateLimitApiTests` explicitly enables
it. It uses a controlled monotonic time source, so no real 60-second wait is
needed. It reduces the enrolment response pad to 1 ms only in that test
context; the normal SEC-08 response-time setting is unchanged.

Run from `backend`:

```powershell
.\mvnw.cmd test
```

## Manual acceptance check

Start the backend with the existing local environment variables. Use a
freshly started instance or wait at least a minute since the last enrolment
request. In another PowerShell window, run:

```powershell
$body = '{"fullName":"Nobody Example","dateOfBirth":"1990-01-01","postcode":"3000"}'
1..21 | ForEach-Object {
    $attempt = $_
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8080/api/public/enrolment-check" -ContentType "application/json" -Body $body -ErrorAction Stop
        [pscustomobject]@{Attempt=$attempt; Status=[int]$response.StatusCode; RetryAfter=$response.Headers['Retry-After']}
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $response = $_.Exception.Response
        [pscustomobject]@{Attempt=$attempt; Status=[int]$response.StatusCode; RetryAfter=$response.Headers['Retry-After']}
    }
}
```

Expected: attempts 1-20 return 200; attempt 21 returns 429 and a positive
Retry-After value. Wait 60 seconds and send one more enrolment check: it
should return 200 again. The test uses a dummy identity and does not modify
the electoral roll. Record the actual results before claiming a manual pass.

## Login protection and scope

The deployed Keycloak gateway supplies HTTP 429 and Retry-After for public
login routes. The live Admin Console showed Brute Force Mode Disabled in
both evp and evp-staff. The API limiter does not modify account state.

Unauthenticated login uses a submitted-username-plus-IP budget, not a global
username-only budget. Authenticated API account limits use verified issuer
and subject across IPs. Public enrolment and anonymous ballots use IP and
endpoint budgets without associating a ballot with a voter account.

These are explicit account-budget semantics. A requirement for a global
per-account login limit across IPs would need a separate design decision;
this implementation does not claim that behaviour.

The manual enrolment threshold and recovery check is recorded below. The
production proxy, shared-counter and capacity limitations above still apply.
/api/session/start is application session setup after OIDC authentication;
the gateway separately protects the Keycloak login routes.

References:
- https://www.rfc-editor.org/rfc/rfc6585.html#section-4
- https://docs.spring.io/spring-security/reference/servlet/architecture.html

## Manual check on 27 October 2026

We sent 21 enrolment-check requests from the same local IP.
Requests 1-20 returned HTTP 200.
Request 21 returned HTTP 429 with Retry-After: 53.
After waiting, another request returned HTTP 200.

This confirms the enrolment endpoint limit and automatic recovery.
It does not test Keycloak login rate limiting or account lockout settings.
