# 07. Backend Engineering Standards

These rules apply to every line of backend code and test. They are what makes the codebase look like it was written by one careful senior engineer. The agent MUST re-read this file before each milestone that touches the backend.

## 1. Language and style

- Java 21. Use `record` for DTOs, value objects and configuration properties; `sealed interface` + pattern-matching `switch` where it models a closed set (for example `AuthorizationDecision`, `Approved` / `Declined`); `Optional` only as a return type, never as a field or parameter.
- Format with **Spotless + google-java-format** (AOSP style not required; keep default Google style). `./mvnw spotless:apply` formats; `./mvnw verify` fails when unformatted.
- Imports: no wildcard imports, no unused imports.
- Naming: classes `UpperCamelCase`, methods and fields `lowerCamelCase`, constants `UPPER_SNAKE_CASE`, packages lowercase. Boolean getters read like questions (`isExpired`). Test classes end in `Test` (unit) or `IT` (integration).
- **Forbidden names**: `*Util`, `*Utils`, `*Helper`, `*Manager`, `*Impl` for single implementations (inject the class itself or extract an interface only when there are two implementations or a real seam), `Data`, `Info`, `Handler` without a clear role.
- No Lombok (ADR 0012). Entities have explicit constructors (a protected no-arg for JPA and a public/static factory with required fields) and only the accessors needed.
- Method length: aim for 5-25 lines; above 40 needs a reason. Class length: below about 300 lines. Cyclomatic complexity stays low; extract well-named private methods or small collaborators.
- No commented-out code. No `TODO`/`FIXME` in committed code (open an item in `BLOCKERS.md` or `docs/FINAL_REPORT.md` "Known limitations" instead).
- Comments explain **why**, never what. Public types in `domain`, `service` and security classes get short Javadoc stating the invariant or contract. Do not narrate obvious code.
- Constants live next to their use; no `Constants` dumping-ground class.

## 2. Package and layering rules

Follow `02` §4. Additional rules:

- Constructor injection only, `final` fields, single constructor (no `@Autowired`).
- No field injection, no static singletons, no service locators.
- Beans never depend on `ApplicationContext` for business logic.
- Controllers are thin: map request to a command/DTO call, call one service method, map the result. No business logic, no repository access, no transactions.
- Services own transactions and orchestration. Domain objects own invariants (an entity protects its own state transitions: `card.block(reason, now)` rather than `setStatus`).
- Entities expose **no public setters for state that has rules** (status, balance, counters). Use intention-revealing methods.
- Cross-feature calls go through the other feature's `service` classes. Never import another feature's entity into your repository queries unless via ids.
- Mapping between entities and DTOs: explicit static `from(...)` methods on response records or small `*Mapper` classes without frameworks. **No MapStruct, no ModelMapper** (keeps behaviour explicit and reviewable).

## 3. Configuration

- `@ConfigurationProperties(prefix = "app")` records, nested records per area (`security`, `crypto`, `cards`, `fraud`, `disputes`, `bootstrap`, `business`), `@Validated`, constraints (`@NotBlank`, `@Min`, custom `@Base64Key(bytes=32)`).
- No `@Value` scattered through code except trivial framework settings.
- `application.yml` holds safe defaults; `application-dev.yml`, `application-prod.yml`, `application-test.yml` hold profile specifics. Secrets only from environment in `prod`.
- Required production keys fail startup via validation, and a startup test proves it.

## 4. Persistence rules (JPA/Hibernate)

1. `spring.jpa.hibernate.ddl-auto=validate`; schema owned by Flyway; `spring.jpa.open-in-view=false`.
2. Relationships default to `LAZY`. `@ManyToOne(fetch = LAZY)`; collections only where the aggregate truly owns them (`Dispute.events` is **not** mapped as a collection on the entity; query `DisputeEventRepository` instead, keeping aggregates small).
3. Prevent N+1: list queries use projections (`interface`/record projections or `select new ...`) or `JOIN FETCH` / `@EntityGraph` for the needed associations. A **query-count test** (Hibernate `Statistics` or a datasource proxy) asserts a bounded number of statements for each list endpoint regardless of page size.
4. Pagination is done in the database. Never load all rows then slice.
5. Dynamic filters (transactions, cards, disputes, audit) use **Spring Data JPA Specifications** or QueryDSL-free `Specification` builders with whitelisted sort properties. Each specification is unit-tested.
6. Native SQL is allowed for reports (documented in `05` §6) with bind parameters; each such query has an integration test at day boundaries.
7. Locking: `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(jakarta.persistence.lock.timeout = 3000)` on `CardRepository.findByIdForUpdate`. Lock timeout maps to `503 TEMPORARILY_UNAVAILABLE`.
8. `equals`/`hashCode` on entities: based on the id only once assigned (the id is assigned at construction because ids are application-generated, so entities are never "transient without id"). `toString` never prints secrets or lazy associations.
9. Auditing fields (`createdAt`, `updatedAt`) are set from the injected `Clock` in a `@PrePersist`/`@PreUpdate` base class or explicit methods; do not use `@CreatedDate` with the system clock.
10. Repositories contain queries only; no business rules.
11. Entities and DTOs are never mixed: a controller never receives or returns an entity.

## 5. Error handling

- One sealed hierarchy: `ApiException` (abstract, carries `ErrorCode`, HTTP status, safe message) with subclasses `NotFoundException`, `ConflictException`, `UnprocessableException`, `BadRequestException`, `UnauthenticatedException`, `ForbiddenException`. `ErrorCode` is an enum mirroring the catalog in `04` §1.5 (one place, with status and default title).
- Throw the most specific exception at the point of knowledge; never catch `Exception` to swallow; never return `null` for "not found" from a service (use `orElseThrow`).
- `GlobalExceptionHandler` maps: `ApiException`; `MethodArgumentNotValidException`, `ConstraintViolationException`, `HandlerMethodValidationException`, `MethodArgumentTypeMismatchException` -> `VALIDATION_FAILED`; `HttpMessageNotReadableException`, `HttpMediaTypeNotSupportedException` -> `MALFORMED_REQUEST`; `OptimisticLockingFailureException` -> `OPTIMISTIC_LOCK_FAILURE`; `PessimisticLockingFailureException`, `CannotAcquireLockException`, `QueryTimeoutException` -> `TEMPORARILY_UNAVAILABLE`; `NoResourceFoundException` -> `RESOURCE_NOT_FOUND`; `AccessDeniedException` -> `FORBIDDEN`; anything else -> `INTERNAL_ERROR` (log at ERROR with the request id; response contains no detail). Verify exact exception class names against the installed Spring version.
- Every mapped response includes `code`, `requestId`, `timestamp`.
- Declines are **not exceptions**. `AuthorizationService` returns an `AuthorizationDecision`; the transaction row is persisted for approved and declined outcomes alike.

## 6. Transactions

- `@Transactional` on service methods only. Read-only queries use `readOnly = true`.
- Keep transactions short and free of remote calls (there are none).
- The audit insert is in the same transaction as the business change.
- Use `Propagation.REQUIRES_NEW` only for the dispute-deadline per-item processing, in a **separate bean** (self-invocation does not trigger proxies).
- Idempotency race handling: catch `DataIntegrityViolationException` on the unique `(scope, key)` insert in a **new transaction boundary** and reload the winner (see `05` §2.2). Never rely on "check then insert" alone.

## 7. Time, money, randomness

- `Clock` is injected. `BusinessCalendar` offers `startOfDay(LocalDate)`, `localDate(Instant)`, `dayRange(Instant)` using `app.business.zone` (default `Asia/Kathmandu`).
- Money: `long` minor units everywhere; `Math.addExact`/`subtractExact`; no `double`/`float`/`BigDecimal` in domain code. Conversion to a decimal string only in `MoneyFormat` at the edges (CSV).
- `SecureRandom` is a singleton bean for security randomness; never `java.util.Random`/`Math.random` for secrets, tokens, PANs. Test code may inject a seeded generator through a constructor.
- UUIDs: time-ordered (UUIDv7 style). Use Hibernate's time-based generator or a small `UuidV7` factory class in `common`; generate in the application.

## 8. API layer rules

- DTOs are records with Bean Validation annotations and OpenAPI `@Schema`. Separate request and response records per endpoint; no reuse of one record for both directions.
- `@Validated` controllers; `@Valid @RequestBody`.
- Controllers return `ResponseEntity` only when headers/status need control (201 + `Location`, replay header, CSV); otherwise return the DTO and `@ResponseStatus`.
- Pagination through a small `PageRequestFactory` that applies size cap 100, default sort, and a per-endpoint whitelist.
- Version in the path (`/api/v1`). No breaking changes without a new version.
- Use proper HTTP semantics: `201` + `Location` for creates, `204` for no body, `409`/`422` per catalog, `PATCH` for partial updates.

## 9. Logging

See `06` §8. Use `Logger` per class via SLF4J (`private static final Logger log = LoggerFactory.getLogger(X.class);`). Log at decision points only; avoid per-row logging in loops; include identifiers, not payloads.

## 10. Testing strategy (detail and case lists in `11`)

| Level | Tooling | Scope |
|-------|---------|-------|
| Unit | JUnit 5, AssertJ, Mockito | Domain logic, pure services with fakes, evaluators, Luhn, crypto, calendar, state machines |
| Slice | `@WebMvcTest`, `@DataJpaTest` + Testcontainers | Controllers (validation, status codes, security), repositories and Specifications |
| Integration | `@SpringBootTest` + Testcontainers PostgreSQL, `MockMvc`/`RestTestClient` (use whatever the installed Spring offers; verify) | Whole flows, concurrency, idempotency, reports, security matrix |
| Architecture | ArchUnit | Layering, naming, no field injection, no `Instant.now()`, no `System.out`, ledger-only balance mutation |
| Contract | OpenAPI snapshot test | `docs/openapi.json` equals generated spec |
| Mutation (COULD) | Pitest on `*.domain.*` | Target >= 70% mutation score |

Rules:
- **Test names describe behaviour**: `declinesWithCode51WhenBalanceIsInsufficient()`. Use `@DisplayName` only when it adds value. Structure Given/When/Then with blank lines, not comments.
- One logical assertion focus per test; AssertJ fluent assertions; no `assertTrue(x.equals(y))`.
- No `Thread.sleep`; use `Awaitility` or the injected adjustable `Clock`.
- No test depends on another test's data or order. Each integration test class cleans state (truncate via `@Sql` or a test helper) or uses unique data.
- Test data built with explicit **builders/factories** in `src/test/java/.../support` (`CardFixtures`, `TestClock`, `TestUsers`), not copy-pasted setup.
- Testcontainers: **singleton container** per JVM (static, started once, reused across classes) with `@ServiceConnection` for speed. Do not start a container per class.
- Concurrency tests use `ExecutorService` + `CountDownLatch` start gate; assert invariants, not timing.
- Mocks only for collaborators at boundaries; never mock the class under test, never mock value objects or the database in integration tests.
- Flaky tests are bugs: fix the cause, never retry blindly or `@Disabled`.

## 11. Build quality gates (`./mvnw -f backend verify` MUST enforce)

1. **Spotless check** (format).
2. **Compiler**: `-Xlint:all` with `-Werror` for the main source set where feasible (if a third-party warning blocks it, scope with `@SuppressWarnings` and a comment; do not drop `-Werror` globally without an ADR).
3. **Maven Enforcer**: Java >= 21, Maven >= 3.9, `dependencyConvergence`, `banDuplicatePomDependencyVersions`.
4. **Unit tests** (Surefire) then **integration tests** (Failsafe, `*IT`).
5. **ArchUnit** rules (run as normal tests).
6. **JaCoCo** report and `check` rule: line coverage >= **85%** and branch coverage >= **75%** at bundle level, and **>= 95% line / 90% branch** for packages `*.domain` and `transaction.service`, `ledger.service`, `fraud.service`, `dispute.service`. Exclusions limited to the application class, configuration records, generated code, and DTO records (documented in the pom).
7. OpenAPI snapshot test.

## 12. Dependency policy

Add a dependency only when it earns its place (document each non-Boot dependency in `docs/adr/0002-versions.md` with a one-line reason). Allowed beyond Boot starters: springdoc-openapi, ArchUnit, Awaitility, Testcontainers, Spotless (plugin), JaCoCo (plugin), Enforcer (plugin), Pitest (COULD), Bucket4j (COULD), and the structured-logging encoder if Boot lacks built-in support. Not allowed: Lombok, MapStruct, Apache Commons for trivial things, Guava, Hutool, any second JSON library, any JWT library other than Nimbus (through Spring Security).

## 13. Anti-patterns (reject on sight)

| Anti-pattern | Do this instead |
|--------------|-----------------|
| `double`/`BigDecimal` money | `long` minor units |
| `@Data`/Lombok everywhere | explicit code, records |
| Returning entities from controllers | DTO records |
| `catch (Exception e) { log... }` swallowing | specific exceptions, rethrow as `ApiException` |
| Business logic in controllers or repositories | services and domain |
| Setters on state-machine fields | intention methods |
| `Instant.now()` / `LocalDate.now()` | injected `Clock` |
| Field injection / `@Autowired` on fields | constructor injection |
| `findAll()` then filter in memory | database filtering and paging |
| `Thread.sleep` in tests | Awaitility / fake clock |
| Hard-coded secrets, keys, URLs | typed configuration |
| `System.out.println` | SLF4J |
| Giant `God` service | split by responsibility |
| "Utility" static grab-bags | small named collaborators |
| Ignoring a failing test | fix cause |
| Copy-pasted test setup | fixtures/builders |
| `@Transactional` on controllers | on services |
| Silent `Optional.get()` | `orElseThrow(...)` with domain exception |
| Magic numbers/strings | named constants or configuration |

## 14. Review checklist (the agent applies it before every commit)

1. Does it follow `07` style and `02` layering? Does `./mvnw verify` pass locally?
2. Does every new behaviour have a test that would fail without it, including the unhappy path?
3. Are all inputs validated at the boundary and re-checked in the domain?
4. Is money handled with `long` and exact arithmetic? Is time taken from `Clock`?
5. Could this leak a secret/PAN to a log, response, or exception message?
6. Is the transaction boundary right? Are locks taken in a consistent order (card row only; never lock two cards)?
7. Is the OpenAPI annotation complete and `docs/openapi.json` regenerated?
8. Is the audit event written for privileged state changes?
9. Are names honest and are there any leftover TODOs, debug prints, dead code, unused imports?
10. Is the commit small, with a Conventional Commit message that says why?
