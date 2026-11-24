# ADR 0010: Immutable Append-Only Ledger with Cached Card Balance Projection

## Status
Accepted

## Context
Financial card processor transactions require absolute auditability and non-repudiation. Balance changes cannot be ephemeral column updates; every movement of funds must be recorded with an immutable, append-only audit trail.

## Decision
1. All financial movements (top-ups, purchases, dispute credits) are recorded as immutable rows in `ledger_entries`.
2. Each ledger entry contains a signed minor unit amount (`PURCHASE` negative, `TOPUP`/`DISPUTE_CREDIT` positive), the running balance after entry (`balance_after_minor`), origin references (`transaction_id` or `dispute_id`), actor identity, and creation timestamp.
3. Database `CHECK` constraints strictly enforce sign conventions and mutually exclusive foreign references according to entry type.
4. `Card.balanceMinor` serves as a performance projection of the current balance and is updated in the same transaction.
5. In accordance with architecture non-negotiables, `LedgerService` is the exclusive component permitted to modify `Card.balanceMinor`. This constraint is statically verified by ArchUnit tests.

## Consequences
- Full transaction history is permanently preserved and tamper-evident.
- Fast balance checks during authorization without scanning historical ledger rows.
- Invariant property holds at all times: the sum of all ledger entries for a card equals `card.balanceMinor`.
