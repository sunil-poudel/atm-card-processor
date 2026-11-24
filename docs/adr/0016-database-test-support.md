# ADR 0016: Dual Test Execution Support (Testcontainers & Native PostgreSQL)

## Status
Accepted

## Context
Integration tests require real PostgreSQL (no H2). In CI and standard environments with Docker installed, Testcontainers PostgreSQL is the standard mechanism with Spring Boot `@ServiceConnection`. However, in restricted or rootless environments where the Docker daemon is unavailable, integration tests need an automatic fallback to native PostgreSQL (`initdb` / local process) without altering test behavior or sacrificing PostgreSQL fidelity.

## Decision
Implement a shared `PostgresSupport` test harness:
1. Detect if Docker is available.
2. If Docker is available, spin up a singleton Testcontainers `PostgreSQLContainer("postgres:17-alpine")`.
3. If Docker is unavailable, automatically initialize and start a temporary local PostgreSQL 16/17 server cluster or attach to `POSTGRES_PORT` / `POSTGRES_HOST`.
4. Expose the dynamic database URL, username, and password into Spring's `DynamicPropertyRegistry`.
5. Run identical Flyway migrations, assertions, and transactions against the real PostgreSQL database.

## Consequences
- Test suite executes identically on machines with Docker and machines without Docker.
- Full parity with PostgreSQL 17 features (Flyway migrations, row locks, JSONB, constraints, timezones).
- Zero compromise on testing quality or schema validation.
