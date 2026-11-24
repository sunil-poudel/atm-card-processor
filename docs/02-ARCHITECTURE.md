# 02. Architecture

## 1. Style

A **modular monolith**: one Spring Boot application, one PostgreSQL database, one Angular SPA. Modules are **packages by feature** with strictly enforced dependency direction. This is the right size for the problem and is easy to explain; a microservice split would add accidental complexity (WON'T).

```
                    HTTPS (nginx in Compose; Angular dev proxy locally)
  Browser (Angular SPA) -----------------------------------+
                                                           v
  POS / Acquirer simulator --X-API-Key--->  [ Spring Boot application :8080 ]
                                              |  web  ->  service  ->  domain
                                              |  repository (Spring Data JPA)
                                              v
                                       [ PostgreSQL 17 ]   (Flyway-managed schema)
```

## 2. Technology and version policy

Versions drift. The rule is: **use the newest stable release that is verified to work together; record deviations in an ADR.** Verify at M0, write the resolved versions into `docs/adr/0002-versions.md`.

| Component | Policy |
|-----------|--------|
| **Java** | 21 (LTS), Temurin distribution. Use records, sealed types where useful, pattern matching for `switch`, text blocks. `var` only when the type is obvious. |
| **Spring Boot** | Latest stable **4.1.x** (4.1.0 GA June 2026). If a mandatory library has no compatible release, use the latest **4.0.x** and write an ADR. **Never** Spring Boot 3.x (3.5 reached open-source end of life on 2026-06-30) and **never** milestones, release candidates or snapshots. |
| **Build** | Maven 3.9+ through the **Maven Wrapper** (`./mvnw`) committed to the repo |
| **Database** | PostgreSQL **17** (`postgres:17-alpine`) |
| **Migrations** | Flyway through the Spring Boot starter |
| **Persistence** | Spring Data JPA / Hibernate, `ddl-auto=validate`, `open-in-view=false` |
| **Security** | Spring Security with the OAuth2 Resource Server module and Nimbus JOSE for JWT (no third-party JWT library) |
| **API docs** | springdoc-openapi (the release line compatible with the chosen Boot version) |
| **Tests** | JUnit Jupiter and Mockito as managed by the Boot BOM, AssertJ, Testcontainers (PostgreSQL) via `@ServiceConnection`, Awaitility, ArchUnit, Spring Security Test |
| **Quality** | Spotless (google-java-format), JaCoCo, Maven Enforcer |
| **Frontend** | Latest stable **Angular** (22.x at the time of writing; verify with `npm view @angular/core version`), standalone components, signals, TypeScript strict mode, Angular Material + CDK, Chart.js |
| **Node** | The active LTS that satisfies the Angular CLI engine requirement |
| **Frontend tests** | The default test runner of the installed Angular version; Playwright for e2e |
| **Lint/format** | angular-eslint + Prettier |
| **Containers** | Docker multi-stage builds, non-root runtime users |
| **CI** | GitHub Actions |

**Important about Spring Boot 4:** it is built on Spring Framework 7, modularizes auto-configuration and starters, and (per the official notes) moves to Jackson 3. **Do not paste Boot 3-era snippets blindly.** Generate the project skeleton from **start.spring.io**, then read the official reference documentation and the Spring Boot 4.0 migration guide whenever a class, starter or property cannot be found. Prefer official docs over memory. Verify behaviour by running the build and tests.

### Bootstrapping the backend

Use Spring Initializr (`curl https://start.spring.io/starter.zip ...`) with: Maven, Java 21, the newest stable Boot version offered, group `dev.minicard`, artifact `card-processor`, package `dev.minicard.processor`, and dependencies for Web, Validation, Data JPA, PostgreSQL driver, Flyway (and its database module if the version requires it), Security, OAuth2 Resource Server, Actuator, Testcontainers (+ PostgreSQL module), Spring Security Test. Query `https://start.spring.io/metadata/client` (or `curl -H "Accept: application/json" https://start.spring.io`) to discover exact dependency ids. Add springdoc, ArchUnit and Awaitility manually.

## 3. Repository layout (monorepo)

```
.
├── GEMINI.md  AGENTS.md  GOAL_PROMPT.md  PROGRESS.md  README.md  BLOCKERS.md (only if needed)
├── docs/                         # this documentation set + adr/ + CODE-TOUR.md + INTERVIEW-QA.md + FINAL_REPORT.md + openapi.json + screenshots/
├── backend/                      # Maven project (wrapper committed)
│   ├── pom.xml  mvnw  .mvn/
│   └── src/{main,test}/{java,resources}
├── frontend/                     # Angular workspace
├── deploy/
│   ├── docker-compose.yml        # postgres + backend + frontend (nginx)
│   ├── docker-compose.dev.yml    # postgres only, for local development
│   ├── nginx.conf
│   └── .env.example
├── scripts/
│   ├── verify-all.sh  smoke-test.sh  export-openapi.sh  gen-secrets.sh  (load-test.js is COULD)
├── .github/workflows/ci.yml
├── .editorconfig  .gitignore  .gitattributes
└── LICENSE (MIT)
```

## 4. Backend package structure

Base package: `dev.minicard.processor`.

```
dev.minicard.processor
├── MiniCardProcessorApplication
├── common
│   ├── config        # @ConfigurationProperties records, Clock bean, Jackson/Web config
│   ├── error         # ErrorCode enum, ApiException hierarchy, GlobalExceptionHandler (ProblemDetail)
│   ├── web           # RequestIdFilter, PageResponse, shared request/response helpers
│   ├── money         # MoneyMinor helpers, Currency catalog
│   ├── crypto        # PanCipher (AES-GCM), HmacHasher
│   ├── persistence   # BaseEntity (id, createdAt, updatedAt, version), auditing config
│   └── time          # BusinessCalendar (business-zone day boundaries)
├── security
│   ├── config        # SecurityConfig, CORS, method security
│   ├── jwt           # JwtService (encoder/decoder wiring), token claims
│   ├── apikey        # ApiKeyAuthenticationFilter, ApiKeyService
│   └── user          # AppUser, UserRepository, UserService, AuthService, RefreshToken*, lockout
├── audit             # AuditEvent, AuditService, AuditController
├── card
│   ├── domain        # Card, CardStatus, CardBlockedMcc, Luhn, PanGenerator, CardLimits, CardNumber
│   ├── repository    # CardRepository
│   ├── service       # CardService, CardQueryService, TopUpService
│   └── web           # CardController, dto/*
├── ledger
│   ├── domain        # LedgerEntry, LedgerEntryType
│   ├── repository
│   └── service       # LedgerService (the ONLY place that moves balance)
├── transaction
│   ├── domain        # Transaction, TransactionStatus, ResponseCode, DeclineReason, AuthorizationDecision
│   ├── repository
│   ├── service       # AuthorizationService, TransactionQueryService, CsvExportService
│   └── web           # AuthorizationController, TransactionController, dto/*
├── fraud
│   ├── domain        # FraudRule, FraudRuleType, FraudAction, RuleEvaluator (+ 3 evaluators), RiskAssessment, RuleHit
│   ├── repository
│   ├── service       # FraudRuleService, RiskEngine
│   └── web
├── dispute
│   ├── domain        # Dispute, DisputeStatus (state machine), DisputeReason, DisputeEvent
│   ├── repository
│   ├── service       # DisputeService, DisputeDeadlineJob
│   └── web
├── report
│   ├── repository    # ReportQueryRepository (native aggregate queries)
│   ├── service       # ReportService
│   └── web
└── meta              # ReferenceDataController (response codes, MCC labels, enums)
```

### Layering rules (enforced by ArchUnit tests, see `11`)

1. `web` may depend on `service` and `domain` types it exposes through DTOs; **never** on `repository`.
2. `service` may depend on `domain`, `repository` and other features' **`service`** public APIs.
3. `domain` depends on **nothing** from Spring Web, security, or other features' repositories. Plain Java plus JPA annotations only.
4. `repository` depends on `domain` only.
5. Controllers **never** return or accept entities. DTOs are Java **records**.
6. Features do not reach into each other's `repository` packages.
7. `LedgerService` is the **only** class allowed to modify `Card.balanceMinor` (enforced by a dedicated ArchUnit/reflection test and code review).
8. No cycles between feature packages.
9. No classes named `*Util`, `*Helper`, `*Manager`. Name by responsibility.

## 5. Key design decisions (each gets an ADR in `docs/adr/`)

| ADR | Decision | Rationale (short) |
|-----|----------|-------------------|
| 0001 | PostgreSQL + Flyway from day one; no H2 | Same engine in dev/test/prod; real locking and timezone semantics |
| 0002 | Resolved versions (generated at M0) | Reproducibility |
| 0003 | Modular monolith, package-by-feature, ArchUnit-enforced | Right-sized, explainable |
| 0004 | Money as `long` minor units; currencies NPR and USD | No floating point; simple and exact |
| 0005 | Pessimistic row lock on the card during authorization | Simplest correct way to serialize debits for one card |
| 0006 | Idempotency via header + unique `(scope, key)` + request fingerprint | Safe retries; detects key reuse with different payload |
| 0007 | PAN encrypted with AES-256-GCM; blind index via HMAC-SHA-256; CVV never stored | PCI-inspired data minimization |
| 0008 | JWT access token (15 min) in memory + rotating refresh token in HttpOnly cookie | Limits XSS impact; revocable sessions |
| 0009 | API keys stored as SHA-256 hash with lookup prefix | Keys are high-entropy random, so a fast hash is appropriate |
| 0010 | Immutable ledger; balance is a cached projection updated only by `LedgerService` | Auditability |
| 0011 | Business-zone day boundaries computed in Java and SQL with explicit zone | Correct daily limits and reports for UTC+05:45 |
| 0012 | No Lombok; records for DTOs; explicit constructors/accessors on entities | Fewer hidden behaviours; easier to explain |
| 0013 | Angular signals + RxJS, no NgRx | Right-sized state management |
| 0014 | Single in-process scheduler for dispute deadlines | One instance; scale-out notes recorded |
| 0015 | RFC 9457 Problem Details with stable `code` field for all errors | Predictable client handling |

An ADR file is short: Title, Status, Context, Decision, Consequences (10 to 25 lines).

## 6. Cross-cutting concerns

### 6.1 Time
- Inject `java.time.Clock` everywhere; **never** call `Instant.now()` or `LocalDate.now()` directly in production code (ArchUnit rule). Tests use a fixed or adjustable clock.
- Persist instants as `TIMESTAMPTZ` (UTC). Convert to the business zone only for "day" semantics.

### 6.2 Configuration
- All configuration through **typed `@ConfigurationProperties` records** under prefix `app`, validated with Bean Validation (`@Validated`). Application fails fast on invalid or missing required values (for example short encryption keys).
- Profiles: `dev`, `prod`, `test`. No default secrets in `prod`. See `09` for the environment variable catalog.

### 6.3 Errors
- One `GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception to an RFC 9457 `ProblemDetail` with extra properties `code`, `requestId`, `timestamp`, and `errors[]` for validation. See `04` and `07`.

### 6.4 Correlation
- `RequestIdFilter` reads `X-Request-Id` (accepts only `[A-Za-z0-9-]{8,64}`) or generates a UUID, puts it in the MDC and the response header, and into every Problem Details body and audit event.

### 6.5 Transactions
- Service methods are the transaction boundary (`@Transactional`). Read-only services use `@Transactional(readOnly = true)`. Controllers are never transactional.
- The authorization path is a **single database transaction**; see `05`.

### 6.6 Auditing
- `AuditService.record(...)` is called by services (not controllers) for privileged actions; the audit insert participates in the same transaction as the business change so they commit or roll back together.

### 6.7 Concurrency
- Card row lock (`SELECT ... FOR UPDATE`) in authorization, top-up, dispute credit, and card status changes.
- `@Version` optimistic locking on mutable aggregates (`Card`, `FraudRule`, `Dispute`, `AppUser`) to catch lost updates in admin flows; `OptimisticLockingFailureException` maps to HTTP 409.

## 7. Frontend architecture (summary; detail in `08`)

Standalone Angular app, lazy-loaded feature routes, signals for UI state, RxJS for HTTP, functional interceptors and guards, Angular Material, typed API models generated from the OpenAPI file, same-origin API access (dev proxy / nginx), access token only in memory.

## 8. Deployment topology (Compose)

| Service | Image / build | Port (host) | Notes |
|---------|---------------|-------------|-------|
| `postgres` | `postgres:17-alpine` | 5432 (dev only) | volume, healthcheck (`pg_isready`) |
| `backend` | `backend/Dockerfile` (layered JAR, `eclipse-temurin:21-jre`, non-root) | 8080 | depends on healthy postgres; health at `/actuator/health/readiness` |
| `frontend` | `frontend/Dockerfile` (Node build stage, `nginxinc/nginx-unprivileged` runtime) | 8081 | serves SPA, proxies `/api` to `backend:8080`, SPA fallback to `index.html`, security headers |

## 9. What "professional" means here (the bar)

- A new developer can clone, run one command, and have the system running.
- Every behaviour in `05` has a test that fails if the behaviour breaks.
- Names explain intent; there is no magic; there are no surprises.
- The code could be handed to another team without a walkthrough, because `README.md`, ADRs and `docs/CODE-TOUR.md` explain where things are and why.
