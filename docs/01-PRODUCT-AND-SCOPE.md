# 01. Product and Scope

## 1. Vision

Build a believable, well-engineered miniature of an **issuer card-processing platform**, the kind of system that sits behind a bank's or fintech's prepaid card program. It should look and behave like a real engineering team built it: clear architecture, safe money handling, strong tests, a polished back-office UI, containerized, CI-verified.

The project exists to demonstrate **engineering judgment**, not feature count:

- money is never stored as floating point,
- a retried payment can never charge twice,
- concurrent payments can never overdraw a card,
- sensitive data is never stored or logged in clear text,
- every important action is auditable,
- every rule in this documentation is backed by an automated test.

## 2. Domain background (short)

A card payment flows like this in the real world:

```
Cardholder -> Merchant POS -> Acquirer -> Card network -> ISSUER PROCESSOR -> (approve / decline)
```

ATM Card Processor is the **issuer processor** box. An external system (simulated by an **API client** or by the dashboard's **Payment Simulator**) sends an authorization request. We decide in milliseconds, record the decision, and move money in our ledger only when approved.

Out of scope on purpose: card networks, settlement files, clearing, HSMs, 3-D Secure, PIN verification. These are listed under WON'T.

## 3. Personas

| Persona | Role in system | Goals |
|---------|----------------|-------|
| **Operations admin** | `ADMIN` | Issue and manage cards, top up balances, configure fraud rules, manage users and API keys, read the audit log |
| **Risk / finance analyst** | `ANALYST` | Read-only: monitor transactions, fraud rule hits, reports, export CSV |
| **Dispute officer** | `DISPUTE_OFFICER` | Work disputes from open to resolution; read cards and transactions |
| **Acquirer / POS system** | API client | Send authorization requests with an API key |

## 4. Scope

### 4.1 MUST (the project is not done without these)

| Area | Requirement |
|------|-------------|
| Identity | Login with email and password, JWT access token, role-based access control, bootstrap admin from environment, `GET /auth/me` |
| API clients | Create API client (key shown once), revoke, authenticate `POST /authorize` by `X-API-Key` |
| Cards | Issue (fake Luhn-valid PAN, encrypted at rest), list/search/filter, detail with masked PAN, activate, block, unblock, close, change limits, card-level MCC blocklist, top-up, ledger view |
| Authorization | Full decision algorithm in `05`, idempotency, row locking, immutable ledger, transaction list and detail, response code catalog |
| Fraud | Rule CRUD (no hard delete), three rule types, aggregate risk score, flag/decline decisions, rule hits stored per transaction |
| Disputes | Open, request evidence, submit evidence, resolve (won/lost), automatic deadline closure job, ledger credit on win, event history |
| Reports | Daily report, range summary with daily series, CSV export |
| Audit | Append-only audit events for all privileged actions |
| Frontend | Every screen listed in `08` |
| Quality | Unit, slice and integration tests per `11`, ArchUnit rules, coverage gates, formatting and lint gates |
| Delivery | Dockerfiles, Compose full stack, CI workflow, README, ADRs |

### 4.2 SHOULD (implement unless blocked; they are the difference between good and excellent)

- Refresh-token **rotation with reuse detection** (token families).
- **Login lockout** after repeated failures.
- Micrometer **custom metrics** and **structured JSON logs** in the `prod` profile.
- **OpenAPI snapshot test** and **TypeScript types generated** from the committed OpenAPI file.
- **Playwright end-to-end** tests against the Compose stack.
- `GET /meta/reference-data` for response codes and MCC labels.
- Query-count tests proving there are no N+1 queries on list endpoints.
- Accessibility checks (axe) in the e2e run.

### 4.3 COULD (only after every MUST and SHOULD is verified green)

- API rate limiting per client (Bucket4j).
- Dark mode in the dashboard.
- Mutation testing with Pitest on `domain` packages.
- k6 load-test script under `scripts/`.
- Dependabot configuration.
- Short-lived caching of fraud rules with explicit invalidation.

### 4.4 WON'T (explicitly out of scope; do not build)

Real ISO 8583 messaging, card network connections, HSM or real key management service, 3-D Secure, PIN and CVV verification, settlement and clearing, multi-tenancy, KYC/AML workflows, microservices split, message brokers (Kafka, RabbitMQ), Kubernetes manifests, multi-currency FX conversion, partial refunds and partial disputes, social login / OAuth provider, email sending.

## 5. Deliberate simplifications (must be documented in README "Limitations")

| Real world | This project |
|-----------|--------------|
| Authorization places a **hold**; settlement posts days later | Approval debits the balance **immediately** |
| Requests carry the PAN inside ISO 8583 messages | Requests reference the card by its **opaque UUID** (`cardId`) |
| CVV / PIN verification with an HSM | Not implemented; CVV is **never generated or stored** |
| Many currencies and exponents | Currencies **NPR and USD only**, both with 2 minor-unit digits |
| Disputes can be partial, multi-round with schemes | One **full-amount** dispute per transaction, single issuer-side workflow |
| Distributed scheduling with leader election | One in-process `@Scheduled` job; safe for one instance, notes for scale-out in ADR |
| Hardware-backed key custody | AES-256-GCM key supplied by environment variable |

## 6. Success criteria (the whole-project Definition of Done)

All of the following must be true and demonstrated by commands, not claims:

1. `./mvnw -f backend verify` is green: formatting, unit tests, integration tests (Testcontainers), ArchUnit, coverage gates.
2. `npm run lint`, `npm test`, `npm run build` are green in `frontend/`.
3. `docker compose -f deploy/docker-compose.yml up --build -d` starts Postgres, backend and frontend; `scripts/smoke-test.sh` passes against it.
4. Playwright e2e suite passes against the running stack.
5. Every MUST item above is implemented and covered by at least one automated test.
6. No `TODO`, `FIXME`, placeholder text, commented-out code, dead code, or unused dependencies remain.
7. `README.md`, `docs/adr/*`, `docs/CODE-TOUR.md`, `docs/INTERVIEW-QA.md`, `docs/FINAL_REPORT.md` exist and are accurate.
8. `scripts/verify-all.sh` exits 0 from a clean checkout.
9. Git history is clean: one commit per milestone minimum, Conventional Commit messages.

## 7. Non-functional requirements

| Quality | Target |
|---------|--------|
| Correctness | Money invariants proven by tests: balance never negative; ledger sum equals balance; idempotent replay never double-charges |
| Performance | Authorization p95 under 100 ms on a developer laptop at 50 requests per second with a warm JVM (sanity target, verified with a simple script or test; not a benchmark) |
| Security | OWASP ASVS-aligned basics in `06`; no secrets in git; no PAN/CVV in logs |
| Reliability | Fail fast on invalid configuration; health/readiness endpoints; graceful shutdown |
| Maintainability | Package-by-feature, enforced layering (ArchUnit), no class over about 300 lines, no method over about 40 lines unless justified |
| Observability | Request id on every response and log line; metrics for authorization outcomes and latency |
| Usability | Clear loading, empty and error states; keyboard-accessible forms; responsive layout |
| Portability | Runs with only JDK 21, Node LTS, Docker installed |

## 8. Assumptions

- The developer machine has **JDK 21**, **Node.js (active LTS)**, **Docker** with Compose v2, **git**, and internet access to Maven Central, npm registry, Docker Hub and start.spring.io.
- If any prerequisite is missing, the agent must stop at M0 and report exactly what to install (see `GOAL_PROMPT.md`, "Stop conditions").
- The default business zone is `Asia/Kathmandu`; default currency is `NPR`.
