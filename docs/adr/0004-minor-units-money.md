# ADR 0004: Money Represented as Long Minor Units

## Status
Accepted

## Context
Floating point types (`float`, `double`) suffer from precision and rounding issues (e.g. `0.1 + 0.2 != 0.3`). `BigDecimal` incurs object allocation overhead, verbose API ergonomics, and risk of incorrect rounding modes when misused in math calculations.

## Decision
Represent all monetary amounts as primitive `long` minor units (paisa for NPR, cents for USD) across domain models, services, DTOs, and database columns (suffix `_minor`, PostgreSQL `BIGINT`). Use exact arithmetic (`Math.addExact`, `Math.subtractExact`). Prohibit `double`, `float`, and `BigDecimal` in all domain and service logic. Format to decimal representation only at boundary edges (CSV exports, frontend display).

## Consequences
- Guaranteed mathematical exactness with zero rounding drift.
- Simple, safe integer arithmetic with overflow detection.
- Fast database comparisons and indexing.
