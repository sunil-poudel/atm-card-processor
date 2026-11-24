# ADR 0001: PostgreSQL and Flyway from Day One

## Status
Accepted

## Context
Payment processing requires precise locking semantics (`SELECT ... FOR UPDATE`), transaction isolation, strict constraint validation, time-zone sensitive timestamp math, and JSONB queries. Using H2 or in-memory databases in testing creates behavioral divergence from production, masking dialect quirks, locking bugs, and timezone edge cases.

## Decision
Use PostgreSQL 17 exclusively across all environments (production, development, and automated test execution). Manage all database schema migrations through Flyway migrations (`V1`..`V6`). Disallow H2 database in both production and test scopes.

## Consequences
- Schema behavior, locks, and constraints are 100% consistent across testing and production.
- Automated tests require PostgreSQL (executed via Testcontainers or native PostgreSQL test support).
- Zero drift between migration SQL and database engine capabilities.
