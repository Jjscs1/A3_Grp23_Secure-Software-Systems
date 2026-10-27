# Electronic Voting Platform (EVP): Group 23, Part Three

A working prototype of the secure Electronic Voting Platform, implementing all 20 security requirements (SEC-01 to SEC-20) chosen in Parts One and Two of the group project.

Stack: React 18 + TypeScript (Vite), Spring Boot 3.5 (Java 21), PostgreSQL 16, Keycloak, GitHub Actions.

## Team

| Name | Student ID | GitHub ID | Requirements |
| --- | --- | --- | --- |
| Junaid Javeed Shaik | s4180547 | [Jjscs1](https://github.com/Jjscs1) | SEC-05, SEC-07, SEC-08, SEC-10, SEC-11 |
| Khaled Ahmed Alhebbi | s3918789 | [khaled20100](https://github.com/khaled20100) | SEC-01, SEC-02, SEC-03, SEC-04, SEC-09 |
| Prince | s4223550 | [PJstorm-blip](https://github.com/PJstorm-blip) | SEC-06, SEC-12, SEC-13, SEC-14, SEC-19 |
| Arvindakshan Sambasivan Ravi | s4206952 | [Arvind9750](https://github.com/Arvind9750) | SEC-15, SEC-16, SEC-17, SEC-18, SEC-20 |

Each requirement is a GitHub issue (#1 to #20) with its user story and acceptance criteria.

## Requirements and where they live

Backend packages are under `backend/src/main/java/com/evp/`. Tests mirror them under `backend/src/test/java/com/evp/`.

| Req | Requirement | Code | Tests |
| --- | --- | --- | --- |
| SEC-01 | Federated voter authentication (OIDC + PKCE) | `keycloak/evp-realm.json`, `config/SecurityConfig`, `frontend/src/oidcUserManager.ts` | `identity/access/EndpointAccessTests` |
| SEC-02 | Phishing resistant MFA for staff (WebAuthn) | `keycloak/evp-staff-realm.json` | Keycloak checks in `keycloak/README.md` |
| SEC-03 | Deny by default access, time boxed admin elevation | `identity/access`, `config/SecurityConfig` | `EndpointAccessTests`, `AdminElevationTests` |
| SEC-04 | Bound, short lived sessions (DPoP, `__Host-` cookie, reauthentication) | `identity/session`, `frontend/src/session.ts` | `BoundSessionIntegrationTests` |
| SEC-05 | Dual authorisation (four eyes) | `changerequest` | `ChangeRequestApiTest` |
| SEC-06 | Injection prevention and input validation | `validation`, `frontend/src/security/inputRules.ts` | `validation/*Test` |
| SEC-07 | XSS prevention, CSP and security headers | `security`, `frontend/eslint.config.js` | `SecurityHeadersApiTest`, `eslint-xss.test.ts` |
| SEC-08 | Anti enumeration enrolment check | `enrolment` | `EnrolmentCheckApiTest` |
| SEC-09 | Rate limiting without lockout | `identity/ratelimit`, `keycloak/gateway` | `RateLimitApiTests`, `RequestRateLimiterTests` |
| SEC-10 | Eligibility validation before ballot issue | `eligibility` | `EligibilityPolicyTest`, `TokenFlowApiTest` |
| SEC-11 | Anonymous single use voting token (RFC 9474 blind RSA) | `token`, `ballot`, `frontend/src/security/blindToken.ts` | `BlindSignerTest`, `TokenFlowApiTest` |
| SEC-12 | Client side ballot encryption with crypto agility | `crypto`, `frontend/src/security/ballotEnvelope.ts` | `crypto/*Test`, `ballotEnvelope.test.ts` |
| SEC-13 | Ballot secrecy by separation and metadata suppression | `secrecy` | `secrecy/*Test` |
| SEC-14 | Tamper evident ballot box (Merkle log) with tracking codes | `ballotbox`, `frontend/src/components/TrackingCodeChecker.tsx` | `ballotbox/*Test`, `ballotBox.test.ts` |
| SEC-15 | Threshold decryption (Shamir 3 of 5) after close of poll | `trustee` | `ThresholdDecryptionApiTest` |
| SEC-16 | Correct, publicly verifiable IRV and STV tally | `tally` | `TallyApiTest` |
| SEC-17 | Append only, hash chained, signed audit trail | `audit` | `audit/*Test` |
| SEC-18 | Signed results export with sequence numbers | `results`, `frontend/src/security/resultsVerifier.ts` | `ResultsApiTest`, `SampleResultsVerifierTest` |
| SEC-19 | Data protection in transit and at rest | `dataprotection` | `dataprotection/*Test` |
| SEC-20 | Secure CI/CD and dependency management | `.github/` | `pipeline/PipelineConfigTest` |

More detail on the identity work is in `docs/` (SEC-03, SEC-04 and SEC-09) and `keycloak/README.md`.

## Security rules every route follows

1. `SecurityConfig` lists each allowed method and path explicitly and ends with `.anyRequest().denyAll()`.
2. Every controller method must carry `@PreAuthorize`. `EndpointSecurity` denies any mapped method without one, so a forgotten annotation fails closed.
3. Authenticated calls need a DPoP bound access token. Plain Bearer tokens are refused.
4. A new endpoint therefore needs both a `@PreAuthorize` and a `SecurityConfig` entry.

## Running it locally

Requirements: Java 21, Node 20 or later, Docker.

1. Copy `.env.example` to `.env` and fill in the values. `.env` and `*.pem` files are git ignored. Never commit real secrets.
2. Create the election signing key (SEC-11):
   `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out election-signing.pem`
3. Start PostgreSQL: `docker compose up -d` (listens on 127.0.0.1:5432).
4. Start Keycloak and import `keycloak/evp-realm.json` and `keycloak/evp-staff-realm.json`, following `keycloak/README.md`.
5. Export the backend settings (Spring Boot does not read `.env` itself), for example:
   `EVP_DB_PASSWORD`, `EVP_LOOKUP_SECRET`, `EVP_TOKEN_KEY_PATH`, `EVP_ELECTION_DAY`, `EVP_AUDIT_KEY_PATH`, `EVP_AUDIT_PUBLIC_KEY_PATH`, `EVP_RESULTS_KEY_PATH`, `EVP_RESULTS_PUBLIC_KEY_PATH`, `EVP_DATA_KEYS`, `EVP_DATA_ACTIVE`, `EVP_INDEX_KEY`, plus `evp.ballot.keyring-dir` and `evp.ballotbox.key-dir` for the SEC-12 and SEC-14 keys.
6. Start the backend with the demo profile (port 8080):
   `cd backend && SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run`
   The demo profile loads four sample voters, one for each SEC-10 outcome. The enrolment check succeeds for Jane Citizen, 1990-01-01, 3000.
7. Start the frontend (port 5173, which forwards `/api` to 8080):
   `cd frontend && npm install && npm run dev`

The backend refuses to start if a required secret or key is missing (fail closed).

## Tests

```
cd backend  && ./mvnw test                     # JUnit and API tests on H2 in PostgreSQL mode
cd frontend && npm test && npm run lint && npm run build
```

The test profile (`backend/src/test/resources/application-test.properties`) uses throw away keys, so no secret is needed or committed to run the tests.

## CI/CD pipeline (SEC-20)

`.github/workflows/security.yml` runs on every push and pull request to `main`, plus a weekly re-scan: backend tests, frontend tests and build, CodeQL (Java and TypeScript), Semgrep, OSV dependency scan, CycloneDX SBOM and gitleaks over the full history. Dependabot raises update pull requests. Review major version updates by hand rather than merging them automatically.

## Known limitations

1. SEC-12, SEC-13 and SEC-14 are built and tested as components but not yet wired into ballot submission. Submitted ballots still go to the placeholder `StoredBallot` store, which SEC-15 decryption reads.
2. Keys are files or environment secrets in this prototype. Production would hold them in an HSM, with the election key split between trustees.
3. Hibernate creates the schema (`ddl-auto=update`). Production would use versioned migrations.
