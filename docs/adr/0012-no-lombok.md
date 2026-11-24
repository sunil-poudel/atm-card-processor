# ADR 0012: No Lombok; Explicit Domain Code and Java Records

## Status
Accepted

## Context
Project Lombok adds implicit byte-code generation that obscures constructors, accessors, `equals`/`hashCode` contracts, and domain invariant validation. It complicates IDE debugging, static analysis tools, and version upgrades (e.g. Java 21 / Spring Boot 4 compiler plugins).

## Decision
Disallow Lombok entirely. Use modern Java language features:
1. Java records for immutable data carriers (DTOs, configuration classes, query projections, value objects).
2. Explicit, intention-revealing constructors and methods on domain entities (e.g. `card.activate()`, `card.block()`), avoiding generic `@Setter` annotations that bypass domain invariants.
3. Explicit, proxy-safe `equals` and `hashCode` based solely on entity primary key `id`.

## Consequences
- Code is 100% transparent, debuggable, and standard Java with zero annotation processor friction.
- Domain invariants are protected by explicit encapsulated methods.
