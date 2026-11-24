# ADR 0007: PAN Cryptography with AES-256-GCM and HMAC-SHA-256 Blind Index

## Status
Accepted

## Context
PCI-DSS and security best practices dictate data minimization for primary account numbers (PAN). Full PANs must never be stored in plaintext or logged, while still allowing the system to verify uniqueness, identify cards by masked PAN / last-4 digits, and support key rotation.

## Decision
1. Encrypt PANs at rest using AES-256-GCM with a 12-byte random IV per record, a 128-bit authentication tag, and Associated Authenticated Data (AAD) set to the UTF-8 bytes of the card's UUID. This cryptographically prevents ciphertext-swapping between database rows.
2. Store ciphertext as URL-safe unpadded Base64: `base64url(iv || ciphertext || tag)`.
3. Support key rotation via key identifiers (`pan_key_id` stored on each card row). New encryptions use `currentKeyId`, while decryption looks up the designated key.
4. Maintain a blind index using HMAC-SHA-256 (`pan_hash`) with a distinct secret key to enforce database-level PAN uniqueness without decrypting database records.
5. Store cleartext truncated elements (`bin` first 6 digits, `last4` last 4 digits) for display as masked PAN (`990000******1234`).
6. Full PAN is exposed strictly once in the creation response when `app.cards.expose-pan-on-issue=true`; CVV and PIN are never generated or stored.

## Consequences
- Robust protection against database dumps, tampering, and row transposition.
- Fast exact-match lookup and uniqueness guarantees via `pan_hash` index.
- Clean key rotation pathway without bulk database re-encryption.
