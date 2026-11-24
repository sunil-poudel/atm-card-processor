# 10. Implementation Plan

The agent works **milestone by milestone, in order**. A milestone is finished only when its **exit gate** commands pass. After each milestone: update `PROGRESS.md`, make a Conventional Commit (more commits inside a milestone are encouraged: commit after each meaningful, green step), then start the next one. Do not start a milestone with a red build from the previous one.

General loop for every task: **read the relevant spec section -> write a failing test -> implement -> run the tests -> refactor -> run the formatter and the full gate -> commit.** Prefer vertical slices that work end to end over broad half-finished layers.

Path conventions: backend commands run from the repo root as `./backend/mvnw -f backend/pom.xml ...` or `cd backend && ./mvnw ...`; frontend commands in `frontend/`.

---

## M0. Preflight, repository and backend skeleton

**Tasks**
1. Check prerequisites and record versions (`java -version`, `mvn -v` optional, `node -v`, `npm -v`, `docker --version`, `docker compose version`, `git --version`, `curl`, `jq`). Missing JDK 21, Node, or Docker: apply the stop rule in `GOAL_PROMPT.md`. Check that Docker can actually run containers (`docker run --rm hello-world` or `docker info`).
2. `git init` (if not a repo), set `main` as default branch, create `.gitignore`, `.gitattributes`, `.editorconfig`, `LICENSE`, `PROGRESS.md` (if missing), initial commit containing the docs and agent files.
3. Generate the backend with Spring Initializr (see `02` §2). Resolve and record the exact Boot version and the Spring Initializr dependency ids you used. Put the project in `backend/`. Ensure the Maven Wrapper is committed.
4. Create `docs/adr/0001-postgres-flyway.md`, `0002-versions.md` (resolved versions of Boot, Java, Postgres image, springdoc, Testcontainers, ArchUnit, Angular, Node, Playwright; one-line reason per non-Boot dependency).
5. Configure the build: Spotless, JaCoCo (report now, gates later), Enforcer, Surefire/Failsafe split (`*Test` vs `*IT`), compiler lint flags, `spring-boot-maven-plugin` build-info, `-parameters`.
6. `deploy/docker-compose.dev.yml` with Postgres; `application.yml` (+ `dev`, `test` profiles) with typed `AppProperties` skeleton.
7. A smoke integration test: context loads against Testcontainers PostgreSQL via `@ServiceConnection` and Flyway runs an empty-but-valid `V1` placeholder (replaced in M1; you may start directly with the real V1 migration).
8. Remove the generated demo/sample code and unused dependencies.

**Exit gate**
- `./mvnw -f backend verify` is green (including the context-loads integration test).
- `git log` shows the initial commits; working tree clean.

---

## M1. Foundations and schema

**Tasks**
1. Flyway migrations `V1`..`V6` copied **exactly** from `03` (fix typos only if the SQL does not run; note any deviation in an ADR).
2. `common` package: `AppProperties` records with validation, `Clock` bean, `BusinessCalendar`, `UuidV7`-style id generator (or Hibernate time-based), `ErrorCode`, `ApiException` hierarchy, `GlobalExceptionHandler` (RFC 9457), `RequestIdFilter`, `PageResponse`, `PageRequestFactory`, base entity.
3. Tests: migration test (empty DB -> latest + `ddl-auto=validate` with entity classes created later; at this point verify with Flyway only), error handler tests through a tiny test controller (every mapped exception), request id filter test, business calendar tests (Kathmandu day boundaries, offset +05:45), config validation test (missing keys fail).
4. ADRs 0003..0004, 0011, 0012, 0015.

**Exit gate**: `./mvnw -f backend verify` green; migration test proves all tables/constraints exist.

---

## M2. Security and identity

**Tasks**
1. `AppUser`, `UserRepository`, `PasswordEncoder` setup, password policy validator, `UserService` (create, update, last-admin protection), `BootstrapAdminRunner`.
2. JWT issuing and validation (Nimbus through Spring Security), resource-server configuration with `iss`/`aud`/`exp` validation, role mapping.
3. `AuthService`: login (uniform errors, timing equalization, lockout), refresh rotation + reuse detection, logout, cookies (`06` §3.2), `GET /auth/me`.
4. API clients: create (key shown once), revoke, list; `ApiKeyAuthenticationFilter` for `POST /authorize` (the controller itself arrives in M4; for now register a protected test endpoint in the test source set).
5. `AuditService` and the audit entity/repository (writing events; the read endpoint comes in M7). Audit for login, lockout, token reuse, user and API client changes.
6. Security configuration: deny-by-default chain, custom entry point and access-denied handler (Problem Details), headers, CORS off by default.
7. Endpoints: `/auth/*`, `/users`, `/api-clients`. OpenAPI annotations on each.
8. Tests (see `11` §3.1-3.2): unit tests for password policy, lockout logic, token hashing, refresh rotation rules; `@WebMvcTest` slices for validation; integration tests for full login/refresh/logout flow with real cookies, reuse detection, parallel refresh, role matrix for the endpoints existing so far, API key tests.
9. ADRs 0008, 0009.

**Exit gate**: `verify` green; manually exercise with the smoke approach: start with Postgres, log in with the bootstrap admin via `curl`, show `GET /auth/me` returns the admin (save command output to `docs/evidence/m2-login.txt` is optional; keep it short).

---

## M3. Cards and ledger

**Tasks**
1. `crypto`: `PanCipher` (AES-256-GCM, AAD, key ids), `HmacHasher` (blind index), key loading and validation. `Luhn`, `PanGenerator` (BIN + 9 random digits + check digit, uniqueness retry on blind-index collision).
2. `Card` entity (state machine methods `activate`, `block`, `unblock`, `close`, limit changes), `CardRepository` (locked and unlocked finders, Specifications for filters), `CardBlockedMcc`.
3. `LedgerEntry`, `LedgerService` (only balance mutator; `recordTopUp`; the other methods stubbed with tests when their use cases arrive), ledger repository and queries.
4. `CardService`: issue (with initial balance), activate, block, unblock, close, change limits (with `@Version`), replace blocked MCCs, top-up; `CardQueryService` (list/detail/ledger). Audit events for each.
5. `CardController` and DTOs with the exact shapes in `04` §6; `ReferenceData` MCC catalog constant class (used again in M7).
6. Tests (`11` §3.3-3.5): Luhn vectors, generator properties (100k generated PANs valid, correct BIN, length 16), cipher round-trip/tamper/AAD/rotation, state machine table tests (every from/to pair), service integration tests, list filter tests with query-count assertion, ArchUnit rule that only `LedgerService` mutates the balance (introduce ArchUnit now with the first rules), a test that no response or log contains the PAN.
7. ADRs 0005 (locking), 0007, 0010.

**Exit gate**: `verify` green; issuing, activating and topping up a card works through HTTP in an integration test; balance always equals ledger sum (property-style test over random operations).

---

## M4. Authorization core

**Tasks**
1. `Transaction` entity + repository (migrations V3 already exist), `ResponseCode`, `DeclineReason`, `AuthorizationDecision` (sealed `Approved`/`Declined`).
2. `AuthorizationService` implementing `05` §2.1 **in the exact order**, including: validation, idempotency lookup and fingerprint, card row lock, checks (status, expiry, currency, MCC restriction, per-transaction limit, daily limit, balance), the fraud hook (an interface `RiskEngine` with a no-op default until M5, wired so M5 only adds an implementation), persisting transactions for approved and declined, ledger debit, idempotency race handling, metrics timing.
3. `AuthorizationController` (`POST /authorize`, API key or admin JWT), response headers (`Idempotent-Replayed`), `Location` not applicable.
4. `TransactionQueryService`, `TransactionController` list/detail (Specifications, projections, query-count test), CSV export comes in M7.
5. Tests (`11` §4): **all 20 worked examples in `05` §9** as a parameterized decision-matrix test; boundary tests (amount equals limit, equals balance, daily limit at local midnight with a controllable clock at 18:14:59Z/18:15:00Z, which is 23:59:59/00:00:00 in Kathmandu); idempotency (replay, reuse with different payload, concurrent duplicates); concurrency (N parallel debits); overflow; unknown card; validation errors; decline reasons; processing time stored; no balance in the response.
6. ADR 0006 (idempotency).

**Exit gate**: `verify` green; concurrency tests pass **20 consecutive runs** (run the class in a loop locally: `for i in $(seq 20); do ./mvnw -f backend -q -Dtest=... test || break; done` adapted to Failsafe) to detect flakiness.

---

## M5. Fraud engine

**Tasks**
1. `FraudRule` entity (+ hits), `FraudRuleRepository`, `FraudRuleService` (create/update/enable/disable with validation per type and `@Version`), audit.
2. `RuleEvaluator` strategy interface; `MaxAmountEvaluator`, `VelocityEvaluator` (counts prior attempts in the window including declined, via `ix_transactions_card_time`), `BlockedMccEvaluator`; `RiskEngine` implementing `05` §4.2 aggregation and thresholds; persistence of `transaction_rule_hits`.
3. Integrate into `AuthorizationService` at the position defined in `05` §2.1 (after the cheap checks, before the ledger debit); `flagged` and `riskScore` persisted.
4. Controller `/fraud-rules` and DTOs.
5. Tests (`11` §5): each evaluator at boundaries (4th/5th/6th attempt; window edges using the fake clock), aggregation and threshold table, rule disabled has no effect, rule change takes effect on the next request, decline vs flag outcomes, hits persisted with detail text, role access.

**Exit gate**: `verify` green; examples 12, 13, 14 in `05` §9 pass.

---

## M6. Disputes

**Tasks**
1. `Dispute`, `DisputeEvent`, state machine in `DisputeStatus`, repositories; `DisputeService` (open with eligibility, request evidence, submit evidence, resolve) including `LedgerService.recordDisputeCredit`.
2. `DisputeDeadlineJob` + per-item processor bean (REQUIRES_NEW), `FOR UPDATE SKIP LOCKED` query, scheduling configuration (cron property), `runOnce()`.
3. `DisputeController`, DTOs with `allowedActions`.
4. Tests (`11` §5.2): eligibility matrix (declined, too old, already disputed), every legal and illegal transition, win credits exactly once (even under concurrent resolve attempts), lose does not move money, deadline job with the fake clock (including idempotence and partial failure isolation), events appended in order, role access.
5. ADR 0014.

**Exit gate**: `verify` green; example 20 passes.

---

## M7. Reports, export, audit read API, reference data

**Tasks**
1. `ReportQueryRepository` native queries (timezone-correct) and `ReportService` per `05` §6 and `04` §10 (zero-filled series, 24 hourly entries, per-currency volumes, rate rounding).
2. `GET /reports/daily`, `/reports/summary`, validation (range <= 92 days).
3. `CsvExportService` + `GET /transactions/export.csv` (RFC 4180, formula guard, 50,000 cap, audit event).
4. `GET /audit-events` with filters; `GET /meta/reference-data`.
5. Tests (`11` §5.3): reports at day boundaries (23:59:59.999 and 00:00:00 local), multi-currency, empty ranges, rounding half-up, series completeness; CSV quoting/escaping/injection cases and exact header; audit filtering and paging; reference data contents.

**Exit gate**: `verify` green.

---

## M8. Backend hardening and quality gates

**Tasks**
1. OpenAPI: complete annotations, security schemes, error responses; `docs/openapi.json` committed with the snapshot test and the update mechanism (`-Dopenapi.update=true`); `scripts/export-openapi.sh`.
2. Observability: Micrometer metrics from `09` §7, structured JSON logs in `prod`, graceful shutdown, actuator exposure rules, build info.
3. Full ArchUnit suite (`11` §2): layering, naming bans, no field injection, no `Instant.now()`/`LocalDate.now()`, no `System.out`, controllers return DTOs, ledger exclusivity, no cycles.
4. JaCoCo gates enabled at the thresholds in `07` §11; add tests until they pass (meaningful ones, not assertion-free filler).
5. Query-count tests for every list endpoint; security matrix test that fails on unlisted mappings; startup-failure tests for missing prod secrets.
6. `DemoDataSeeder` (profile `demo`) using real services; idempotent (does nothing if cards exist).
7. Cleanup pass over the whole backend: dead code, unused dependencies (`./mvnw dependency:analyze` output reviewed), naming, Javadoc on domain/service, package-info where it helps, remaining ADRs.
8. Optional COULD items only if every MUST and SHOULD is green: Pitest on `*.domain`, rate limiting.

**Exit gate**: `./mvnw -f backend verify` green with coverage gates; `docs/openapi.json` up to date; grep proves no `TODO|FIXME|System.out|printStackTrace` in `src/main`.

---

## M9. Frontend foundation

**Tasks**
1. Scaffold Angular (`08` §1), Material, ESLint, Prettier, strict TS, proxy config, SCSS theme/tokens, `api:types` script generating `schema.d.ts` from `docs/openapi.json`.
2. `core`: config, `ApiClient`, error model, interceptors (request id, auth with single-flight refresh, error), `AuthStore`/`AuthService`, guards, session restore, notification service.
3. `shared/ui`: page header, empty/error/loading components, confirm dialog, status chip, masked PAN, copy button, KPI card, chart wrapper, `money` pipe and minor-unit parser, form error component and `applyServerErrors`.
4. Layout shell with role-filtered navigation, login page, 403/404 pages.
5. Unit/component tests for everything above.

**Exit gate**: in `frontend/`: `npm run lint && npm run format:check && npm test && npm run build` all green; manual check by running the backend and `npm start`: login works against the real API.

---

## M10. Frontend features

Implement in this order, each as a vertical slice with tests, committing after each: Cards (list, issue, detail tabs, actions), Transactions (list, filters in URL, detail, CSV export), Payment simulator, Fraud rules, Disputes (list, detail, actions via `allowedActions`), Reports + Dashboard (charts), Users, API clients, Audit log. Follow `08` §6-§8 for states, UX and design.

**Exit gate**: lint, format, tests, production build green; every screen reachable by role; the backend's demo data makes every screen show real content.

---

## M11. End-to-end tests and accessibility

**Tasks**: Playwright setup (`frontend/e2e`), global setup that logs in and stores state per role, tests listed in `11` §6 with axe checks, trace/screenshot on failure, screenshots script for the README. Stabilize: no fixed sleeps, use web-first assertions.

**Exit gate**: with the Compose stack up (see M12 for the files; if M12 is not done yet, run backend+frontend locally against dev Postgres), `npm run e2e` passes 3 times in a row.

> Order note: it is acceptable to do M12's Docker/Compose files **before** M11 so that e2e can run against the real stack. If you do, record the order in `PROGRESS.md`.

---

## M12. Containers, CI, scripts, documentation

**Tasks**
1. Backend and frontend Dockerfiles, `.dockerignore`s, `deploy/nginx.conf`, `deploy/docker-compose.yml`, `.env.example` (`09`).
2. `scripts/verify-all.sh`, `smoke-test.sh`, `export-openapi.sh`, (`gen-secrets.sh`).
3. `.github/workflows/ci.yml` (`09` §6); validate YAML locally.
4. `README.md` (`09` §9) with real screenshots from `docs/screenshots/`.
5. `docs/CODE-TOUR.md`, `docs/INTERVIEW-QA.md` (>= 25 Q&A), start `docs/FINAL_REPORT.md`.

**Exit gate**: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build -d` becomes healthy; `scripts/smoke-test.sh` passes; browser-equivalent checks via Playwright pass; `docker compose down -v` leaves nothing running.

---

## M13. Final verification and release polish

**Tasks**
1. Run `scripts/verify-all.sh` with **no flags** on a clean tree (`git status` clean, `git clean -fdx` on build outputs first). Fix everything that fails. Repeat until one full run is green.
2. Self-review against `07` §14 across the whole codebase; re-read each of docs 01 to 06 and tick every MUST in `01` §4.1 against code and tests (write the traceability table in `FINAL_REPORT.md`: requirement -> class -> test).
3. Run a secret scan and a TODO/placeholder scan; verify `.gitignore` coverage; ensure the repository tree contains no stray files.
4. Update `README.md` and `FINAL_REPORT.md` with real numbers (coverage, test counts, versions, durations).
5. Final commit and tag `v1.0.0`.
6. Update `PROGRESS.md` with `STATUS: COMPLETE` **only** after step 1 is green. Then stop.

**Exit gate**: the Definition of Done in `01` §6 is satisfied point by point, with evidence recorded in `FINAL_REPORT.md`.

---

## Time/effort guidance (for planning only)

M0-M1: small. M2-M4: the core, most care. M5-M7: moderate. M8: quality work. M9-M11: largest by volume. M12-M13: finishing. If something is blocked (for example a library lacks a compatible version), follow the blocker rules in `GOAL_PROMPT.md`: record, choose the safest alternative, continue.

## What to cut if the run is long (priority order of sacrifice)

Cut only COULD items first, then SHOULD items from the bottom of `01` §4.2 upward (axe checks last to go among frontend items). **Never cut**: authorization correctness, idempotency, ledger invariants, security basics, tests for MUST items, Docker Compose run, README.
