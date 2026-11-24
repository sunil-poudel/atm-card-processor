# ADR 0003: Modular Monolith Architecture

## Status
Accepted

## Context
ATM Card Processor is an issuer card-processing system. A distributed microservice architecture would introduce network latency, distributed transaction overhead, eventual consistency complexities, and unnecessary deployment friction for this domain size.

## Decision
Structure the backend as a modular monolith organized into strict feature packages (`card`, `ledger`, `transaction`, `fraud`, `dispute`, `report`, `identity`, `audit`, `common`). Enforce architectural boundaries with ArchUnit: web layers depend on services, domain depends on no external frameworks, and only `LedgerService` mutates card balances.

## Consequences
- High development velocity, simple local development, atomic database transactions.
- Zero distributed transaction failure modes.
- Modularity is strictly preserved and verified by automated architecture tests.
