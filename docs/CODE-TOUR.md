# Code Tour: ATM Card Processor

Welcome to the **ATM Card Processor** codebase. This guide walks you through the core architectural pathways of the application, showing you where key logic lives and how data flows through the system.

---

## 1. Where to Start

The application is structured as an ArchUnit-enforced modular monolith with strict domain boundaries and a decoupled Angular frontend:

- **Backend**: `backend/src/main/java/dev/minicard/processor/`
  - `identity/`: Staff authentication, API client credentials, RBAC.
  - `cards/`: Prepaid card aggregates, lifecycle state machine, PAN generation.
  - `crypto/`: AES-256-GCM encryption with AAD, HMAC-SHA256 blind indexing.
  - `ledger/`: Double-entry append-only immutable ledger; balance mutations.
  - `transactions/`: Real-time transaction authorization engine, idempotency, CSV export.
  - `fraud/`: Strategy-pattern fraud rules engine (amount, velocity, MCCs).
  - `disputes/`: Chargeback and dispute state machine, ledger credits, deadline sweeper.
  - `reports/`: Timezone-aware daily aggregations and date-range operational summaries.
  - `audit/`: Asynchronous/independent audit event logging (`REQUIRES_NEW`).
- **Frontend**: `frontend/src/app/`
  - `core/`: API client, reactive AuthStore, HTTP interceptors, session restore.
  - `features/`: Vertical feature slices (cards, transactions, simulator, fraud rules, disputes, reports, dashboard, users, api-clients, audit).
  - `shared/`: Reusable UI components, pipes, validators.

---

## 2. Core Pathways

### Pathway A: Real-Time Transaction Authorization

The heartbeat of ATM Card Processor is the payment authorization flow.

1. **HTTP Ingestion**:
   - [`AuthorizationController.java`](file:///backend/src/main/java/dev/minicard/processor/transactions/web/AuthorizationController.java) receives `POST /api/v1/authorize` with an `Idempotency-Key` header and an [`AuthorizeRequest`](file:///backend/src/main/java/dev/minicard/processor/transactions/web/dto/AuthorizeRequest.java).
2. **Idempotency Guard**:
   - [`IdempotencyKeyService.java`](file:///backend/src/main/java/dev/minicard/processor/transactions/service/IdempotencyKeyService.java) calculates the SHA-256 payload fingerprint using [`RequestFingerprintCalculator.java`](file:///backend/src/main/java/dev/minicard/processor/transactions/service/RequestFingerprintCalculator.java).
   - If the key exists with the matching fingerprint, it returns the cached response with `Idempotent-Replayed: true`. If the fingerprint differs, it throws `409 IDEMPOTENCY_CONFLICT`.
3. **Pessimistic Card Locking & Lifecycle Check**:
   - [`AuthorizationService.java`](file:///backend/src/main/java/dev/minicard/processor/transactions/service/AuthorizationService.java) executes the 7-step sequence defined in `docs/05-BUSINESS-RULES.md` §2.
   - It acquires a pessimistic write lock on the card (`PESSIMISTIC_WRITE`) to serialize concurrent debits on the same card without race conditions.
   - Checks expiry in `Asia/Kathmandu` and status (`ACTIVE`).
4. **Limits & Velocity Validation**:
   - Compares the transaction amount against `perTransactionLimitMinor` and daily spend accumulator against `dailyLimitMinor`.
5. **Fraud Engine Evaluation**:
   - [`FraudRiskEngine.java`](file:///backend/src/main/java/dev/minicard/processor/fraud/service/FraudRiskEngine.java) evaluates active fraud rules (`MAX_AMOUNT`, `VELOCITY`, `BLOCKED_MCC`).
   - Rule scores accumulate: $\ge 50$ flags the transaction for review; $\ge 80$ or explicit `DECLINE` action declines the transaction (`responseCode=59`).
6. **Balance Check & Ledger Mutation**:
   - If approved, checks available balance.
   - Delegates exclusively to [`LedgerService.java`](file:///backend/src/main/java/dev/minicard/processor/ledger/service/LedgerService.java), which appends an immutable `AUTHORIZATION_DEBIT` ledger entry and updates the card balance atomically.
7. **Transaction Record & Metrics**:
   - Persists [`TransactionRecord.java`](file:///backend/src/main/java/dev/minicard/processor/transactions/domain/TransactionRecord.java) and records rule hits in `TransactionRuleHit`.
   - Increments Micrometer metrics tagged with status and response code.

---

### Pathway B: Append-Only Immutable Ledger

The ledger is the single source of truth for financial balances.

1. **Exclusivity Enforcement**:
   - ArchUnit rule enforces that ONLY `LedgerService` may modify card balances. No entity method or other service can alter balances.
2. **Operations**:
   - [`LedgerService.java`](file:///backend/src/main/java/dev/minicard/processor/ledger/service/LedgerService.java) supports:
     - `INITIAL_LOAD`: Funding during card issuance.
     - `TOP_UP`: Operator manual top-ups.
     - `AUTHORIZATION_DEBIT`: Successful POS authorization.
     - `DISPUTE_CREDIT`: Full refund upon winning a chargeback dispute.
3. **Invariants**:
   - Balances are stored strictly as `long` minor units (cents / paisa). Never floating point or BigDecimal.
   - An entry stores `amountMinor`, `balanceBeforeMinor`, and `balanceAfterMinor`, ensuring a verifiable audit chain.

---

### Pathway C: Card PAN Cryptography & Blind Indexing

Card numbers never touch disk or logs unencrypted.

1. **Issuance**:
   - [`CardService.java`](file:///backend/src/main/java/dev/minicard/processor/cards/service/CardService.java) issues a card. [`PanGenerator.java`](file:///backend/src/main/java/dev/minicard/processor/cards/service/PanGenerator.java) generates a Luhn-compliant 16-digit PAN under the configured BIN (`990000`).
2. **AES-256-GCM Encryption**:
   - [`PanCipher.java`](file:///backend/src/main/java/dev/minicard/processor/crypto/PanCipher.java) encrypts the PAN using AES-256-GCM. The `cardId` UUID is passed as Associated Authenticated Data (AAD) to prevent ciphertext swapping between cards.
3. **HMAC-SHA256 Blind Index**:
   - [`PanBlindIndexer.java`](file:///backend/src/main/java/dev/minicard/processor/crypto/PanBlindIndexer.java) computes a keyed HMAC hash of the normalized PAN. This allows exact-match database lookups without decrypting all PANs.
4. **Masking**:
   - All logs, DTOs, and frontend screens display PANs formatted by [`PanMasker.java`](file:///backend/src/main/java/dev/minicard/processor/crypto/PanMasker.java) (`990000******1234`). Only the one-time issuance response exposes the raw PAN if `exposePanOnIssue` is enabled.

---

### Pathway D: Dispute Management & Automatic Sweeper

Disputes follow a formal finite state machine.

1. **Opening a Dispute**:
   - [`DisputeService.java`](file:///backend/src/main/java/dev/minicard/processor/disputes/service/DisputeService.java) verifies eligibility (transaction must be `APPROVED`, within 120 days, and not already disputed).
   - Creates a [`Dispute.java`](file:///backend/src/main/java/dev/minicard/processor/disputes/domain/Dispute.java) aggregate in `OPEN` state and appends an event to [`DisputeEvent.java`](file:///backend/src/main/java/dev/minicard/processor/disputes/domain/DisputeEvent.java).
2. **State Transitions**:
   - Transitions follow [`DisputeStateMachine.java`](file:///backend/src/main/java/dev/minicard/processor/disputes/domain/DisputeStateMachine.java). Allowed actions (`allowedActions`) are evaluated dynamically based on current status.
   - `OPEN` -> `EVIDENCE_REQUESTED` -> `EVIDENCE_SUBMITTED` -> `RESOLVED_WON` or `RESOLVED_LOST`.
3. **Ledger Credit on Won**:
   - When resolved `WON`, `LedgerService.recordDisputeCredit(...)` is invoked atomically.
   - If the card has been closed, crediting is prohibited and throws `409 INVALID_STATE_TRANSITION`.
4. **Automated Background Sweeper**:
   - [`DisputeDeadlineJob.java`](file:///backend/src/main/java/dev/minicard/processor/disputes/job/DisputeDeadlineJob.java) periodically scans for disputes in `EVIDENCE_REQUESTED` where `evidenceDueAt < clock.instant()`.
   - Uses `SELECT ... FOR UPDATE SKIP LOCKED` and per-item `REQUIRES_NEW` transactions so that concurrent cluster nodes or single failures do not block other disputes. Overdue disputes automatically transition to `RESOLVED_LOST` with an audit trail.

---

### Pathway E: Frontend Single-Page Application

The Angular SPA follows modern standalone architecture:

1. **Reactive Auth Store**:
   - [`auth.store.ts`](file:///frontend/src/app/core/auth/auth.store.ts) holds JWT access token exclusively in memory (signals). Refresh tokens are handled via HttpOnly cookies.
2. **Single-Flight Refresh Interceptor**:
   - [`auth.interceptor.ts`](file:///frontend/src/app/core/auth/auth.interceptor.ts) coordinates multiple concurrent 401s into a single shared refresh call, replaying waiting requests on success.
3. **Problem Details Error Handling**:
   - RFC 9457 errors from the backend are converted by [`error.interceptor.ts`](file:///frontend/src/app/core/http/error.interceptor.ts) into typed `ApiError` models and mapped directly into form controls.
4. **Payment Simulator**:
   - [`payment-simulator.component.ts`](file:///frontend/src/app/features/transactions/simulator/payment-simulator.component.ts) allows testing real POS authorizations, idempotency replays, and rapid-fire velocity evaluations.
