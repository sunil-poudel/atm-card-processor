# ADR 0006: Authorization Idempotency and Fingerprinting

## Status
Accepted

## Context
Payment networks and client applications often retry authorization requests upon network timeouts or transient communication failures. The issuer-processor must ensure that retried authorizations do not result in double debits, multiple authorizations, or silent state corruption when a key is maliciously or accidentally reused with different payload parameters.

## Decision
1. **Idempotency Header & Scope**: Every authorization request requires an `Idempotency-Key` header matching `^[A-Za-z0-9_.:-]{8,64}$`. To prevent inter-tenant collisions, keys are scoped by caller identity:
   - API clients: `CLIENT:<apiClientId>`
   - Authenticated users: `USER:<userId>`
2. **Database Constraint**: A database unique constraint on `(idempotency_scope, idempotency_key)` in the `transactions` table guarantees single execution at storage level.
3. **Request Fingerprinting**: An unambiguous length-prefixed fingerprint is computed:
   `SHA-256(len(cardId):cardId:len(amount):amount:len(currency):currency:len(merchant):merchant:len(mcc):mcc:len(ref):ref)`
   where `merchantName` is trimmed and `reference` is empty string if absent.
4. **Replay Semantics**: If an existing transaction is found matching `(scope, key)`:
   - If stored fingerprint matches: return the previously stored response with HTTP header `Idempotent-Replayed: true`. No locks are held, no new ledger rows are written, and no balance changes occur.
   - If stored fingerprint differs: reject with HTTP 422 `IDEMPOTENCY_KEY_REUSED`.
5. **Race Condition Handling**: If concurrent requests with the same scope and key race and attempt insert, the unique database constraint will trigger a `DataIntegrityViolationException`, which is caught and mapped to HTTP 409 `IDEMPOTENCY_CONFLICT`. The client may safely retry.
6. **Decided Outcomes Only**: Only decided transactions (APPROVED or DECLINED) are stored and consume idempotency keys. Pre-decision rejections (such as HTTP 400 validation failures or HTTP 404 card not found) do not persist a transaction record and do not consume the key.

## Consequences
- Guaranteed at-most-once financial debits across retries and client reconnections.
- Safe retries with transparent replay indicator.
- Protection against parameter tampering under existing idempotency keys.
