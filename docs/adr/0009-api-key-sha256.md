# ADR 0009: API Keys Stored as SHA-256 Hashes with Prefix Lookup

## Status
Accepted

## Context
Machine callers (POS and acquirer simulators calling `/api/v1/authorize`) authenticate with API keys. Because authorization is high-throughput, key verification must be fast and resistant to timing attacks while safeguarding the key against database leaks.

## Decision
Format API keys as `mck_<prefix>_<secret>`:
- `prefix`: 8 random alphanumeric characters `[a-z0-9]` used for database index lookup.
- `secret`: 43 characters representing URL-safe Base64 of 32 random bytes (256 bits of entropy).
Store only the `key_prefix` and `SHA-256(fullKey)` hex string in the database. When verifying an incoming `X-API-Key`:
1. Extract prefix and query active client.
2. Hash incoming full key with SHA-256.
3. Compare hashes using constant-time `MessageDigest.isEqual`.
Because 256 bits of cryptographically secure random entropy are present in the key, a fast cryptographic hash (SHA-256) is completely secure against dictionary and brute-force attacks, avoiding the heavy CPU cost of BCrypt/Argon2 during real-time payment authorization.

## Consequences
- Fast sub-millisecond lookup and verification for real-time payment authorization.
- Zero raw keys stored in the database.
- Database compromises do not compromise unhashed API keys.
