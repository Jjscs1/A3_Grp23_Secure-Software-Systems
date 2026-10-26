# SEC-04: reauthentication and session closure for voting

This change connects the identity session to blind-token issuance.
It keeps ballot submission anonymous. It does not port SEC-12 from the
teammate's separate JavaScript app.

## Flow

1. A signed-in voter chooses Verify identity to vote.
2. POST /api/session/prepare-cast creates a random, one-use challenge, bound
   to that subject. It closes the old application session and clears its cookie.
3. The browser starts Keycloak login with prompt=login, max_age=0 and that
   challenge as the OIDC nonce. Existing ID-token nonce validation stays enabled.
4. The backend verifies the new signed ID token during session bootstrap.
   To allow preparation, the nonce must match an unused challenge for this user.
   The challenge lasts 180 seconds. auth_time must be newer than the previous
   authentication and not older than challenge creation. The original 60-second
   freshness check at bootstrap still applies.
5. The voter chooses Prepare my ballot. Encryption happens first. If it fails,
   the app does not request a voting token and does not consume eligibility.
6. POST /api/tokens requires the verified casting session, authenticated within
   the last 60 seconds. It closes that session in the same database transaction
   as eligibility checking, marking the ballot issued and blind signing.
7. The browser unblinds the signature, clears its local signed-in user and
   shows a separate anonymous confirmation panel. Only the anonymous token
   and encrypted envelope remain in memory for that panel.
8. The voter chooses Cast my vote. /api/ballots receives no identity token,
   DPoP proof or cookie. The server session was already closed at step 6.
9. On success the anonymous data is removed from the page state. An uncertain
   network result allows retrying the SAME token; it never requests a new one.
   The anonymous panel also clears after 90 seconds without activity.

The session is closed BEFORE anonymous casting, not in an identity-bearing
ballot request. This is an intentional timing choice: a successfully cast ballot
cannot leave the issuing application session usable, even if frontend logout is
skipped. It preserves the team's blind-token separation between identity and
ballot storage. There is no ballot serial or ciphertext in a session/challenge row.

A legitimate fresh login can create another ordinary application session.
This change invalidates the issuing session, not the user's account permanently.
Keycloak SSO is not globally revoked as part of anonymous casting; the application
requests explicit authentication on the next sign-in.

## Shared-file integration

TokenController still uses the existing TokenIssuanceService through an IAM
wrapper. Token formats, blind signatures, eligibility rules and public ballot
endpoint remain unchanged. A failed eligibility check or signature operation
rolls back both the session closure and any voter changes.

App.tsx still exposes buildEncryptedBallot to the SEC-12 owner. The old dummy
pending-SEC-12 JSON has been replaced with an error. The application must not
claim it encrypted or cast a real ballot while encryption is disconnected.

The supplied teammate function is:

    sealBallot(preferences, keyInfo)

It returns an object with v, alg, kid, ek, iv and ct. An integration adapter
must supply real selected preferences and the intended election encryption
public key, and return JSON.stringify(await sealBallot(preferences, keyInfo)).
The actual election-key endpoint and preferences UI still need integration by
the SEC-12 owner. Do not use the blind-signing key as the encryption key.

## Checks performed in the review environment

Validation in the review environment: 108 backend tests and 44 frontend tests
passed, along with the frontend build and lint.

The tests use real signed ID/access tokens and DPoP proofs against the Spring
filters, plus H2. They check:

- an ordinary login cannot issue a voting token;
- the old session becomes invalid when reauthentication starts;
- a new signed nonce grants permission; a random nonce does not;
- challenges expire and cannot be consumed twice;
- preparation permission expires after 60 seconds;
- failed eligibility or signing rolls back session closure;
- successful blind-token issuance deletes the cookie and invalidates /api/me;
- anonymous casting with that token succeeds, and the old session remains 401.

The end-to-end security test uses a labelled test envelope. It tests IAM and
anonymous transport, not the correctness of ballot encryption. Frontend tests
cover nonce forwarding, verification before preparation, no issuance on encryption
failure, anonymous handoff, explicit cast and retrying the same token.

Existing business-rule tests keep their session-filter isolation. The dedicated
session integration suite explicitly enables session enforcement. Keep
 evp.session.enabled=true for the running demo (the default).

## Checks still needed on the user's machine

- Run backend tests and frontend tests/build/lint after applying.
- Restart both services. Sign in as voter1.
- Choose Verify identity to vote and complete the password prompt.
- Verify the page reports Identity verified and shows Prepare my ballot.
- Until SEC-12 is connected, Prepare my ballot must report that it cannot
  prepare the ballot. It must not send POST /api/tokens in that case.
- After SEC-12 integration, test the complete preparation and cast flow once.
  Verify /api/me returns 401 for the issuing session after preparation and cast.
- Save final Keycloak exports after all requested configuration work is finished.

The complete live encrypted voting demonstration is not yet verified. Do not
close SEC-04 solely because the automated tests passed.

## Known prototype limits

- A lost issuance response can leave eligibility consumed without a recoverable
  client token. This was already a limitation of the team's blind-token flow.
  Closing the identity session atomically does not solve that recovery problem.
- Refreshing/closing the page loses the anonymous token held only in memory.
- Anonymous token expiry is not added to the existing blind-token protocol.
  The confirmation panel's 90-second clearing is a browser safeguard for that
  anonymous capability; server identity-session expiry is enforced independently.
- Challenge/session records need retention cleanup and multi-instance review.
- No claim is made that the currently disconnected encryption path implements SEC-12.
