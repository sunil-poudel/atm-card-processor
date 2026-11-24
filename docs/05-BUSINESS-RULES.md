# 05. Business Rules and Algorithms

This document is **normative**. If code and this document disagree, the code is wrong (or an ADR must record why this document was changed, and this document must be updated in the same commit).

## 1. Foundations

### 1.1 Response code catalog

Only these codes are produced. They are borrowed from the ISO 8583 vocabulary.

| Code | Message | Used for |
|------|---------|----------|
| `00` | Approved | Approved payments |
| `12` | Invalid transaction | `CURRENCY_MISMATCH` |
| `51` | Insufficient funds | `INSUFFICIENT_FUNDS` |
| `54` | Expired card | `CARD_EXPIRED` |
| `57` | Transaction not permitted to cardholder | `MCC_RESTRICTED` |
| `59` | Suspected fraud | `SUSPECTED_FRAUD` |
| `61` | Exceeds amount limit | `LIMIT_PER_TRANSACTION`, `LIMIT_DAILY` |
| `62` | Restricted card | `CARD_NOT_ACTIVE` (inactive, blocked or closed) |

`DeclineReason` is the precise internal reason; `responseCode` is what an external system sees. The mapping lives in **one place**: an enum `DeclineReason` with fields `responseCode` and `message`, plus an `ApprovalOutcome` constant for `00`.

### 1.2 Input rules for an authorization request

See the validation table in `04` §1.2. Additional semantic rules:

- `currency` must be a supported currency, else `422 UNSUPPORTED_CURRENCY`. A **supported** currency that differs from the card's currency is not an error: it is a **decline** (`12`).
- `merchantName` is trimmed and stored trimmed.
- `reference` is optional and informational only.

### 1.3 Time

- "Now" is `clock.instant()` read **once** at the start of the authorization and used for every check and the `createdAt` of the stored transaction.
- The **business day** of an instant is its calendar date in the business zone (`Asia/Kathmandu`, UTC+05:45, no daylight saving). `startOfBusinessDay(instant)` is midnight of that date in that zone as an `Instant`.
- A card is **valid through the end of its expiry month** in the business zone. It is expired when `now >= startOfMonth(expiryYear, expiryMonth + 1)` in the business zone.

## 2. Authorization algorithm

`AuthorizationService.authorize(caller, idempotencyKey, request)` is one `@Transactional` method (default isolation, `READ COMMITTED`).

### 2.1 Pseudocode (normative order)

```
authorize(caller, key, req):
    now      = clock.instant()
    scope    = caller is ApiClient ? "CLIENT:" + client.id : "USER:" + user.id
    fingerprint = sha256Hex( lenPrefixed(req.cardId, req.amountMinor, req.currency,
                                         trimmed(req.merchantName), req.mcc, req.reference or "") )

    card = cardRepository.findByIdForUpdate(req.cardId)           # SELECT ... FOR UPDATE, lock timeout 5 s
           or throw CARD_NOT_FOUND (404)                          # no transaction row is written

    existing = transactionRepository.findByScopeAndKey(scope, key)
    if existing present:
        if existing.fingerprint == fingerprint: return replay(existing)   # header Idempotent-Replayed: true
        else: throw IDEMPOTENCY_KEY_REUSED (422)

    decision = first matching of, in this order:
        1. card.status != ACTIVE                        -> DECLINE CARD_NOT_ACTIVE
        2. card is expired at `now`                     -> DECLINE CARD_EXPIRED
        3. req.currency != card.currency                -> DECLINE CURRENCY_MISMATCH
        4. req.mcc in card.blockedMccs                  -> DECLINE MCC_RESTRICTED
        5. req.amountMinor > card.perTransactionLimit   -> DECLINE LIMIT_PER_TRANSACTION
        6. spentToday + req.amountMinor > card.dailyLimit -> DECLINE LIMIT_DAILY
        7. req.amountMinor > card.balanceMinor          -> DECLINE INSUFFICIENT_FUNDS
        8. risk = riskEngine.assess(card, req, now)
           if risk.decision == DECLINE                  -> DECLINE SUSPECTED_FRAUD
        9. otherwise                                    -> APPROVE (flagged = risk.decision == FLAG)

    transaction = new Transaction(... decision, riskScore (0 when step 8 was not reached), flagged ...)
    persist transaction (saveAndFlush) ; persist rule hits if step 8 was reached and produced hits
    if APPROVE:
        ledgerService.recordPurchase(card, transaction, actor)    # debits card, inserts ledger row, sets balanceAfter
    publish metrics, log one INFO line (no PAN)
    return response (transactionId, status, responseCode, message, authorizedAt)
```

Notes:
- **Limits are inclusive:** an amount equal to a limit or equal to the balance is allowed.
- `spentToday` = sum of `abs(amount_minor)` of `PURCHASE` ledger entries of this card with `created_at >= startOfBusinessDay(now)` (and `< now`). Disputes credits do **not** reduce `spentToday`.
- Fraud assessment (step 8) runs **only** when steps 1 to 7 pass. Declines at steps 1 to 7 store `riskScore = 0`, `flagged = false` and no rule hits.
- A fraud **decline** stores the risk score and the rule hits that triggered it (so analysts can see why).
- The transaction row is written for **every** decided request (approved or declined). The only requests without a row are those rejected before the decision: validation failures (400), unknown card (404), idempotency violations (422/409), authentication/authorization failures.
- `processingMillis` = milliseconds between the first and last `clock.instant()` read inside the method.
- A lost race on the unique idempotency constraint (same key and scope inserted by a concurrent request for a different card) surfaces as `409 IDEMPOTENCY_CONFLICT`; the client may retry with the same key.
- A lock timeout on the card row surfaces as `503 TEMPORARILY_UNAVAILABLE` with `Retry-After: 1`.

### 2.2 Idempotency details

| Aspect | Rule |
|--------|------|
| Key location | `Idempotency-Key` header, pattern `^[A-Za-z0-9_.:-]{8,64}$` |
| Scope | `CLIENT:<apiClientId>` or `USER:<userId>`: the same key from different callers never collides |
| Uniqueness | DB unique constraint on `(idempotency_scope, idempotency_key)` |
| Fingerprint | SHA-256 hex of the length-prefixed field list `len:value` for `cardId`, `amountMinor`, `currency`, trimmed `merchantName`, `mcc`, `reference` (empty string when absent). Length prefixes prevent ambiguity (`"ab","c"` vs `"a","bc"`). |
| Replay | Same scope + key + fingerprint: return the **stored** outcome (status 200, same body, `Idempotent-Replayed: true`). No locks beyond the card read, no new rows, no metric increments for approvals. |
| Reuse | Same scope + key, different fingerprint: `422 IDEMPOTENCY_KEY_REUSED` |
| Retention | Keys live as long as the transaction row (no expiry in this project; documented limitation) |
| What is stored | Only decided outcomes. A request that failed validation never consumes a key. |

### 2.3 Concurrency guarantees (each has a test)

1. N parallel approvals against one card whose balance covers only K of them: exactly K approvals, N-K declines (`51`), final balance equals initial minus K times amount, never negative.
2. The same idempotent request sent concurrently M times: exactly one transaction row, one ledger entry, one debit; every response identical (the others replay or report `IDEMPOTENCY_CONFLICT` and succeed on retry).
3. Sum of all ledger entries of a card always equals `balanceMinor`, and every ledger row's `balanceAfterMinor` equals the running sum up to that row.

### 2.4 Ledger rules (`LedgerService`)

`LedgerService` is the **only** code path that changes `Card.balanceMinor`. Methods (all require the caller to already hold the card row lock, asserted by the repository method used to load the card):

| Method | Entry type | Sign | Preconditions |
|--------|------------|------|---------------|
| `recordTopUp(card, amount, description, actor)` | `TOPUP` | `+` | Card is `ACTIVE` or `INACTIVE` |
| `recordPurchase(card, transaction, actor)` | `PURCHASE` | `-` | `transaction.status == APPROVED`, `amount <= balance` |
| `recordDisputeCredit(card, dispute, actor)` | `DISPUTE_CREDIT` | `+` | Dispute is being resolved `WON`; at most one per dispute (unique index) |

Every entry stores `balanceAfterMinor`. Overflow uses `Math.addExact`; failure maps to `AMOUNT_OVERFLOW`.

## 3. Card issuance and life cycle rules

### 3.1 PAN generation

1. Take the configured BIN (`app.cards.bin`, default `990000`, exactly 6 digits, documented as a fictional non-issued range).
2. Append 9 random digits from `SecureRandom`.
3. Append the **Luhn check digit** so the 16-digit number is Luhn-valid.
4. Compute `pan_hash` (HMAC-SHA-256 hex with the hash key). On unique-constraint collision regenerate, at most 5 attempts, then fail with `INTERNAL_ERROR` (practically impossible).

Luhn validation (used by the generator's self-check and by tests): from the rightmost digit moving left, double every second digit (starting with the second from the right); when a doubled value exceeds 9 subtract 9; sum all digits; the number is valid when `sum % 10 == 0`. Provide `Luhn.isValid(String)` and `Luhn.checkDigit(String payload)` with unit tests including known vectors (for example `79927398713` valid).

### 3.2 Storage and display

- Store `pan_encrypted` (AES-256-GCM, see §3.3), `pan_key_id`, `pan_hash`, `bin`, `last4`.
- **Masked PAN** = first 6 digits, then `******`, then last 4 digits (`990000******1234`). This is the only form returned by the API.
- The full PAN is returned **once**, in the response to `POST /cards`, and only when `app.cards.expose-pan-on-issue` is `true` (default `false`; enabled by the Compose and `dev` profiles for demos).
- There is **no endpoint** that decrypts and returns a stored PAN later. Decryption exists in code (`PanCipher.decrypt`) for the issue flow and tests only.
- CVV and PIN are **never** generated, stored or logged.

### 3.3 Encryption

- Algorithm: **AES-256-GCM**, 12-byte random IV per encryption (`SecureRandom`), 128-bit tag, **AAD = the card id** (UTF-8) so ciphertexts cannot be swapped between rows.
- Stored value: `base64url( iv || ciphertext || tag )` without padding.
- Keys: `app.crypto.pan.keys.<keyId>` (base64, 32 bytes). `app.crypto.pan.current-key-id` selects the key for new encryptions; decryption uses the key id stored on the row (enables rotation). Hash key: `app.crypto.pan.hash-key` (base64, at least 32 bytes), a **different** secret.
- Application startup **fails** when a key is missing, malformed, or shorter than 32 bytes (outside the `test` profile tests supply fixed test keys).

### 3.4 Issuance defaults and validation

| Field | Rule |
|-------|------|
| `expiry` | Business-zone current month and year plus `validityYears` (default 3) |
| Limits | Defaults from configuration; custom values must satisfy `1 <= perTransaction <= daily <= 10_000_000_000` else `422 LIMIT_CONFIGURATION_INVALID` |
| Initial balance | Optional; when positive, a `TOPUP` ledger entry `Initial load` is created in the same transaction |
| Blocked MCCs | Optional, each 4 digits, at most 50, stored de-duplicated |
| Status | Always `INACTIVE` |
| Audit | `CARD_ISSUED` with details `last4=1234 currency=NPR` (never the PAN) |

### 3.5 Life cycle actions

All transition actions load the card with the row lock, validate the transition table in `03` §6.1, apply it, bump `updatedAt`, write an audit event, and return the updated card.

| Action | From | To | Extra |
|--------|------|----|-------|
| activate | `INACTIVE` | `ACTIVE` | sets `activatedAt` (first activation only) |
| block | `INACTIVE`, `ACTIVE` | `BLOCKED` | requires reason; sets `blockedAt`, `blockReason` |
| unblock | `BLOCKED` | `ACTIVE` if `activatedAt != null`, else `INACTIVE` | clears `blockReason`, `blockedAt` |
| close | `INACTIVE`, `ACTIVE`, `BLOCKED` | `CLOSED` | requires `balanceMinor == 0`; sets `closedAt` |

Limit changes and MCC list changes are rejected on `CLOSED` cards (`INVALID_STATE_TRANSITION`).

### 3.6 Top-up

Preconditions: card status `ACTIVE` or `INACTIVE`; amount within bounds; new balance computed with `Math.addExact`. Effects: `TOPUP` ledger entry, balance update, audit event `CARD_TOPUP` (details: amount and currency, no balance). Top-up is **not** idempotent (documented limitation); every call is a new entry.

## 4. Fraud rules

### 4.1 Rule types and parameters

| Type | Parameters | Triggers when | Detail text stored on the hit |
|------|------------|---------------|-------------------------------|
| `MAX_AMOUNT` | `thresholdValue` (minor units, > 0) | `amountMinor > thresholdValue` | `amount 6000000 exceeds 5000000` |
| `VELOCITY` | `thresholdValue` (count >= 1), `windowSeconds` (1 to 86400) | number of **prior attempts** (any status) on this card with `createdAt >= now - windowSeconds` is `>= thresholdValue` | `5 prior attempts in 60s (threshold 5)` |
| `BLOCKED_MCC` | `mccCodes` (comma-separated, each 4 digits, normalized sorted unique, max 50) | `req.mcc` is in the list | `mcc 7995 is restricted` |

Every rule also has: `name` (unique, case-insensitive), `description`, `enabled`, `scorePoints` (1 to 100), `action` (`FLAG` or `DECLINE`). Validation mirrors the database `CHECK` and returns `400 VALIDATION_FAILED` with field errors. Thresholds are interpreted in the transaction's own currency units (no FX; documented limitation).

### 4.2 Risk assessment

```
assess(card, req, now):
    rules = enabled rules ordered by name ascending
    hits  = for each rule: evaluator(rule.type).evaluate(rule, context) -> RuleHit?   # 0 or 1 hit per rule
    score = min(100, sum(hit.points))
    decision = DECLINE   if any hit.action == DECLINE or score >= app.fraud.decline-threshold (default 80)
               FLAG      else if any hit.action == FLAG   or score >= app.fraud.flag-threshold    (default 50)
               ALLOW     otherwise
    return RiskAssessment(score, decision, hits)
```

- `RuleEvaluator` is an interface with `supports(FraudRuleType)` and `evaluate(...)`. Spring injects `List<RuleEvaluator>`; adding a new rule type means adding one class and one enum value. This is the **strategy pattern**.
- The `VELOCITY` evaluator needs prior attempt counts through a small port (`AttemptCounter`) implemented over the transaction repository so evaluators stay unit-testable without a database.
- Hits are persisted as `TransactionRuleHit` rows (snapshotting `ruleName`, `ruleType`, `points`, `action`, `detail`).
- The first and default rules are in migration `V6` (see `03` §3).

### 4.3 Rule administration

Create, update (with `version`), enable, disable; **no delete**. Every change is audited (`FRAUD_RULE_CREATED`, `FRAUD_RULE_UPDATED`, `FRAUD_RULE_ENABLED`, `FRAUD_RULE_DISABLED`) with before and after parameter summaries in `details`. Changes apply to the next authorization (rules are read per authorization; caching is a COULD and must be invalidated on change).

## 5. Disputes

### 5.1 Eligibility (opening)

All must hold, else `422 DISPUTE_NOT_ELIGIBLE` (or `409 DISPUTE_ALREADY_EXISTS`):

1. Transaction exists (else `404 TRANSACTION_NOT_FOUND`) and its status is `APPROVED`.
2. `transaction.createdAt >= now - app.disputes.max-age-days` (default 120 days).
3. No dispute exists for the transaction (`409 DISPUTE_ALREADY_EXISTS`; also guarded by the unique constraint).
4. Reason is one of the `DisputeReason` values.

The dispute amount and currency are copied from the transaction (full amount only).

### 5.2 Workflow

| Action | Allowed from | Result | Side effects |
|--------|--------------|--------|--------------|
| open | (none) | `OPENED` | event `null -> OPENED`; audit `DISPUTE_OPENED` |
| request-evidence | `OPENED` | `EVIDENCE_REQUESTED` | `evidenceDueAt = now + app.disputes.evidence-days` (default 7); audit |
| submit-evidence | `EVIDENCE_REQUESTED` and `now <= evidenceDueAt` | `UNDER_REVIEW` | note stored on the event; after the deadline the call fails with `409 INVALID_STATE_TRANSITION` (message `evidence deadline passed`) |
| resolve WON | `UNDER_REVIEW` | `RESOLVED_WON` | `LedgerService.recordDisputeCredit` (+amount) in the **same transaction**; `resolvedAt/By`, `resolutionNote` |
| resolve LOST | `UNDER_REVIEW` | `RESOLVED_LOST` | `resolvedAt/By`, `resolutionNote` |
| deadline job | `EVIDENCE_REQUESTED` and `evidenceDueAt < now` | `RESOLVED_LOST` | actor `SYSTEM`, note `Evidence deadline passed`; audit `DISPUTE_AUTO_CLOSED` |

Every transition goes through `Dispute.transitionTo(...)`, validated against the allowed-transitions map in the `DisputeStatus` enum, and appends a `DisputeEvent`. Illegal transitions throw `INVALID_STATE_TRANSITION`. Concurrent resolution attempts are serialized by `@Version` plus the unique constraint on the ledger credit; the loser gets `409`.

Dispute credit on a card that is `CLOSED`: a closed card must not receive money, so `resolve WON` fails with `409 INVALID_STATE_TRANSITION` (message `card is closed`) and the dispute stays `UNDER_REVIEW`. A card in any other status (including `BLOCKED`) receives the credit.

`allowedActions` returned to the UI is derived from the status (and the deadline for `SUBMIT_EVIDENCE`), so the frontend does not duplicate the state machine.

### 5.3 Deadline job

`DisputeDeadlineJob` runs on `app.disputes.deadline-job-cron` (default hourly, `0 0 * * * *`) in the business zone. `runOnce()` is public and unit/integration tested with the controllable clock. It selects up to 100 overdue disputes per batch in a loop, and processes **each dispute in its own transaction** (`REQUIRES_NEW` in a separate bean) so one failure does not block others; it re-checks the status inside the transaction. For multi-instance deployments the query should use `FOR UPDATE SKIP LOCKED` (document in the ADR; implement it since PostgreSQL supports it).

## 6. Reports

### 6.1 Definitions

- **attempts**: all transactions in the window (approved + declined).
- **approved**, **declined**: by `status`. **flagged**: approved with `flagged = true`.
- **approvalRatePercent**: `approved / attempts * 100`, `BigDecimal`, scale 2, `RoundingMode.HALF_UP`; `0.00` when `attempts == 0`.
- **approvedVolume**: sum of `amountMinor` of approved transactions grouped by currency.
- **declinesByCode**: declined transactions grouped by `responseCode`, ordered by count descending then code ascending; `message` from the catalog.
- **window** for a calendar date `D`: `[startOfBusinessDay(D), startOfBusinessDay(D+1))` as instants.
- **hourly**: bucket by hour of day **in the business zone**, zero-filled to 24 entries.
- **series** (range summary): per business-zone calendar date, zero-filled.

### 6.2 SQL guidance (PostgreSQL)

Use native queries for aggregates, passing the zone as a parameter, for example:

```sql
SELECT (t.created_at AT TIME ZONE :zone)::date AS day,
       COUNT(*) FILTER (WHERE t.status = 'APPROVED') AS approved,
       COUNT(*) FILTER (WHERE t.status = 'DECLINED') AS declined,
       COUNT(*) FILTER (WHERE t.status = 'APPROVED' AND t.flagged) AS flagged
FROM transactions t
WHERE t.created_at >= :fromInstant AND t.created_at < :toInstant
GROUP BY 1
ORDER BY 1;
```

Filter by the indexed `created_at` range first; convert zones only for grouping. Return projections (interfaces or records), not entities. Tests must include transactions at `23:59:59` and `00:00:00` local time on both sides of a business-day boundary, and a range that spans a month end.

## 7. Audit events

`AuditService.record(action, entityType, entityId, details)` captures actor (from the security context), request id and client IP (from the request). Actions (exact strings):

`LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `TOKEN_REUSE_DETECTED`, `LOGOUT`, `USER_CREATED`, `USER_UPDATED`, `API_CLIENT_CREATED`, `API_CLIENT_REVOKED`, `CARD_ISSUED`, `CARD_ACTIVATED`, `CARD_BLOCKED`, `CARD_UNBLOCKED`, `CARD_CLOSED`, `CARD_LIMITS_CHANGED`, `CARD_MCC_CHANGED`, `CARD_TOPUP`, `FRAUD_RULE_CREATED`, `FRAUD_RULE_UPDATED`, `FRAUD_RULE_ENABLED`, `FRAUD_RULE_DISABLED`, `DISPUTE_OPENED`, `DISPUTE_EVIDENCE_REQUESTED`, `DISPUTE_EVIDENCE_SUBMITTED`, `DISPUTE_RESOLVED`, `DISPUTE_AUTO_CLOSED`, `EXPORT_GENERATED`.

Authorizations are **not** audit events (they are transactions). Failed logins record the attempted email in `actor_label` (truncated to 100 chars) and never the password. `details` never contains secrets, tokens, full PANs or balances beyond what the action needs.

## 8. MCC catalog (curated constant, shown in the UI)

| MCC | Label |
|-----|-------|
| 4111 | Local transport |
| 4121 | Taxis and ride hailing |
| 4511 | Airlines |
| 4900 | Utilities |
| 5200 | Home supply stores |
| 5311 | Department stores |
| 5411 | Grocery stores, supermarkets |
| 5541 | Service stations |
| 5651 | Family clothing |
| 5732 | Electronics stores |
| 5812 | Restaurants |
| 5814 | Fast food |
| 5912 | Drug stores, pharmacies |
| 5999 | Miscellaneous retail |
| 6011 | ATM cash withdrawal |
| 6012 | Financial institutions |
| 7011 | Hotels and lodging |
| 7832 | Cinemas |
| 7995 | Betting and gambling |
| 8062 | Hospitals |

## 9. Worked examples (these become the decision-matrix tests in `11`)

Assume card: `ACTIVE`, `NPR`, balance 100000, per-transaction limit 50000, daily limit 120000, no blocked MCCs, default fraud rules, nothing spent today.

| # | Request | Expected |
|---|---------|----------|
| 1 | 20000 NPR, MCC 5411 | `APPROVED 00`, balance 80000 |
| 2 | 50000 NPR (equals per-txn limit) | `APPROVED 00`, balance 50000 |
| 3 | 50001 NPR | `DECLINED 61` `LIMIT_PER_TRANSACTION` |
| 4 | Card `INACTIVE`, 100 NPR | `DECLINED 62` `CARD_NOT_ACTIVE` |
| 5 | Card `BLOCKED`, 100 NPR | `DECLINED 62` |
| 6 | Expiry month already passed | `DECLINED 54` `CARD_EXPIRED` |
| 7 | 100 USD on an NPR card | `DECLINED 12` `CURRENCY_MISMATCH` |
| 8 | Card blocks MCC 6011, request MCC 6011 | `DECLINED 57` `MCC_RESTRICTED` |
| 9 | Card with balance 500000 (so balance is not the constraint) and daily limit 120000. Approve three payments of 40000 (total exactly 120000), then request 1 more | first three `APPROVED 00`; the fourth is `DECLINED 61` `LIMIT_DAILY` |
| 10 | Balance 100, request 101 | `DECLINED 51` |
| 11 | Balance 100, request exactly 100 | `APPROVED 00`, balance 0 |
| 12 | 6,000,000 NPR with per-txn limit raised above it and balance sufficient | `APPROVED 00` flagged (`High value payment`, score 40) |
| 13 | 6th attempt within 60 seconds (5 prior attempts of any status) | `DECLINED 59` `SUSPECTED_FRAUD`, hit `Rapid-fire attempts`, score 60 |
| 14 | MCC 7995 | `DECLINED 59`, hit `Restricted merchant categories`, score 100 |
| 15 | Same key and payload twice | second call: identical body, header `Idempotent-Replayed: true`, one debit only |
| 16 | Same key, amount changed | `422 IDEMPOTENCY_KEY_REUSED` |
| 17 | Unknown `cardId` | `404 CARD_NOT_FOUND`, no transaction row |
| 18 | Amount 0 or negative | `400 VALIDATION_FAILED` |
| 19 | Day boundary: 23:59:59 local approvals count toward that day's `spentToday`; 00:00:00 local starts a new day | daily limit resets at local midnight |
| 20 | Dispute won on an approved 20000 transaction | ledger `DISPUTE_CREDIT +20000`, balance +20000, exactly once |
