# 04. HTTP API Specification

Base path: `/api/v1`. JSON only (`application/json`, UTF-8) except the CSV export. All timestamps are ISO-8601 UTC instants (`2026-10-20T09:15:30.123Z`). All money fields are integer **minor units** with the suffix `Minor`.

## 1. Conventions

### 1.1 Common headers

| Header | Direction | Meaning |
|--------|-----------|---------|
| `Authorization: Bearer <jwt>` | request | Staff authentication (see `06`) |
| `X-API-Key: <key>` | request | API client authentication; accepted **only** on `POST /authorize` |
| `Idempotency-Key` | request | Required on `POST /authorize`. Pattern `^[A-Za-z0-9_.:-]{8,64}$` |
| `X-Request-Id` | request, response | Correlation id (pattern `^[A-Za-z0-9-]{8,64}$`); generated when absent; always echoed |
| `Idempotent-Replayed: true` | response | Present when `POST /authorize` returned a stored result |
| `Location` | response | On `201 Created`, URL of the new resource |
| `Retry-After` | response | On `429` (when rate limiting is implemented) |
| `Cache-Control: no-store` | response | On **every** API response |
| `X-Content-Type-Options: nosniff` | response | On every response |

### 1.2 Validation limits (shared by controllers, services and the frontend)

| Field | Rule |
|-------|------|
| `amountMinor` (payments, top-ups) | integer, `1 <= v <= 10_000_000_000` |
| `perTransactionLimitMinor`, `dailyLimitMinor` | integer, `1 <= v <= 10_000_000_000`; `daily >= perTransaction` |
| `initialBalanceMinor` | integer, `0 <= v <= 10_000_000_000` |
| `currency` | `NPR` or `USD` |
| `merchantName` | 1 to 100 characters after trimming; no control characters |
| `mcc` | exactly 4 digits |
| `reference` | optional, 1 to 64 characters, `[A-Za-z0-9 _.:/#-]` |
| `holderName` | 2 to 100 characters; letters (any script), spaces, `.`, `'`, `-` |
| `validityYears` | integer 1 to 5, default 3 |
| `email` | valid email, at most 254 characters, stored lowercase |
| `password` | 12 to 128 characters, must not equal the email |
| `displayName` | 2 to 100 characters |
| `note`, `description`, `reason` (free text) | at most 1000 characters (block reason at most 200) |
| Page size | `size` 1 to 100, default 20; `page` is zero-based |

Free text is stored as-is, never interpreted as HTML. The frontend renders it as text only.

### 1.3 Pagination

Request: `?page=0&size=20&sort=createdAt,desc`. `sort` accepts **only whitelisted** properties per endpoint; anything else returns `400 VALIDATION_FAILED`.

Response envelope (`PageResponse<T>`):

```json
{
  "content": [ ],
  "page": { "number": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
}
```

### 1.4 Error format (RFC 9457 Problem Details, `application/problem+json`)

```json
{
  "type": "urn:minicard:problem:card-not-found",
  "title": "Card not found",
  "status": 404,
  "detail": "No card exists with id 7a0c3f0e-....",
  "instance": "/api/v1/cards/7a0c3f0e-...",
  "code": "CARD_NOT_FOUND",
  "requestId": "6f1d6a0e-3b4a-4d1d-9c53-2f7c2a8a7c11",
  "timestamp": "2026-10-20T09:15:30.123Z"
}
```

Validation errors add `errors`:

```json
{
  "type": "urn:minicard:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "code": "VALIDATION_FAILED",
  "requestId": "...",
  "timestamp": "...",
  "errors": [
    { "field": "amountMinor", "message": "must be greater than or equal to 1" },
    { "field": "mcc", "message": "must be exactly 4 digits" }
  ]
}
```

Rules: never include stack traces, SQL, class names, secrets or PANs in any error. `detail` is human readable and safe. Clients switch on `code`, never on `detail`.

### 1.5 Error code catalog

| `code` | HTTP | Meaning |
|--------|------|---------|
| `VALIDATION_FAILED` | 400 | Bean validation or parameter errors (`errors[]` populated) |
| `MALFORMED_REQUEST` | 400 | Unreadable JSON, wrong types, unsupported media type details |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Missing or malformed `Idempotency-Key` |
| `UNAUTHENTICATED` | 401 | Missing, invalid or expired credentials |
| `INVALID_CREDENTIALS` | 401 | Login failed (generic; also used while an account is locked) |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh cookie missing, unknown, expired or reused |
| `FORBIDDEN` | 403 | Authenticated but not allowed (role) or missing `X-Requested-With` on cookie endpoints |
| `CARD_NOT_FOUND` | 404 | |
| `TRANSACTION_NOT_FOUND` | 404 | |
| `DISPUTE_NOT_FOUND` | 404 | |
| `FRAUD_RULE_NOT_FOUND` | 404 | |
| `USER_NOT_FOUND` | 404 | |
| `API_CLIENT_NOT_FOUND` | 404 | |
| `RESOURCE_NOT_FOUND` | 404 | Unknown route |
| `INVALID_STATE_TRANSITION` | 409 | Card or dispute state machine violation |
| `IDEMPOTENCY_CONFLICT` | 409 | A concurrent request with the same key is still being processed or lost a race; safe to retry |
| `OPTIMISTIC_LOCK_FAILURE` | 409 | Stale `version`; reload and retry |
| `EMAIL_ALREADY_USED` | 409 | |
| `NAME_ALREADY_USED` | 409 | API client or fraud rule name conflict |
| `DISPUTE_ALREADY_EXISTS` | 409 | One dispute per transaction |
| `LAST_ADMIN_PROTECTED` | 409 | Cannot disable or demote the last enabled admin, or yourself |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Same key, different payload |
| `DISPUTE_NOT_ELIGIBLE` | 422 | Transaction is declined or older than the allowed window |
| `UNSUPPORTED_CURRENCY` | 422 | |
| `LIMIT_CONFIGURATION_INVALID` | 422 | e.g. daily limit below per-transaction limit |
| `AMOUNT_OVERFLOW` | 422 | Arithmetic would overflow |
| `EXPORT_TOO_LARGE` | 422 | CSV export would exceed 50,000 rows |
| `RATE_LIMITED` | 429 | Only if rate limiting (COULD) is implemented |
| `TEMPORARILY_UNAVAILABLE` | 503 | Database or lock acquisition timeout; safe to retry (`Retry-After: 1`) |
| `INTERNAL_ERROR` | 500 | Unexpected failure; generic message; full detail only in logs |

Status mapping notes: unknown resource ids are `404`; business-rule violations on valid input are `409` or `422` as listed; **a declined payment is not an error** (see §5).

## 2. Authentication endpoints

### POST `/auth/login`  (anonymous)

Request:
```json
{ "email": "admin@example.com", "password": "correct horse battery staple" }
```
Response `200`:
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": { "id": "…", "email": "admin@example.com", "displayName": "Admin", "role": "ADMIN" }
}
```
Also sets the refresh cookie (see `06` §3.2): `Set-Cookie: mc_refresh=<opaque>; Path=/api/v1/auth; HttpOnly; SameSite=Strict; Max-Age=604800` (+ `Secure` when `app.security.cookie-secure=true`).
Errors: `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS`.

### POST `/auth/refresh`  (anonymous; requires refresh cookie and header `X-Requested-With: XMLHttpRequest`)

Rotates the refresh token (old one becomes revoked, a new cookie is set) and returns the same body as login. Errors: `401 REFRESH_TOKEN_INVALID`, `403 FORBIDDEN` (header missing).

### POST `/auth/logout`  (requires cookie and `X-Requested-With`)

Revokes the whole token family, clears the cookie. `204`. Idempotent: a missing cookie also returns `204`.

### GET `/auth/me`  (any authenticated user)

`200` `{ "id", "email", "displayName", "role" }`.

## 3. Users (ADMIN)

| Method and path | Description | Success |
|-----------------|-------------|---------|
| `GET /users?page&size&sort=email,asc` | List users (sort: `email`, `displayName`, `createdAt`) | 200 `PageResponse<UserResponse>` |
| `POST /users` | Create `{ email, displayName, role, password }` | 201 `UserResponse` |
| `PATCH /users/{id}` | Partial update `{ displayName?, role?, enabled?, newPassword?, version }` | 200 `UserResponse` |

`UserResponse`: `{ id, email, displayName, role, enabled, locked (boolean), createdAt, updatedAt, version }`. Never returns the hash. Errors: `409 EMAIL_ALREADY_USED`, `409 LAST_ADMIN_PROTECTED`, `409 OPTIMISTIC_LOCK_FAILURE`, `404 USER_NOT_FOUND`.

## 4. API clients (ADMIN)

| Method and path | Description | Success |
|-----------------|-------------|---------|
| `GET /api-clients` | List (page, size) | 200 page of `ApiClientResponse` |
| `POST /api-clients` | Create `{ name }`; returns the key **once** | 201 `ApiClientCreatedResponse` |
| `POST /api-clients/{id}/revoke` | Revoke (idempotent) | 200 `ApiClientResponse` |

`ApiClientResponse`: `{ id, name, keyPrefix, status, createdAt, revokedAt, lastUsedAt }`.
`ApiClientCreatedResponse`: the same plus `apiKey` (format `mck_<8 chars>_<43 chars>`). The key is **never retrievable again**.

## 5. Authorization (the core endpoint)

### POST `/authorize`

Auth: API key (`X-API-Key`) **or** staff JWT with role `ADMIN`. Headers: `Idempotency-Key` (required).

Request:
```json
{
  "cardId": "0c3f1f3a-6f0e-7a4b-9e0c-5b7a1a2b3c4d",
  "amountMinor": 50000,
  "currency": "NPR",
  "merchantName": "Demo Mart",
  "mcc": "5411",
  "reference": "POS-1042"
}
```

Response `200` (approved **or** declined; the outcome is business data, not an HTTP error):
```json
{
  "transactionId": "0192f0aa-....",
  "status": "APPROVED",
  "responseCode": "00",
  "responseMessage": "Approved",
  "authorizedAt": "2026-10-20T09:15:30.123Z"
}
```
Declined example:
```json
{
  "transactionId": "0192f0ab-....",
  "status": "DECLINED",
  "responseCode": "51",
  "responseMessage": "Insufficient funds",
  "authorizedAt": "2026-10-20T09:15:31.004Z"
}
```

The response intentionally omits the balance, the internal decline reason, the risk score and the flag. A merchant must not learn them. Staff read them via `GET /transactions/{id}`.

Replays: the same `Idempotency-Key` with an identical payload returns the **stored** response with `Idempotent-Replayed: true` and **does not** change any state.

Errors: `400 VALIDATION_FAILED`, `400 IDEMPOTENCY_KEY_REQUIRED`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 CARD_NOT_FOUND`, `409 IDEMPOTENCY_CONFLICT`, `422 IDEMPOTENCY_KEY_REUSED`, `422 AMOUNT_OVERFLOW`.

The decision algorithm is specified in `05` §2.

## 6. Cards

`CardResponse`:
```json
{
  "id": "…",
  "maskedPan": "990000******1234",
  "bin": "990000",
  "last4": "1234",
  "holderName": "Sita Sharma",
  "expiryMonth": 10,
  "expiryYear": 2029,
  "expired": false,
  "currency": "NPR",
  "status": "ACTIVE",
  "balanceMinor": 250000,
  "perTransactionLimitMinor": 5000000,
  "dailyLimitMinor": 20000000,
  "blockedMccs": ["7995"],
  "blockReason": null,
  "activatedAt": "…", "blockedAt": null, "closedAt": null,
  "createdAt": "…", "updatedAt": "…", "version": 3
}
```
Only the **issue** response may additionally contain `"pan": "9900001234567890"` and only when `app.cards.expose-pan-on-issue=true`; it is never returned again by any endpoint.

| Method and path | Roles | Description | Success |
|-----------------|-------|-------------|---------|
| `POST /cards` | ADMIN | Issue a card. Body below. | 201 `CardResponse` |
| `GET /cards` | ADMIN, ANALYST, DISPUTE_OFFICER | Filters: `status`, `currency`, `q` (holder name contains, case-insensitive, or exact last 4), `expired` (boolean). Sort: `createdAt`, `holderName`, `balanceMinor`. | 200 page |
| `GET /cards/{id}` | same | Detail | 200 |
| `POST /cards/{id}/activate` | ADMIN | `INACTIVE -> ACTIVE` | 200 |
| `POST /cards/{id}/block` | ADMIN | Body `{ "reason": "Lost card" }` (required, 3 to 200 chars) | 200 |
| `POST /cards/{id}/unblock` | ADMIN | See `03` §6.1 | 200 |
| `POST /cards/{id}/close` | ADMIN | Requires zero balance | 200 |
| `PUT /cards/{id}/limits` | ADMIN | `{ perTransactionLimitMinor, dailyLimitMinor, version }` | 200 |
| `PUT /cards/{id}/blocked-mccs` | ADMIN | `{ "mccs": ["7995", "6011"], "version" }` (replace set, max 50) | 200 |
| `POST /cards/{id}/topups` | ADMIN | `{ amountMinor, description? }`. Card must be `ACTIVE` or `INACTIVE` (not `BLOCKED`/`CLOSED`) | 201 `LedgerEntryResponse` |
| `GET /cards/{id}/ledger` | read roles | Sort `createdAt`; default newest first | 200 page of `LedgerEntryResponse` |
| `GET /cards/{id}/transactions` | read roles | Same shape as `GET /transactions` scoped to the card | 200 page |

Issue request:
```json
{
  "holderName": "Sita Sharma",
  "currency": "NPR",
  "validityYears": 3,
  "perTransactionLimitMinor": 5000000,
  "dailyLimitMinor": 20000000,
  "initialBalanceMinor": 100000,
  "blockedMccs": ["7995"]
}
```
All fields except `holderName` and `currency` are optional; limits default from configuration (`app.cards.default-per-transaction-limit-minor` = 5,000,000 and `app.cards.default-daily-limit-minor` = 20,000,000). A positive `initialBalanceMinor` creates a `TOPUP` ledger entry (description `Initial load`). The new card starts `INACTIVE`.

`LedgerEntryResponse`: `{ id, cardId, type, amountMinor (signed), balanceAfterMinor, transactionId?, disputeId?, description, createdBy, createdAt }`.

Mutating card endpoints that take `version` return `409 OPTIMISTIC_LOCK_FAILURE` when stale. The action endpoints (`activate`, `block`, ...) do not need `version` because they use the row lock.

## 7. Transactions

`TransactionSummary` (list item):
```json
{
  "id": "…", "createdAt": "…", "cardId": "…", "cardLast4": "1234", "holderName": "Sita Sharma",
  "amountMinor": 50000, "currency": "NPR", "merchantName": "Demo Mart", "mcc": "5411",
  "status": "APPROVED", "responseCode": "00", "declineReason": null,
  "flagged": false, "riskScore": 0,
  "source": "API_CLIENT", "sourceName": "demo-pos"
}
```
`TransactionDetail` adds: `reference`, `balanceAfterMinor`, `processingMillis`, `idempotencyKey`, `ruleHits: [{ ruleId, ruleName, ruleType, points, action, detail }]`, `dispute: { id, status } | null`.

| Method and path | Roles | Description |
|-----------------|-------|-------------|
| `GET /transactions` | ADMIN, ANALYST, DISPUTE_OFFICER | Filters: `cardId`, `status`, `responseCode`, `declineReason`, `mcc`, `flagged`, `minAmountMinor`, `maxAmountMinor`, `from`, `to` (instants, `from` inclusive, `to` exclusive), `q` (merchant name contains). Sort: `createdAt`, `amountMinor`. Default `createdAt,desc`. |
| `GET /transactions/{id}` | same | Detail |
| `GET /transactions/export.csv` | ADMIN, ANALYST | Same filters, no paging. `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="transactions-<yyyyMMdd-HHmmss>.csv"`. At most 50,000 rows else `422 EXPORT_TOO_LARGE`. Cells that start with `=`, `+`, `-`, `@`, tab or carriage return are prefixed with a single quote (`'`) to defeat CSV/formula injection. RFC 4180 quoting. |

CSV columns (header row exactly): `transactionId,createdAt,cardLast4,amount,currency,merchantName,mcc,status,responseCode,declineReason,flagged,riskScore`. `amount` is the major-unit decimal with two digits (`500.00`). The export is audited (`EXPORT_GENERATED`).

## 8. Fraud rules

`FraudRuleResponse`: `{ id, name, description, type, enabled, thresholdValue, windowSeconds, mccCodes: [], scorePoints, action, createdAt, updatedAt, version }`.

| Method and path | Roles | Description |
|-----------------|-------|-------------|
| `GET /fraud-rules` | ADMIN, ANALYST | All rules (not paged; there are few), sorted by `name` |
| `POST /fraud-rules` | ADMIN | Create. Parameter combination validated per type (see `05` §4.1) |
| `PUT /fraud-rules/{id}` | ADMIN | Full update including `version`. `type` cannot change. |
| `POST /fraud-rules/{id}/enable` | ADMIN | |
| `POST /fraud-rules/{id}/disable` | ADMIN | |

There is **no delete** (rules referenced by past transactions must remain). Changes take effect for the next authorization immediately and are audited.

## 9. Disputes

`DisputeResponse`: `{ id, transactionId, cardId, cardLast4, reason, description, status, amountMinor, currency, openedBy, openedAt, evidenceDueAt, resolvedAt, resolvedBy, resolutionNote, allowedActions: ["REQUEST_EVIDENCE" | "SUBMIT_EVIDENCE" | "RESOLVE_WON" | "RESOLVE_LOST"] }`.
`DisputeDetail` adds `events: [{ id, fromStatus, toStatus, actor, note, createdAt }]` (oldest first) and the embedded `transaction` summary.

| Method and path | Roles | Description | Success |
|-----------------|-------|-------------|---------|
| `POST /disputes` | ADMIN, DISPUTE_OFFICER | `{ transactionId, reason, description? }`. Eligibility in `05` §5.1 | 201 `DisputeDetail` |
| `GET /disputes` | ADMIN, ANALYST, DISPUTE_OFFICER | Filters: `status`, `reason`, `cardId`, `overdue` (boolean: evidence deadline passed and still `EVIDENCE_REQUESTED`). Sort: `openedAt`, `evidenceDueAt`, `amountMinor` | 200 page |
| `GET /disputes/{id}` | same | Detail | 200 |
| `POST /disputes/{id}/request-evidence` | ADMIN, DISPUTE_OFFICER | Body `{ note? }`. Sets `evidenceDueAt = now + app.disputes.evidence-days` | 200 |
| `POST /disputes/{id}/submit-evidence` | ADMIN, DISPUTE_OFFICER | Body `{ note }` (required, 3 to 1000 chars) | 200 |
| `POST /disputes/{id}/resolve` | ADMIN, DISPUTE_OFFICER | Body `{ outcome: "WON" | "LOST", note }` (note required) | 200 |

Errors: `404 DISPUTE_NOT_FOUND`, `409 INVALID_STATE_TRANSITION`, `409 DISPUTE_ALREADY_EXISTS`, `422 DISPUTE_NOT_ELIGIBLE`.

## 10. Reports (ADMIN, ANALYST)

### GET `/reports/daily?date=2026-10-20`

`date` is a calendar date in the business zone; default is today. Response:

```json
{
  "date": "2026-10-20",
  "zone": "Asia/Kathmandu",
  "attempts": 120,
  "approved": 101,
  "declined": 19,
  "flagged": 4,
  "approvalRatePercent": 84.17,
  "approvedVolume": [ { "currency": "NPR", "amountMinor": 12500000 } ],
  "declinesByCode": [ { "responseCode": "51", "message": "Insufficient funds", "count": 9 } ],
  "topMccs": [ { "mcc": "5411", "label": "Grocery stores, supermarkets", "attempts": 40, "approvedVolume": [ { "currency": "NPR", "amountMinor": 3100000 } ] } ],
  "hourly": [ { "hour": 0, "attempts": 0, "approved": 0, "declined": 0 } ]
}
```
`hourly` always has 24 entries (hour 0 to 23 in the business zone). `topMccs`: top 5 by `attempts` (ties broken by `mcc` ascending); `label` is `null` for unlabelled MCCs. `approvedVolume` arrays contain one entry per currency that has approved volume and are empty arrays otherwise. `approvalRatePercent`: `approved / attempts * 100` rounded half-up to 2 decimals, `0.00` when there are no attempts.

### GET `/reports/summary?from=2026-10-01&to=2026-10-20`

Inclusive calendar dates in the business zone, at most 92 days, `from <= to` (else `400 VALIDATION_FAILED`). Response:

```json
{
  "from": "2026-10-01", "to": "2026-10-20", "zone": "Asia/Kathmandu",
  "totals": { "attempts": 2400, "approved": 2010, "declined": 390, "flagged": 80, "approvalRatePercent": 83.75 },
  "approvedVolume": [ { "currency": "NPR", "amountMinor": 250000000 } ],
  "series": [ { "date": "2026-10-01", "approved": 100, "declined": 20, "flagged": 3, "approvedVolume": [ { "currency": "NPR", "amountMinor": 12000000 } ] } ],
  "openDisputes": 7,
  "activeCards": 54
}
```
`series` contains **exactly one entry for every calendar date** in the range, in ascending order, zero-filled (counts `0`, `approvedVolume` empty array) for dates without activity. `openDisputes` counts disputes not yet resolved, at the moment of the request. `activeCards` counts cards with status `ACTIVE` at the moment of the request.

## 11. Audit (ADMIN)

`GET /audit-events?action=&entityType=&entityId=&actorLabel=&from=&to=&page&size` returns `PageResponse<AuditEventResponse>`: `{ id, occurredAt, actorType, actorId, actorLabel, action, entityType, entityId, requestId, ipAddress, details }`. Sort: `occurredAt` (default desc).

## 12. Reference data (any authenticated user)

`GET /meta/reference-data` returns static catalogs used by the UI so labels are not duplicated:

```json
{
  "responseCodes": [ { "code": "00", "message": "Approved" } ],
  "declineReasons": [ { "reason": "INSUFFICIENT_FUNDS", "responseCode": "51", "message": "Insufficient funds" } ],
  "mccs": [ { "code": "5411", "label": "Grocery stores, supermarkets" } ],
  "currencies": [ { "code": "NPR", "minorUnits": 2 }, { "code": "USD", "minorUnits": 2 } ],
  "disputeReasons": [ "UNAUTHORIZED_TRANSACTION", "DUPLICATE_CHARGE", "GOODS_NOT_RECEIVED", "INCORRECT_AMOUNT", "OTHER" ]
}
```
The MCC list is a curated constant (see `05` §9). Any 4-digit MCC is accepted by the API even when it has no label.

## 13. Operational endpoints

| Path | Access | Notes |
|------|--------|-------|
| `/actuator/health/liveness`, `/actuator/health/readiness` | anonymous | Probes only; no details |
| `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus` | ADMIN token **or** only on the separate management port | Management port `9090` is not published by Compose |
| `/v3/api-docs`, `/swagger-ui.html` | anonymous in profile `dev`; disabled in `prod` unless `SPRINGDOC_ENABLED=true` | |

## 14. OpenAPI requirements

- Every controller method has `@Operation` summary, documented responses (including error codes), and security requirements.
- Security schemes: `bearerAuth` (HTTP bearer JWT) and `apiKeyAuth` (header `X-API-Key`).
- Request and response DTO records carry `@Schema` descriptions and examples for key fields.
- The generated spec is committed as `docs/openapi.json`; an integration test fails when the committed file differs from the generated one (regenerate with `-Dopenapi.update=true`).
