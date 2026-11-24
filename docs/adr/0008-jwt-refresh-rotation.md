# ADR 0008: JWT Access Token and Rotating Refresh Token in HttpOnly Cookie

## Status
Accepted

## Context
Staff dashboard access requires secure token handling that minimizes XSS and CSRF attack surfaces while supporting revocable user sessions.

## Decision
1. Issue short-lived (15 minutes) JWT access tokens signed with HMAC-SHA256 (`HS256`). Access tokens reside only in JavaScript memory and are sent via the standard `Authorization: Bearer <token>` header.
2. Issue high-entropy 256-bit refresh tokens stored as SHA-256 hashes in the database. Refresh tokens are transmitted exclusively in an `HttpOnly`, `SameSite=Strict`, path-scoped (`/api/v1/auth`) cookie (`mc_refresh`).
3. Enforce refresh token rotation on every `/api/v1/auth/refresh` request within a token family. If an already revoked/replaced token is presented, trigger reuse detection, revoke all tokens in the entire family immediately, audit `TOKEN_REUSE_DETECTED`, and reject the request with `401 REFRESH_TOKEN_INVALID`.
4. Cookie endpoints require the `X-Requested-With: XMLHttpRequest` custom header to prevent simple cross-site requests.

## Consequences
- XSS attackers cannot access the refresh token in `HttpOnly` cookie.
- Stolen access tokens are limited to 15 minutes of validity.
- Token theft and replay are detected through token family revocation.
