# 06. Security

This document is **normative** for authentication, authorization, secrets and data protection. Aim: OWASP ASVS Level 1 basics done properly plus the PCI-inspired data handling that suits a card system. Nothing here is optional unless marked COULD.

## 1. Threat model (short)

| Threat | Mitigation |
|--------|-----------|
| Stolen database dump | PAN encrypted (AES-256-GCM); passwords hashed (BCrypt); refresh tokens and API keys stored only as hashes; no CVV/PIN ever stored |
| Stolen access token (XSS) | Token lives only in JS memory; 15-minute lifetime; strict CSP; Angular output escaping; no `innerHTML` with user data |
| Stolen refresh cookie | `HttpOnly`, `SameSite=Strict`, path-scoped; rotation with reuse detection revokes the whole family |
| CSRF | Access token is a bearer header (not auto-sent). Cookie endpoints require `X-Requested-With` header and `SameSite=Strict`; CORS disallows foreign origins |
| Credential stuffing / brute force | Per-account lockout, uniform error, constant-time-ish behaviour, audit |
| Replay of payments | Idempotency keys; request fingerprints |
| Privilege escalation | Deny-by-default; role checks at URL and method level; tests per endpoint and role |
| Injection | Parameterized queries only; validation; no string-built SQL; CSV formula guard |
| Information leakage | Problem Details without internals; merchants never see balance/risk; logs redact secrets and PANs |
| Insecure defaults | `prod` profile has no default secrets; startup fails if any are missing |
| Vulnerable dependencies | Maven Enforcer + `dependency-check` or `npm audit` in CI (report; fail on critical) |

## 2. Roles and the authorization matrix

Roles: `ADMIN`, `ANALYST`, `DISPUTE_OFFICER` (users) and the machine principal `API_CLIENT`. Authorization is **deny by default**: every route not listed below requires authentication and is otherwise rejected.

| Capability | ADMIN | ANALYST | DISPUTE_OFFICER | API_CLIENT | Anonymous |
|------------|:-----:|:-------:|:---------------:|:----------:|:---------:|
| `POST /auth/login`, `/auth/refresh`, `/auth/logout` | yes | yes | yes | no | yes |
| `GET /auth/me`, `GET /meta/reference-data` | yes | yes | yes | no | no |
| Users and API clients management | yes | no | no | no | no |
| Issue / manage cards, top-up | yes | no | no | no | no |
| Read cards, ledger, transactions | yes | yes | yes | no | no |
| Export transactions CSV | yes | yes | no | no | no |
| `POST /authorize` | yes | no | no | **yes** | no |
| Read fraud rules | yes | yes | no | no | no |
| Change fraud rules | yes | no | no | no | no |
| Open / work disputes | yes | no | yes | no | no |
| Read disputes | yes | yes | yes | no | no |
| Reports | yes | yes | no | no | no |
| Audit log | yes | no | no | no | no |
| Liveness/readiness probes | yes | yes | yes | yes | yes |

Implementation: `SecurityFilterChain` with `authorizeHttpRequests` listing path rules, **plus** `@PreAuthorize` on service-facing controller methods (defence in depth). A parameterized integration test iterates every endpoint x every role and asserts the matrix (see `11`). Adding an endpoint without extending the matrix test must fail the build (the test enumerates mappings via `RequestMappingHandlerMapping` and fails on any mapping not present in the matrix).

Unauthenticated access to a protected route returns `401 UNAUTHENTICATED`; authenticated but not permitted returns `403 FORBIDDEN`. Both use Problem Details (custom `AuthenticationEntryPoint` and `AccessDeniedHandler`).

## 3. Staff authentication

### 3.1 Passwords
- Hash with **BCrypt strength 12** through `PasswordEncoder` (`DelegatingPasswordEncoder` with `bcrypt` default so the algorithm can be upgraded).
- Policy: 12 to 128 characters; must not equal the email; reject the 20 most common passwords from a small bundled list (`resources/security/common-passwords.txt`). No composition rules (length beats complexity). Never truncate silently; BCrypt's 72-byte limit is handled by rejecting passwords over 72 **bytes** in UTF-8 with a validation error (document in the field message).
- `password_hash` is never serialized, logged or returned.

### 3.2 Login, tokens and cookies

`POST /auth/login`:
1. Normalize email (trim, lower-case). Look up the user.
2. When the user does not exist, **still run a BCrypt comparison against a fixed dummy hash** to equalize timing, then return `401 INVALID_CREDENTIALS`.
3. If `enabled=false`, or `locked_until > now`, return the same `401 INVALID_CREDENTIALS` (do not reveal state) and audit `LOGIN_FAILED` with details `reason=disabled` or `reason=locked`.
4. On wrong password: increment `failed_login_attempts`; at **5** failures set `locked_until = now + 15 minutes` (configurable `app.security.lockout.*`), audit `ACCOUNT_LOCKED`. Each failure audits `LOGIN_FAILED`.
5. On success: reset the counter and `locked_until`, issue tokens, audit `LOGIN_SUCCESS`.

**Access token**: JWT signed **HS256** with a secret from `app.security.jwt.secret` (at least 32 bytes after base64 decoding; fail fast otherwise). Claims: `iss=minicard-processor`, `sub=<userId>`, `aud=minicard-dashboard`, `role`, `iat`, `exp` (15 minutes), `jti`. Validation in the resource server must check signature, `exp`, `iss` and `aud`, with a clock skew of 30 seconds. Role is read from the `role` claim and mapped to `ROLE_<role>`. The user's current `enabled` flag is **not** re-checked on every request (documented trade-off; the 15-minute lifetime bounds it).

**Refresh token**: 256-bit random value from `SecureRandom`, URL-safe base64, delivered **only** as cookie `mc_refresh`:
`HttpOnly; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800` plus `Secure` when `app.security.cookie-secure=true` (true in Compose-behind-TLS/prod, false in plain-HTTP local runs). The database stores only `SHA-256(token)` hex in `refresh_tokens.token_hash`. Lifetime 7 days, absolute (rotation does not extend the family beyond 7 days from the original login: carry `expires_at` forward as `min(now+7d, familyStart+7d)`; simplest implementation: new token's `expires_at` equals the old token's).

**Rotation with reuse detection** (`POST /auth/refresh`):
1. Hash the cookie value, find the row. Unknown, expired: `401 REFRESH_TOKEN_INVALID` (and clear the cookie).
2. If the row is **already revoked/replaced**: this is reuse. Revoke **every** token of the same `family_id`, audit `TOKEN_REUSE_DETECTED`, return `401 REFRESH_TOKEN_INVALID`.
3. Otherwise mark it revoked, create the successor in the same family (`replaced_by_id` set), return a new access token and set the new cookie. All in one transaction with a row lock on the old token so two parallel refreshes cannot both succeed.
4. The user must still be `enabled`; otherwise revoke the family and return `REFRESH_TOKEN_INVALID`.

`POST /auth/logout` revokes the family and clears the cookie (`Max-Age=0`), audits `LOGOUT`.

A scheduled job (`RefreshTokenCleanupJob`, daily) deletes refresh-token rows expired for more than 30 days.

### 3.3 CSRF stance
No server session exists. State-changing endpoints under `/api/v1` other than the cookie endpoints require a bearer token an attacker's page cannot attach. The three cookie endpoints require the custom header `X-Requested-With: XMLHttpRequest` (cannot be sent cross-site without a CORS preflight, which is denied) and the cookie is `SameSite=Strict`. Spring's CSRF filter is disabled with a code comment pointing to this section and ADR 0008.

## 4. API-client authentication (`POST /authorize` only)

- Key format `mck_<prefix>_<secret>`: `prefix` 8 chars `[a-z0-9]` (random, unique), `secret` 43 chars URL-safe base64 of 32 random bytes. Total about 56 chars.
- Stored: `key_prefix` (lookup) and `key_hash = SHA-256(full key)` hex. A fast hash is appropriate because the secret has 256 bits of entropy (ADR 0009). The full key is shown **once** in the create response.
- Authentication: `ApiKeyAuthenticationFilter` runs **only** for `POST /api/v1/authorize` when `X-API-Key` is present. It looks up by prefix, compares hashes with `MessageDigest.isEqual` (constant time), rejects `REVOKED` clients, updates `last_used_at` at most once per minute (avoid write amplification), and sets an `ApiClientAuthentication` principal with authority `ROLE_API_CLIENT`. A key presented to any other route is ignored and results in `401`.
- The raw key is never logged; the request logging filter redacts `X-API-Key` and `Authorization`.

## 5. Data protection

| Data | Rule |
|------|------|
| PAN | Encrypted at rest (see `05` §3.3). Only the last 4 digits and BIN are stored in clear for display and search. Decryption exists in exactly one place (`PanCipher`), and **no endpoint returns a decrypted PAN** except the one-time issue response when `app.cards.expose-pan-on-issue=true`. |
| CVV / PIN / track data | Never generated, accepted, stored or logged. A request field named `cvv`, `pin`, or `pan` causes `400 MALFORMED_REQUEST` because DTOs reject unknown properties (`spring.jackson.deserialization.fail-on-unknown-properties=true`, verify the property name for the installed Jackson version). |
| Passwords | BCrypt only |
| Tokens / keys | Hashes only |
| Logs | Redaction rules in §8 |
| Backups / exports | CSV contains only last 4 digits |
| Test data | Fake PANs only; BIN `990000` is a documented fictional range |

## 6. HTTP security configuration

Backend (all responses):
- `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` (or CSP `frame-ancestors 'none'`).
- HSTS only when served over TLS (`app.security.hsts=true` for prod behind TLS).
- CORS: **disabled by default** (same-origin through the dev proxy / nginx). If `app.security.cors.allowed-origins` is set, allow only those exact origins, methods `GET,POST,PUT,PATCH`, headers `Authorization,Content-Type,Idempotency-Key,X-Request-Id,X-Requested-With`, no wildcard, `allowCredentials` only for explicit origins.
- Request size limits: JSON body max 64 KB (reject larger with 413 handled as `MALFORMED_REQUEST`); header size defaults.
- Stack traces disabled: `server.error.include-stacktrace=never`, `include-message=never`, `include-binding-errors=never`.

Frontend nginx (`deploy/nginx.conf`):
- `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` (adjust only if a measurable need arises, and document it; self-host fonts and icons so no external CDN is needed).
- `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`.
- Gzip on, `/index.html` not cached, hashed assets cached long-term.

## 7. Input handling

- Bean Validation at the controller boundary with the limits in `04` §1.2, plus domain invariants re-checked in services (never trust the controller to be the only gate).
- `spring.jackson` strict settings: unknown properties fail, `FAIL_ON_NULL_FOR_PRIMITIVES` on, dates as ISO strings.
- All database access via Spring Data derived queries, JPQL with named parameters, or native SQL with bind parameters. **No string concatenation of user input into queries.** Sort properties are whitelisted.
- CSV export neutralizes formula injection (`04` §7).
- Free text is displayed as text in the SPA (Angular interpolation); never bind to `innerHTML`.

## 8. Logging and redaction

- JSON logs in `prod` (Logstash-style fields: `timestamp`, `level`, `logger`, `message`, `requestId`, `userId`/`clientId` when known). Use Spring Boot's built-in structured logging support (`logging.structured.format.console`) if present in the installed version, else a minimal logback encoder config.
- **Never log**: passwords, tokens, refresh cookies, API keys, PANs, encryption keys, `Authorization` and `Cookie` headers, request bodies of auth endpoints.
- Log events that matter at `INFO`: login success/failure (no secret), authorization decision (`txId`, `cardId`, `status`, `responseCode`, `millis`), admin actions. Use SLF4J parameterized messages. No string concatenation, no `System.out`, no `printStackTrace`.
- A test asserts that logging an object that contains a PAN-shaped value uses the masking helper (implemented as a small `PanMasker` class): 13-19 digit sequences passing Luhn are masked in log patterns where the application controls the message.

## 9. Secrets management

- No secret is committed. `deploy/.env.example` documents every variable with **obviously fake** placeholders and generation commands (`openssl rand -base64 32`).
- `prod` profile: required variables have **no defaults**; configuration validation fails the startup with a clear message listing missing keys. `dev` and `test` profiles may use fixed non-secret development values committed in `application-dev.yml` and test resources, clearly labelled as such.
- Compose reads `deploy/.env`; `scripts/gen-secrets.sh` (optional) writes a fresh `.env` for local use.
- `.gitignore` excludes `.env`, `*.pem`, `*.key`, `target/`, `node_modules/`, `dist/`.
- A CI step (or pre-commit check inside `verify-all.sh`) greps the repository for patterns like `-----BEGIN`, `AKIA`, and a 32+ byte base64 assignment outside allowed files; failure blocks the build.

## 10. Bootstrap admin

`BootstrapAdminRunner` creates the first admin only when **no user exists** and `app.bootstrap.admin.email` and `app.bootstrap.admin.password` are both set (password must satisfy the policy or startup fails). It logs the **email only**. Compose `.env.example` sets a development admin (`admin@minicard.local`) with a placeholder password the operator must change. There is no default admin in code.

## 11. Rate limiting and abuse (COULD)

Bucket4j filter on `/authorize` per API client (default 100 req/s burst 200) and on `/auth/login` per IP (10/min). Responses `429 RATE_LIMITED` with `Retry-After`. Only after every MUST and SHOULD item is verified.

## 12. Dependency and supply-chain hygiene

- Versions pinned through the Boot BOM and `package-lock.json` (committed). `npm ci` in CI and Docker builds.
- Maven Enforcer: require Java 21, ban duplicate dependency versions, `dependencyConvergence`.
- CI runs `npm audit --audit-level=high` and (SHOULD) OWASP `dependency-check`; critical findings fail the build, others are recorded in `FINAL_REPORT.md`.
- Docker: pin base image major versions, run as non-root, no build tools in runtime images, `HEALTHCHECK` defined.

## 13. Security test checklist (enumerated again in `11`)

1. Endpoint x role matrix (parameterized).
2. Expired, malformed, wrong-signature, wrong-audience JWT rejected with `401`.
3. Login uniform error for unknown user, wrong password, disabled and locked accounts; lockout after 5; unlock after the window.
4. Refresh rotation; reuse detection revokes the family; parallel refresh yields exactly one success.
5. API key accepted only on `/authorize`; revoked key rejected; key never logged.
6. PAN never in any response (except one-time issue), log output, audit details, exception message or CSV.
7. Stored `pan_encrypted` differs for equal PANs (random IV); tamper with ciphertext or AAD fails decryption.
8. SQL-injection-style inputs in filters and sort parameters are rejected or inert.
9. CSV formula injection neutralized.
10. Unknown JSON properties (`cvv`) rejected.
11. Security headers present on API and nginx responses.
