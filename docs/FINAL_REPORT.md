# ATM Card Processor: Final Verification Report & Project Delivery

**Project**: ATM Card Processor — Production-Style Prepaid Card Issuer-Processing Engine & Back-Office  
**Date**: October 21, 2026  
**Status**: 100% COMPLETE & VERIFIED GREEN  
**Target Environment**: Linux, OpenJDK 21, Spring Boot 4.1.1, PostgreSQL 16/17, Angular 19.2+, Playwright 1.64  

---

## Executive Summary

ATM Card Processor has been built, hardened, and verified autonomously milestone by milestone (M0 through M13) strictly according to the architectural blueprints in `docs/` and the directives in `GOAL_PROMPT.md`.

The system represents a complete, production-grade prepaid card issuer-processing engine and modern back-office management console. All non-negotiable financial, cryptographic, concurrency, and architectural invariants have been proven through comprehensive multi-layer automated tests.

---

## 1. What Was Built

### Backend (Spring Boot 4.1.1 / Java 21)
- **Modular Monolith Core**: Package-by-feature domain architecture strictly enforced by 13 compile-time ArchUnit rules.
- **7-Step Authorization Engine**: Full implementation of the standard card authorization sequence (card active, card unblocked, expiry check, per-transaction and daily limits, balance check, velocity & fraud evaluation, append-only ledger mutation).
- **Append-Only Immutable Ledger**: Absolute double-entry ledger where card balance is a derived and cached quantity; only `LedgerService` alters balances under pessimistic row locking (`SELECT ... FOR UPDATE`), eliminating double-spending under concurrent threads.
- **SHA-256 Fingerprint Idempotency**: Idempotent request handling with `(scope, idempotency_key)` unique constraints and cached response replays marked with `Idempotent-Replayed: true`.
- **Cardholder Data Security**: AES-256-GCM authenticated encryption with `cardId` as Additional Authenticated Data (AAD) and HMAC-SHA256 blind indexing for fast lookup. Zero plaintext PAN or CVV storage.
- **Fraud Risk Engine**: Strategy pattern evaluators (`MAX_AMOUNT`, `VELOCITY` with sliding time window, `BLOCKED_MCC`) evaluating cumulative risk scores (0–100) and automated flags/declines.
- **Dispute Lifecycle State Machine**: Full issuer dispute lifecycle with atomic ledger crediting upon win and concurrent background sweeper using `FOR UPDATE SKIP LOCKED`.
- **Identity & RBAC**: JWT access tokens in memory, rotating refresh tokens in `HttpOnly` cookies with family reuse revocation, BCrypt password hashing with 12-char policy, and SHA-256 hashed API client keys (`mck_` format).
- **Asia/Kathmandu Business Calendar**: Timezone-correct daily operational reports and multi-day summary analytics with zero-fill date formatting.
- **RFC 4180 CSV Export**: Streaming CSV export with formula injection defense and 50k row limits.
- **Production Secrets Validation**: Strict startup validation in `application-prod.yml` preventing startup with placeholder or weak secrets.

### Frontend (Angular 19.2+ / Angular Material)
- **Modern Angular Architecture**: Standalone components only, Angular Signals for local/UI reactivity, OnPush change detection, strict TypeScript.
- **Generated API Contracts**: Strictly typed client interfaces generated from backend OpenAPI contracts via `openapi-typescript`.
- **Core HTTP Infrastructure**: Single-flight silent refresh queuing (`AuthInterceptor`), request tracking (`RequestIdFilter` & interceptor), and RFC 9457 Problem Details error translation (`ErrorInterceptor`).
- **7 Complete Vertical Slices**:
  1. *Cards*: Search/filter, issue modal with one-time PAN reveal, activation, top-up, limits editing, blocked MCCs, ledger tab, transaction history tab.
  2. *Transactions*: Multi-filter search, CSV export, detail drawer with fraud rule hits and dispute status.
  3. *Payment Simulator*: Presets (Grocery 500, Betting 100), custom authorizations, idempotency replay demonstration, Rapid-Fire x6 velocity test.
  4. *Fraud Rules*: Rule catalog, instant toggle, rule editor dialog, score threshold visual guide.
  5. *Disputes*: Filterable dispute list, detail view with facts and linked transaction, dynamic action panel strictly rendering backend `allowedActions`, resolution dialogs.
  6. *Reports & Dashboard*: KPI cards, daily business zone reports, summary range charts with Chart.js.
  7. *Administration*: Staff user management with password policy, API client registration with one-time secret key reveal, and searchable audit log.

### Deployment, CI & Operations
- **Containerization**: Multi-stage, layered, non-root `backend/Dockerfile` and `frontend/Dockerfile` with unprivileged NGINX reverse proxy.
- **Compose Stack**: `deploy/docker-compose.yml` orchestrating PostgreSQL 17, Spring Boot backend, and NGINX frontend with healthcheck dependencies.
- **GitHub Actions Workflow**: `.github/workflows/ci.yml` validating backend build, frontend quality gates, Docker builds, and security audits.
- **Operational Scripts**: `scripts/gen-secrets.sh`, `scripts/smoke-test.sh`, and `scripts/verify-all.sh`.
- **Documentation**: `docs/CODE-TOUR.md`, `docs/INTERVIEW-QA.md` (33 Q&A), `README.md`, and 15 ADRs.

---

## 2. Test Verification & Evidence Summary

| Test Layer | Framework / Tool | Verified Result | Exit Gate Status |
|------------|------------------|-----------------|:----------------:|
| Backend Unit & Integration Tests | JUnit 5, Mockito, Testcontainers | **254 passed, 0 failed** (184 unit, 70 IT) | GREEN |
| Architecture Guardrails | ArchUnit | **13 of 13 rules passed** | GREEN |
| Backend Coverage | JaCoCo | **>= 85% line, >= 75% branch** (enforced) | GREEN |
| Code Formatting | Spotless (google-java-format) | **0 formatting violations** | GREEN |
| Banned Words & Hygiene | Maven Enforcer | **0 banned tokens (TODO/FIXME/dead code)** | GREEN |
| Frontend Unit & Component Tests | Jasmine / Karma | **167 passed, 0 failed** (55 test specs) | GREEN |
| Frontend Linting & Style | ESLint & Prettier | **0 lint errors, 0 format warnings** | GREEN |
| Frontend Production Build | Angular CLI (`ng build`) | **0 compiler warnings, bundles optimized** | GREEN |
| End-to-End User Journeys | Playwright (Chromium) | **27 passed, 0 failed** (3 consecutive runs) | GREEN |
| Web Accessibility | @axe-core/playwright (WCAG 2.1 AA) | **0 critical, 0 serious violations** | GREEN |
| Responsive Layouts | Playwright Viewport Matrix | **No horizontal scroll at 1280px & 768px** | GREEN |
| Live Smoke Verification | Bash / cURL (`smoke-test.sh`) | **8 of 8 smoke checks passed** | GREEN |

**Total Automated Tests**: **448 automated tests** passing green across the repository.

---

## 3. Concurrency & Money Safety Verification Evidence

1. **Balance Race Condition Test** (`AuthorizationConcurrencyIT`):
   - 50 virtual threads fired simultaneously against a card with an initial balance of NPR 10,000 requesting NPR 500 each.
   - *Result*: Exactly 20 authorizations approved; exactly 30 declined with `INSUFFICIENT_FUNDS` (decline code 51). Final card balance was verified to be exactly NPR 0.00; exactly 20 debit entries existed in the ledger. Zero over-spend occurred.
2. **Idempotency Replay Race Test** (`AuthorizationConcurrencyIT`):
   - 10 threads fired the exact same request payload with identical `Idempotency-Key` headers simultaneously.
   - *Result*: Exactly 1 transaction recorded in the database; 9 requests returned the cached replay response with `Idempotent-Replayed: true`; exactly 1 ledger debit was written.
3. **Dispute Deadline Concurrent Sweeper** (`DisputeIT`):
   - PostgreSQL `FOR UPDATE SKIP LOCKED` verified to allow multiple concurrent worker sweeps without lock contention or duplicate settlement credits.

---

## 4. Known Simplifications & Operational Limitations

As specified in `docs/01-PRODUCT-AND-SCOPE.md` §5, the following intentional simplifications are in place:
1. **Single-Message Authorization vs Dual-Message Settlement**: Payments debit balances immediately upon approval rather than placing a temporary hold followed by batch settlement clearing days later.
2. **Opaque Card UUID References**: REST endpoints accept `cardId` UUIDs rather than raw binary ISO 8583 PAN fields.
3. **Currencies Restricted**: Supported currencies are strictly `NPR` and `USD` (both 2 decimal digits).
4. **Disputes**: Full-amount disputes only; partial refunds or multi-round scheme arbitrations are not modeled.
5. **Key Management**: Cryptographic keys are injected via environment variables rather than a physical Hardware Security Module (HSM).

---

## 5. Manual Verification Guide for System Owner

To perform a manual end-to-end verification of the system:
1. **Launch Stack**:
   ```bash
   cp deploy/.env.example deploy/.env
   docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build -d
   ```
2. **Access UI**:
   - Open browser at `http://localhost:8081`
   - Log in using `admin@minicard.local` / password from `deploy/.env`.
3. **Issue a Card**:
   - Go to **Cards** -> **Issue Card**.
   - Enter cardholder `Sunil Poudel`, validity `3 Years`, per-tx limit `5000.00`, daily limit `20000.00`, initial balance `1000.00`.
   - Copy the one-time revealed card number and acknowledge.
   - On the card details page, click **Activate**.
4. **Simulate POS Payment**:
   - Go to **Payment Simulator**.
   - Select the newly activated card.
   - Click **Grocery 500 (MCC 5411)** preset and click **Send Authorization**.
   - Verify instant green `APPROVED (Code 00)` response.
   - Click **Send Authorization** again without changing the key -> verify yellow `Idempotent Replay Detected` banner.
5. **Verify Audit & Reports**:
   - Go to **Reports** -> observe daily volume and approval rate update in real time.
   - Go to **Administration** -> **Audit Log** -> verify every action (card issue, activate, authorize) is recorded with actor and timestamp.
