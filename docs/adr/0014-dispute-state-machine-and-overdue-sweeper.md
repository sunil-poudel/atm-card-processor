# ADR 0014: Dispute State Machine, Ledger Credit Atomicity, and Concurrent Overdue Sweeper

## Status
Accepted

## Context
Prepaid card transactions may be disputed by cardholders within a configurable eligibility window (`app.disputes.max-age-days`). Disputes proceed through formal operational stages: opening, evidence gathering, review, and resolution. When a dispute is resolved in favor of the cardholder (`WON`), the original transaction amount must be credited back to the card balance. When a dispute is resolved against the cardholder (`LOST`) or when evidence submission deadlines expire, the dispute is closed without moving funds.

In high-throughput and multi-instance cloud deployments, disputes may experience concurrent operations (such as parallel resolution attempts or simultaneous sweeper runs across multiple instances). The system requires strict consistency guarantees:
1. No double-crediting or ledger corruption under concurrent resolutions.
2. Safe, non-blocking scheduled sweeps of overdue disputes across multiple worker instances.
3. Partial failure isolation so an error on one dispute does not abort the processing of others.

## Decision
1. **Explicit State Machine**:
   - `DisputeStatus` defines allowed transitions:
     - `OPENED` -> `EVIDENCE_REQUESTED`
     - `EVIDENCE_REQUESTED` -> `UNDER_REVIEW` (when evidence is provided within deadline) or `RESOLVED_LOST` (when evidence deadline expires)
     - `UNDER_REVIEW` -> `RESOLVED_WON` or `RESOLVED_LOST`
     - `RESOLVED_WON` and `RESOLVED_LOST` are terminal states.
   - Any invalid transition attempt throws `ConflictException(INVALID_STATE_TRANSITION)`.
   - Every transition appends an immutable `DisputeEvent` capturing old status, new status, actor, optional note, and timestamp.

2. **Atomic Dispute Credit via Ledger**:
   - When a dispute transitions to `RESOLVED_WON`, `LedgerService.recordDisputeCredit` is invoked within the **same transaction**.
   - If the card has status `CLOSED`, `resolve WON` fails with `409 INVALID_STATE_TRANSITION` (message `card is closed`) and the dispute remains `UNDER_REVIEW`. Cards in any other status (including `BLOCKED` or `ACTIVE`) receive the credit.
   - Idempotency and concurrency safety:
     - Optimistic locking (`@Version`) serializes concurrent API attempts.
     - Database unique constraint `ux_ledger_entries_dispute` on `ledger_entries(dispute_id)` guarantees at the database level that exactly one credit can ever exist per dispute.

3. **Deadline Sweeper with SKIP LOCKED and Per-Item Transactions**:
   - `DisputeDeadlineJob` queries overdue disputes (`status = 'EVIDENCE_REQUESTED' AND evidence_due_at < :now`) in batches of 100.
   - Each overdue dispute is processed by `DisputeItemProcessor` inside its own independent database transaction using `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - The query uses PostgreSQL native `FOR UPDATE SKIP LOCKED`. If multiple background instances run simultaneously, each instance locks distinct rows without waiting or deadlocking.
   - If processing fails for a specific dispute, the exception is logged and isolated; the loop proceeds to process remaining items in the batch.
   - Once marked `RESOLVED_LOST`, the dispute is terminal, making repeated runs completely idempotent.

## Consequences
- Full ledger integrity is preserved with zero risk of double credits.
- The deadline sweeper scales horizontally across multiple application replicas without cross-node locking contention.
- The UI derives allowed actions dynamically from dispute state and deadline (`getAllowedActions(Instant now)`), preventing redundant client-side state machine duplication.
