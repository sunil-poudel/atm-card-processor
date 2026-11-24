# ADR 0005: Pessimistic Row Locking on Card During Financial and State Mutations

## Status
Accepted

## Context
Payment card authorization, balance top-ups, dispute credits, and state transitions (activate, block, unblock, close) require strict serialization of balance and status modifications. Concurrent requests targeting the same card must not cause race conditions, negative balances, or illegal lifecycle states.

## Decision
1. Acquire a pessimistic write lock (`SELECT ... FOR UPDATE` via `CardRepository.findByIdForUpdate`) with a 5-second lock timeout hint whenever modifying card balance or transitioning card status.
2. In the authorization transaction, acquire the card lock immediately before checking card status, limits, and balance.
3. Lock timeouts map to HTTP `503 TEMPORARILY_UNAVAILABLE` with a `Retry-After: 1` header.
4. Non-financial metadata updates (e.g., limit updates, MCC list modifications) also acquire the row lock and verify optimistic locking (`@Version`) against lost updates.

## Consequences
- Guaranteed serialization and correctness of balance mutations across concurrent threads.
- Predictable behavior under concurrency; avoids phantom over-drafts.
- Contention is strictly localized to concurrent requests for the exact same card; different cards execute in parallel without mutual blocking.
