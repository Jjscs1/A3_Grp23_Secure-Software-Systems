# EVP Part Three: SEC-05, SEC-07, SEC-08, SEC-10, SEC-11

Theme: Enrolment, eligibility and admin controls. Stack per the team doc: Spring Boot 3.3 (Java 21), PostgreSQL, React 18 with TypeScript.

## Committing it in steps

The `evp-junaid-by-requirement` folders hold the same code split into one commit each. Copy the contents of each folder into the repo root (where `backend/` and `frontend/` live), allowing overwrites, in this order:

| Order | Folder | Commit message |
|---|---|---|
| 1 | `00 Shared skeleton` | `Shared skeleton: Spring Boot, React, base security config` |
| 2 | `SEC-05 Dual authorisation (four-eyes)` | `SEC-05: four-eyes approval for ballot and recount changes, closes #5` |
| 3 | `SEC-07 XSS prevention and security headers` | `SEC-07: CSP, security headers and XSS lint rules, closes #7` |
| 4 | `SEC-08 Anti-enumeration enrolment check` | `SEC-08: exact-match, constant-time enrolment check, closes #8` |
| 5 | `SEC-10 Eligibility validation before ballot issue` | `SEC-10: server-side eligibility check before ballot issue, closes #10` |
| 6 | `SEC-11 Anonymous single-use voting token` | `SEC-11: blind-signed single-use voting token, closes #11` |

Check the issue numbers on GitHub first. Three files change in more than one step on purpose: `SecurityConfig.java` and `eslint.config.js` (SEC-07 adds to both) and `App.tsx` (each step adds its screen). Every step was checked to compile, lint and pass its own tests, so teammates can pull after any commit. Run `mvn test` in `backend/` after each step.

## Run the tests first

```
cd backend && mvn test          # needs Java 21 and Maven
cd frontend && npm install && npm test && npm run lint && npm run build
```

What has already been checked, and what hasn't:

| Part | Status |
|---|---|
| Core security logic (eligibility rules, lookup key, timing padder, dual control rules, blind signer, CSP) | Compiled and run: 37 checks pass |
| SEC-11 cryptography against the real browser library (@cloudflare/blindrsa-ts) | Interop checked both ways: JS blinds, Java signs, both sides verify |
| Frontend (all components, lint rules, production build) | 15 tests pass, lint clean, builds |
| Spring layer (controllers, JPA, config) and JUnit tests | Compiled against stand-in types only. **Not yet run.** Run `mvn test` before committing anything and fix what fails. |

## Where each acceptance criterion lives

| Issue | Checkbox | Code | Test |
|---|---|---|---|
| SEC-05 | Proposer approving own change returns 403 | `changerequest/DualControlPolicy`, `ChangeRequestService`, DB CHECK on `ChangeRequestApproval` | `ChangeRequestApiTest.selfApprovalForbidden`, `.databaseCheckConstraint` |
| SEC-05 | Exclusion with 1 approval, or no court order, not applied | `DualControlPolicy.requiredApprovals`, `.validateProposal` | `.exclusionNeedsTwoApprovals`, `.exclusionWithoutCourtOrderRejected` |
| SEC-05 | Every step in the audit log | `ChangeRequestService` calls `AuditLogger` | `.everyStepAudited` |
| SEC-07 | `<script>` candidate name renders as text | `frontend/src/components/CandidateList.tsx` | `CandidateList.test.tsx` |
| SEC-07 | CSP has no unsafe-inline or unsafe-eval | `security/ContentSecurityPolicy`, `SecurityHeaders` | `SecurityHeadersApiTest` |
| SEC-07 | ESLint fails on dangerouslySetInnerHTML | `frontend/eslint.config.js` | `eslint-xss.test.ts` |
| SEC-08 | Identical status and body shape | `enrolment/EnrolmentCheckService` | `EnrolmentCheckApiTest.identicalStatusAndShape` |
| SEC-08 | Timing within 50 ms | `enrolment/ResponseTimePadder` | `.responseTimesWithin50ms` |
| SEC-08 | Partial match returns not found | `enrolment/EnrolmentLookupKey` (HMAC exact match) | `.partialMatchReturnsNotFound` |
| SEC-10 | Refused: under 18, not enrolled, wrong division, already voted | `eligibility/EligibilityPolicy`, `EligibilityService` | `EligibilityPolicyTest`, `TokenFlowApiTest` (API level) |
| SEC-11 | Token signature verifies against election key | `token/BlindSigner` | `BlindSignerTest`, `TokenFlowApiTest.issuedTokenVerifies` |
| SEC-11 | Signer never sees the unblinded serial | `frontend/src/security/blindToken.ts` | `blindToken.test.ts`, `TokenFlowApiTest.serialNeverReachesServer` |
| SEC-11 | Same serial again returns 409 | `ballot/SpentToken` UNIQUE, `BallotSubmissionService` | `.reusedTokenConflict` |
| SEC-11 | Two concurrent submissions produce one ballot | UNIQUE constraint plus retry in `BallotSubmissionService` | `.concurrentSubmissionsProduceOneBallot` |

## What the application contains, against the brief

| Brief requires | Status | Where |
|---|---|---|
| Front end GUI | Done for these five requirements | `frontend/` React: enrolment check, candidate list, voting, delegate console |
| Server side back end | Done | `backend/` Spring Boot |
| RESTful web services | Done | Endpoints listed below |
| Database | Done | PostgreSQL 16 via `docker-compose.yml`; JPA entities; tables listed below |
| Unit tests for core security | Done | 8 backend test classes, 5 frontend test files |
| Login (identity provider) | **Not here: SEC-01/02 owner** | Without it, the authenticated screens (voting, delegate console) cannot be used in the demo |
| CI/CD pipeline (optional in brief) | **Not here: SEC-20 owner** | GitHub Actions running these tests |
| The other 15 requirements | Teammates | Integration points below |

REST endpoints in this code:

| Method and path | Who | Requirement |
|---|---|---|
| `POST /api/public/enrolment-check` | Anyone | SEC-08 |
| `GET /api/tokens/public-key` | Anyone | SEC-11 |
| `POST /api/tokens` | Signed-in voter | SEC-10, SEC-11 |
| `POST /api/ballots` | Anyone (anonymous by design) | SEC-11 |
| `GET /api/candidates?division=` | Signed-in user | SEC-05, SEC-07 |
| `GET /api/change-requests` | Delegate or auditor | SEC-05 |
| `POST /api/change-requests` | Delegate | SEC-05 |
| `POST /api/change-requests/{id}/approvals` | Delegate | SEC-05 |
| `POST /api/change-requests/{id}/rejection` | Delegate | SEC-05 |

Database tables (Hibernate creates them on first start):

| Table | Holds | Requirement |
|---|---|---|
| `voter` | Electoral roll: lookup key, date of birth, citizen, enrolled, division, has_voted | SEC-08, SEC-10, SEC-11 |
| `change_request` | Proposed changes and their status | SEC-05 |
| `change_request_approval` | One row per approval, CHECK approver <> proposer | SEC-05 |
| `candidate` | Candidates added through approved changes | SEC-05 |
| `spent_token` | SHA-256 of each used token serial, UNIQUE | SEC-11 |
| `stored_ballot` | Encrypted ballot envelopes (placeholder until SEC-12/13/14) | SEC-11 |

Note there is no link between `voter` and `spent_token` or `stored_ballot`. That gap is what keeps the ballot secret.

## Running it for the demo

```
cp .env.example .env                        # fill in the values, see comments inside
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out election-signing.pem
docker compose up -d                        # PostgreSQL on 127.0.0.1:5432

# export the same values for the backend (Spring Boot does not read .env itself)
export EVP_DB_PASSWORD=... EVP_LOOKUP_SECRET=... EVP_TOKEN_KEY_PATH=$PWD/election-signing.pem
export EVP_ELECTION_DAY=2026-05-02 SPRING_PROFILES_ACTIVE=demo
cd backend && mvn spring-boot:run           # port 8080
cd frontend && npm run dev                  # port 5173, proxies /api to 8080
```

The `demo` profile loads four sample voters (`DemoVoterSeeder`): one eligible, one under 18, one in another division and one non-citizen, so every SEC-10 outcome can be shown. The enrolment check succeeds for Jane Citizen, 1990-01-01, 3000. For the signed-in parts, the SEC-01 owner needs Keycloak users with the usernames `voter1`, `teen1`, `wills1` and `visitor1` (role VOTER), plus three delegates (role DELEGATE).

The app refuses to start if any secret is missing (fail closed). `.env` and `*.pem` are git-ignored.

## Things teammates need to know (integration points)

| Who | What |
|---|---|
| Identity and access (SEC-01, 03, 04, 09) | `config/SecurityConfig` is a placeholder. When merging, keep `SecurityHeaders.configure(http)`, the three `permitAll` lines and `@EnableMethodSecurity`. Rate limit the two public endpoints (SEC-09). Front end: call `setAccessTokenProvider` in `src/api/http.ts` from the OIDC module. |
| Whoever builds registration and address update | Set `Voter.lookupKey` with `EnrolmentLookupKey.compute(name, dob, postcode)`, and recompute it when the name or postcode changes, otherwise SEC-08 says "not found". |
| Ballot casting and cryptography (SEC-12, 13, 14, 19) | Implement `ballot/BallotSink` and delete `JpaBallotSink`/`StoredBallot`. Pass your encryption function as `buildEncryptedBallot` to `VotingPanel`. Never log request bodies on `/api/ballots`. |
| Tally, audit and pipeline (SEC-16, 17, 20) | Implement `audit/AuditLogger` (use REQUIRES_NEW) and delete `Slf4jAuditLogger`. The recount should read only `findByTypeAndStatus(RECOUNT_EXCLUSION, APPLIED)`. |

## Design decisions and limits (put these in the report)

1. has-voted is set when the token is issued, not when the ballot arrives. A voter who abandons the session cannot get a second token online and would vote on paper.
2. The token cannot be linked to the voter cryptographically. Timing and network metadata could still correlate issue and submission. SEC-13's IP stripping and hourly timestamps reduce this; production should also delay or separate submission.
3. A recount exclusion needs two approvers, neither of them the proposer (three delegates in total), plus a court order reference.
4. The bulk enrolment workflow applies to changes of more than 50 records.
5. The election signing key is a PEM file here. Production would use an HSM, split between trustees (SEC-15).
6. Hibernate creates the schema (`ddl-auto=update`). Production would use Flyway migrations.
7. Tests run on H2 in PostgreSQL mode. The demo and production use PostgreSQL.
