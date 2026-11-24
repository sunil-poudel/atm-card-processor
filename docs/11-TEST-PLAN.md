# 11. Test Plan

Tests are part of the deliverable, not an afterthought. This file enumerates the cases that MUST exist. Names below are descriptions; implement them as well-named JUnit/Vitest/Playwright tests (`07` §10). Add more where the code has more branches. Coverage gates are in `07` §11 and `08` §9.

## 1. Test support code (build first, in `src/test/java/.../support`)

| Support class | Purpose |
|---------------|---------|
| `PostgresContainerSupport` | Singleton Testcontainers PostgreSQL (`postgres:17-alpine`) started once per JVM, wired with `@ServiceConnection` |
| `MutableClock` (implements `Clock`) | Fixed start instant, `advance(Duration)`, `set(Instant)`; registered as the `Clock` bean in the `test` profile |
| `DatabaseCleaner` | Truncates all mutable tables (not Flyway history) between integration tests, restarts nothing else |
| `CardFixtures` | Builders: `activeCard()`, `cardWithBalance(...)`, `blockedCard()`, expired variants via the `MutableClock` or direct persistence |
| `UserFixtures` / `AuthTestSupport` | Create users per role, obtain JWTs through the real login endpoint or by signing with the test secret, create API clients and keys |
| `ApiAssertions` | Helpers that assert Problem Details shape (`code`, `status`, `requestId`) |
| `QueryCounter` | Counts executed SQL statements per call (Hibernate statistics or datasource proxy) |
| `LogCapture` | Captures log output for redaction assertions (Logback `ListAppender`) |

## 2. Architecture tests (ArchUnit, plain unit tests)

1. Feature `web` classes do not depend on `repository` classes.
2. `domain` classes do not depend on `org.springframework.web..`, `..security..`, or other features' `repository`.
3. No cycles between feature packages (slices).
4. Controllers are annotated with `@RestController` and live in `..web..`; their public methods do not return types annotated with `@Entity`.
5. No class annotated `@Entity` appears as a parameter or return type in `..web..`.
6. No field injection (`@Autowired` on fields); no `@Autowired` at all on constructors (single-constructor injection).
7. No call to `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `ZonedDateTime.now()`, `System.currentTimeMillis()` in main code, except inside the `Clock` bean configuration.
8. No `System.out`, `System.err`, `printStackTrace`.
9. Only `LedgerService` (and the `Card` entity method it calls) mutates `balanceMinor`: no other class calls the mutator; the mutator is package-private or guarded.
10. Class names do not end with `Util`, `Utils`, `Helper`, `Manager`.
11. `@Transactional` appears only on service classes/methods (and the dispute per-item processor).
12. Repositories are interfaces in `..repository..` packages only.
13. DTO types in `..web.dto..` are records.

## 3. Backend unit and slice tests

### 3.1 Identity and security
- Password policy: too short, too long (> 72 bytes), equals email, common password, valid passphrase, unicode.
- BCrypt: hashes differ for same password, verification works, strength is 12.
- JWT: valid token accepted; expired, wrong signature, wrong `iss`, wrong `aud`, missing `role`, `alg=none` all rejected with `401`.
- Login: success (tokens + cookie attributes: `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age`), unknown user vs wrong password return identical status/body/code, disabled user, locked user (correct password still rejected while locked), counter reset on success, lockout after exactly 5 failures, unlock after window with the `MutableClock`.
- Refresh: success rotates (old token now unusable); reuse of an old token revokes the family and the newest token also stops working; expired token; missing cookie; missing `X-Requested-With` -> 403; disabled user revokes family; parallel refresh with the same cookie: exactly one succeeds.
- Logout revokes family, clears cookie, idempotent without cookie.
- Users: create/update, email uniqueness (case-insensitive), last-admin protection (cannot disable/demote the last enabled admin or self), optimistic lock conflict, password never in responses.
- Bootstrap admin: created only when no users exist; skipped when users exist; fails startup on weak password; email logged but not password.
- API clients: key format and prefix, hash stored (raw key not in DB), shown once, revoke idempotent, authenticate on `/authorize`, rejected on other routes, rejected after revocation, rejected when tampered, `last_used_at` throttling.
- Endpoint x role matrix (parameterized over `{endpoint, method} x {anonymous, ADMIN, ANALYST, DISPUTE_OFFICER, API_CLIENT}` with expected 2xx/401/403/404) and a completeness check that every mapped route is in the matrix.
- Security headers present; error responses are Problem Details even for 401/403/404/405/415.
- CORS: no `Access-Control-Allow-Origin` by default; only configured origins when set.

### 3.2 Common/infrastructure
- Error handler: each exception type -> expected status, `code`, `requestId`, no stack trace/class name leakage; validation `errors[]` shape; malformed JSON; unknown property (`cvv`) -> `MALFORMED_REQUEST`; unsupported media type; method not allowed; unknown route -> `RESOURCE_NOT_FOUND`.
- Request id: generated when absent, echoed when valid, replaced when invalid pattern, present in MDC/log and Problem Details.
- Pagination: size cap 100, default 20, invalid sort property -> 400, negative page -> 400.
- Business calendar: Kathmandu offset +05:45; day start/end instants; month end; leap day; `localDate` of `18:14:59Z` vs `18:15:00Z`.
- Configuration: missing prod secret fails startup with a clear message; short keys fail; relaxed binding from environment works.
- Migration test: Flyway migrates an empty database; every table/constraint from `03` exists; Hibernate `validate` passes with all entities.

### 3.3 Cards, crypto, ledger
- `Luhn`: known valid and invalid numbers, all-zero, non-digits, length variants, check-digit calculation vectors.
- `PanGenerator`: 100,000 generated PANs are 16 digits, start with the BIN, pass Luhn; collision retry via the blind index is exercised with a stubbed generator.
- `PanCipher`: round trip; two encryptions of the same PAN differ (random IV); modified ciphertext fails; wrong AAD (different card id) fails; unknown key id fails clearly; key rotation (encrypt with k1, switch current to k2, still decrypt old rows, new rows use k2); key-size validation.
- `HmacHasher`: deterministic, different key -> different hash, constant-time compare used.
- Card state machine: for **every** (from, action) pair, assert legal or `INVALID_STATE_TRANSITION` (table-driven from `03` §6.1); unblock returns to `ACTIVE` when `activatedAt != null` else `INACTIVE`; close requires zero balance and is terminal.
- Issue: defaults applied, validation (holder name rules, limits relationships, validity years bounds, MCC format and de-duplication, currency), initial balance creates a ledger entry, PAN not in response unless configured, PAN encrypted in DB, audit has last4 only.
- Limits: update with stale version -> 409; daily < per-transaction -> 422.
- Blocked MCC replacement: normalization, max 50, stale version.
- Top-up: allowed on `ACTIVE`/`INACTIVE`, rejected on `BLOCKED`/`CLOSED`, amount bounds, overflow -> 422, ledger entry shape, concurrent top-ups both applied.
- Ledger invariants: after any sequence of random operations (property-style with a seeded RNG, 200 sequences) `sum(ledger.amount) == card.balance` and every `balanceAfter` equals the running sum; entries cannot be updated or deleted (a DB-level test attempts UPDATE/DELETE through JDBC and expects failure if triggers/rules exist, otherwise assert repository exposes no mutators).

### 3.4 Card HTTP behaviour (slice + integration)
- Validation errors for each field; role access; `201` + `Location`; list filters (`status`, `currency`, `q` by name and by last4, `expired`), sorting whitelist, paging, **query-count test** (statements constant for page sizes 1, 20, 100).
- The response never contains `pan`/`panEncrypted`/`panHash` fields except the one-time issue response under configuration.

### 3.5 Log and data leakage
- With `LogCapture`, run issue, authorize, and error scenarios; assert no log line contains the PAN, the JWT, the refresh cookie, the API key, the password or the encryption keys.
- Audit event `details` never contain a PAN.
- CSV and error bodies never contain a PAN.

## 4. Authorization tests

### 4.1 Decision matrix (parameterized, one row per case)
All 20 worked examples in `05` §9, plus:
- Decline precedence: card both expired and blocked -> `CARD_NOT_ACTIVE` (62, step 1 first); expired + wrong currency -> 54; currency mismatch + MCC restricted -> 12; MCC restricted + over limit -> 57; over per-transaction limit + insufficient funds -> 61; daily-limit breach + insufficient funds -> 61; insufficient funds + fraud condition -> 51 (fraud not evaluated).
- Boundaries: amount equals per-transaction limit (approve), limit+1 (decline); spentToday + amount equals daily limit (approve), +1 (decline); amount equals balance (approve, balance 0), balance+1 (decline 51); amount 1 and max `10_000_000_000`.
- Expiry: card valid through the last instant of expiry month in Kathmandu (`MutableClock` at `18:14:59.999Z` on the last day of month vs `18:15:00Z`).
- Daily limit day boundary: purchases at 23:59:59 local count for that day; at 00:00:00 local the counter resets (instants `18:14:59Z` and `18:15:00Z`); dispute credits do not reduce `spentToday`; declined attempts do not count.
- Persisted transaction: all fields (status, response code, decline reason, risk score, flagged, amount, merchant trimmed, mcc, reference, source, scope/key, fingerprint, `processingMillis >= 0`, `balanceAfter` only on approval).
- Declines persist a transaction row and **no** ledger entry; approvals persist exactly one `PURCHASE` entry with `balanceAfter` correct.
- Response shape: only the five documented fields; no balance, no decline reason, no risk data.
- Caller types: API key works; staff ADMIN JWT works; ANALYST/DISPUTE_OFFICER -> 403; no credentials -> 401; revoked key -> 401.
- Request validation: missing/blank fields, bad UUID, amount 0/negative/too large/string, 3-letter currency not supported -> 422 `UNSUPPORTED_CURRENCY`, lowercase currency, MCC with letters/3 digits/5 digits, merchant name too long or with control chars, reference with illegal chars, missing `Idempotency-Key`, malformed key.
- Unknown card -> 404 and **no** transaction row.
- Overflow guard: balance near `Long.MAX_VALUE` with a large amount cannot wrap (use direct DB setup).

### 4.2 Idempotency
- Replay with same payload returns the identical body and `Idempotent-Replayed: true`; no new rows; balance unchanged.
- Replay of a **declined** request also replays the decline (does not re-evaluate even if the card changed meanwhile).
- Same key + changed amount / merchant / mcc / reference / currency / card -> 422 `IDEMPOTENCY_KEY_REUSED`.
- Same key from a different caller (client A vs client B, or client vs user) is independent.
- Fingerprint ambiguity test: `("ab","c")` vs `("a","bc")` produce different fingerprints.
- A request that failed validation does not consume the key; the corrected request with the same key succeeds.
- **Concurrent duplicates**: 10 parallel identical requests -> exactly one transaction row, one ledger entry, one debit; all responses succeed with identical bodies or `409 IDEMPOTENCY_CONFLICT` that succeeds on retry.

### 4.3 Concurrency
- 50 parallel approvals of equal amount against a balance that covers exactly 20: exactly 20 `00` and 30 `51`; final balance exactly as computed; never negative; ledger sum equals balance.
- Parallel authorize + top-up + block on one card: invariants hold, no deadlock, results consistent with some serial order.
- Lock timeout path: hold the row lock in another transaction beyond the timeout -> `503 TEMPORARILY_UNAVAILABLE` with `Retry-After`.
- Run the concurrency classes repeatedly (20 times) during M4 to check for flakiness.

### 4.4 Transactions read API
- List filters each individually and in combination (`cardId`, `status`, `responseCode`, `declineReason`, `mcc`, `flagged`, amount range, date range with inclusive-from/exclusive-to, `q`), sorting, paging, query-count constant, role access (API_CLIENT forbidden), detail with rule hits and dispute link, 404 for unknown id.

## 5. Fraud, disputes, reports, audit, CSV

### 5.1 Fraud
- Evaluators unit-tested without Spring: `MAX_AMOUNT` (equal -> no hit, +1 -> hit), `VELOCITY` (4 prior no hit, 5 prior hit, window edge exactly at `now - window`, declined attempts counted, other cards not counted), `BLOCKED_MCC` (listed / not listed / normalization).
- `RiskEngine`: no hits -> score 0 ALLOW; single FLAG rule below threshold -> FLAG by action; score cap at 100; sum >= 80 -> DECLINE even if actions are FLAG; sum 50..79 -> FLAG; any DECLINE-action hit -> DECLINE; disabled rule ignored; evaluation order by name; deterministic hit detail text.
- Integration: default rules from V6 present; example 12/13/14; rule edited via API affects the very next authorization; hits persisted with snapshots; fraud step skipped when an earlier decline applies (no hits stored).
- Rule admin API: create validation per type (missing threshold, bad window, bad MCC list, score 0/101, duplicate name case-insensitively), `type` immutable, stale version -> 409, no DELETE route (405/404), ANALYST read-only, audit events with before/after summary.

### 5.2 Disputes
- Eligibility: approved within window ok; declined -> `DISPUTE_NOT_ELIGIBLE`; older than `max-age-days` -> not eligible; second dispute on same transaction -> `DISPUTE_ALREADY_EXISTS` (including a race with two parallel opens: exactly one succeeds).
- Transitions table-driven (every state x every action); events appended in order with actor/note/from/to; `allowedActions` per state and role-agnostic.
- Evidence deadline set from configuration; submitting after deadline is rejected (`INVALID_STATE_TRANSITION`) or already auto-closed.
- Resolve WON: exactly one `DISPUTE_CREDIT` entry for the transaction amount, balance increased, `balanceAfter` correct; two concurrent resolve attempts -> one succeeds, one `409`, still exactly one credit (also enforced by the unique index); card in `BLOCKED` status still receives the credit; on a `CLOSED` card resolve WON fails with `409 INVALID_STATE_TRANSITION` and the dispute stays `UNDER_REVIEW` (`05` §5.2).
- Resolve LOST: no money movement.
- Deadline job (`MutableClock`): overdue `EVIDENCE_REQUESTED` -> `RESOLVED_LOST` with a system event `DISPUTE_AUTO_CLOSED` audit; not overdue untouched; other states untouched; idempotent second run; batch of 250 processed in batches of 100; a failing item does not block others (inject a failing processor stub); `FOR UPDATE SKIP LOCKED` used (integration test with two concurrent runs does not double-process).
- Role access: ANALYST read-only, DISPUTE_OFFICER and ADMIN can act.

### 5.3 Reports, CSV, audit, meta
- Daily report on seeded data: counts, approval rate rounding (e.g., 2/3 -> 66.67, 1/8 -> 12.50, 0 attempts -> 0.00), per-currency volumes, declines by code ordering, top MCCs (ties by mcc), hourly 24 entries in business zone with data at 18:14:59Z and 18:15:00Z falling on different days.
- Summary: series zero-filled and contiguous across month end; range > 92 days -> 400; `from > to` -> 400; `openDisputes` and `activeCards` snapshot semantics.
- CSV: exact header; amount formatting `500.00`; RFC 4180 quoting for commas/quotes/newlines in merchant names; formula injection (`=`, `+`, `-`, `@`, tab, CR prefixes) neutralized; filters applied identically to the list endpoint; 50,000 cap -> 422 (use a configurable lowered cap in the test profile); `Content-Disposition` filename pattern; audit event written; DISPUTE_OFFICER forbidden.
- Audit read API: filters, paging, order, ADMIN only; every privileged action in `05` §7 produces exactly one event with the correct action string (one parameterized test per action).
- Reference data: contains all response codes, decline reasons, 20 MCCs, currencies, dispute reasons.

### 5.4 Contract and operations
- OpenAPI snapshot test (generated equals committed `docs/openapi.json`; includes security schemes; every operation has a summary and at least one error response).
- Health: liveness/readiness anonymous and detail-free; other actuator endpoints protected.
- Graceful shutdown config present; `prod` profile requires secrets (startup failure test); `prod` disables Swagger.
- Metrics: counters/timers increment on approve and decline; no PAN or card id tags.
- Demo data seeder: creates consistent data (invariants hold) and is a no-op on second run.

## 6. Frontend tests

### 6.1 Unit/component (Vitest or the installed default)
- `money` pipe and parser: `0`, `1`, `100`, `12345`, negative, large, USD vs NPR formatting; parsing `"500"`, `"500.5"`, `"500.50"`, `"500.505"` (rejected), `""`, `"abc"`, `"-1"`, `"1e3"`, max boundary.
- Auth store and interceptor: attaches bearer to API calls, not to auth calls; `401` triggers exactly one refresh for N parallel requests and replays them; refresh failure logs out and redirects with `returnUrl`; no token persisted to storage (assert `localStorage`/`sessionStorage` untouched).
- Guards: unauthenticated redirect; role mismatch -> 403 page.
- Error interceptor: Problem Details mapped to `ApiError`; non-JSON error; network error.
- `applyServerErrors` maps fields; unknown fields go to a form-level error.
- Components: login (validation, error, loading), card issue form (limits relation, MCC select, one-time PAN reveal), filters sync to URL query params and restore from them, dispute action panel shows exactly `allowedActions`, empty/error/loading states, confirm dialog, status chips (text present, not color only), CSV export button handles `EXPORT_TOO_LARGE`.

### 6.2 End-to-end (Playwright against the Compose stack, `demo` data off, tests create their own data)
1. Login failure message; login success lands on the role's default page; logout.
2. Session restore: reload the page, stay logged in (refresh cookie); expire the access token (short test token TTL or manipulate) and confirm transparent refresh.
3. ADMIN issues a card (with initial balance), sees one-time PAN reveal, activates, tops up, sees ledger entries.
4. Simulator: approved payment (balance decreases), over-limit decline (61), insufficient funds (51), restricted MCC decline (57 via card-level MCC), currency mismatch (12); replay with same key shows replay indicator and one debit.
5. Fraud: edit a rule threshold in the UI, trigger a flagged payment, see the flag and rule hit in the transaction detail; "Rapid-fire x6" yields the fraud decline on the 6th.
6. Dispute lifecycle: open from an approved transaction, request evidence, submit, resolve WON, ledger shows `DISPUTE_CREDIT`, balance restored; dispute actions respect roles (login as DISPUTE_OFFICER created by ADMIN through the UI).
7. Reports: dashboard KPIs reflect the transactions created in the test (use a unique card and assert relative changes, or isolate by date range and fresh database per run).
8. Role restrictions: ANALYST cannot see Users/API clients/Audit nav items and gets the 403 page on direct URL; ANALYST can export CSV (download event, header row asserted).
9. Users and API clients: create user; create API client and see key once; call `/authorize` with the key through Playwright's request context; revoke and see 401.
10. Audit log shows the actions performed above.
11. Accessibility: axe scan (zero serious/critical) on login, dashboard, cards list, card detail, simulator result, dispute detail.
12. Responsive smoke at 1280 and 768 widths (no horizontal page scroll).
Stability rules: web-first assertions, no sleeps, unique data per test, traces on first retry only, retries 1 in CI.

## 7. Non-functional checks (scripts, not necessarily gating)

- **Performance sanity** (`scripts/load-test.js` or a Java test with a thread pool): 2,000 authorizations over 40 seconds across 20 cards, report p50/p95/p99; record in `FINAL_REPORT.md`; investigate if p95 > 100 ms.
- **Startup time** and image sizes recorded.
- **Dependency audit** (`npm audit`, optional OWASP dependency-check): recorded.

## 8. Coverage and quality targets (recap)

| Scope | Target |
|-------|--------|
| Backend overall | >= 85% line, >= 75% branch (enforced) |
| `*.domain`, authorization, ledger, fraud, dispute services | >= 95% line, >= 90% branch (enforced) |
| Frontend `core` and `shared` | >= 80% lines |
| Frontend features | >= 70% lines |
| Mutation score (COULD) on `*.domain` | >= 70% |
| Flaky tests | zero tolerated; concurrency suites pass 20 consecutive runs |
