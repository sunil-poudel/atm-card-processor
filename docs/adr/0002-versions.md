# ADR 0002: Resolved Dependency and Tool Versions

## Status
Accepted

## Context
ATM Card Processor requires deterministic, reproducible builds and tests across developer machines, containers, and CI. All non-Spring Boot BOM dependencies must have documented justifications.

## Resolved Versions

| Component / Dependency | Resolved Version | Justification / Notes |
|------------------------|------------------|-----------------------|
| Java | 21 (LTS) | Baseline runtime; supports modern records, pattern matching, and virtual threads. |
| Spring Boot | 4.1.1 | Latest stable release in the 4.1.x line (Spring Framework 7 baseline). |
| PostgreSQL Engine | 17 (`postgres:17-alpine`) | Production database image for Docker Compose and containers. |
| Spring Data JPA / Hibernate | 7.x (Boot managed) | Standard ORM persistence layer. |
| Flyway | 11.x (Boot managed) | Database migration runner with PostgreSQL support. |
| Spring Security | 7.x (Boot managed) | Security baseline, OAuth2 Resource Server, Nimbus JOSE JWT support. |
| springdoc-openapi | 2.8.6 | OpenAPI 3.1 documentation and Swagger UI generation for Spring WebMvc. |
| Testcontainers | 2.0.5 | Containerized integration testing with Spring Boot `@ServiceConnection`. |
| ArchUnit | 1.4.1 | Architecture rule enforcement for package boundaries, naming, and invariants. |
| Awaitility | 4.3.0 | Async assertions for scheduling and concurrency tests without `Thread.sleep`. |
| Spotless Plugin | 2.44.2 | Code formatting enforcement using Google Java Format. |
| JaCoCo Plugin | 0.8.13 | Code coverage analysis and quality gate enforcement. |
| Maven Enforcer Plugin | 3.5.0 | Build environment constraints (Java >= 21, Maven >= 3.9, no duplicate dependencies). |
| Angular | 22.2.3 | Latest stable Angular release (signals, standalone components). |
| Node.js | v20.20.2 | Active LTS runtime satisfying Angular 22 engine requirements. |
| Playwright | 1.64.1 | End-to-end browser testing for the Angular dashboard. |

## Consequences
All dependencies adhere strictly to non-negotiable rules (no Lombok, no third-party JWT, no snapshot versions).
