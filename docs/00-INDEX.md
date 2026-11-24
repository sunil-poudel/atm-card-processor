# ATM Card Processor: Documentation Index

> Read this file first. It tells you what every document is for, in what order to read them, and which document wins when two documents seem to disagree.

## 1. What this project is

**ATM Card Processor** is a small but production-style **prepaid card issuer-processing system**. It models the core of what an issuer processor does:

1. **Issue** prepaid payment cards (fake, Luhn-valid numbers only) and manage their life cycle.
2. **Authorize** payments in real time (approve or decline with ISO-8583-style response codes), safely and exactly once.
3. **Score** every payment with configurable **fraud rules**.
4. Handle **chargeback / dispute** cases through a strict workflow, with money returned through an immutable ledger.
5. Provide **reports** and a back-office **Angular dashboard** for staff.

Stack: **Java 21 + Spring Boot 4.x + PostgreSQL + Flyway** (backend), **Angular (latest stable) + Angular Material** (frontend), **Docker Compose**, **GitHub Actions**.

All data is fictional. No real card numbers, no real money, no real network connections to card schemes or banks.

## 2. Reading order

| # | Document | Purpose | Read when |
|---|----------|---------|-----------|
| 00 | `00-INDEX.md` (this file) | Map, glossary, precedence rules, deltas from the earlier study guide | First |
| 01 | `01-PRODUCT-AND-SCOPE.md` | Goals, personas, scope (MUST / SHOULD / COULD / WON'T), simplifications vs the real world | Before anything else |
| 02 | `02-ARCHITECTURE.md` | Stack and version policy, repo layout, package structure, layering, cross-cutting design, ADR list | Before writing code |
| 03 | `03-DOMAIN-AND-DATABASE.md` | Entities, enums, state machines, **normative SQL schema** (Flyway migrations), conventions | Before M1 (migrations) |
| 04 | `04-API-SPEC.md` | Every endpoint: request, response, status codes, errors, headers | Before writing controllers |
| 05 | `05-BUSINESS-RULES.md` | Exact algorithms: authorization order, limits, fraud scoring, disputes, reports, crypto | Before writing services |
| 06 | `06-SECURITY.md` | Authentication, authorization matrix, token handling, API keys, secrets, logging redaction | Before M2 |
| 07 | `07-BACKEND-STANDARDS.md` | Coding standards, JPA rules, exception handling, testing strategy, build quality gates, anti-patterns | Always |
| 08 | `08-FRONTEND-SPEC.md` | Angular architecture, auth flow, every screen, UX states, testing | Before M9 |
| 09 | `09-DEVOPS-AND-QUALITY.md` | Docker, Compose, env var catalog, CI, observability, scripts, README requirements | Before M0 and M12 |
| 10 | `10-IMPLEMENTATION-PLAN.md` | Milestones M0 to M13 with tasks, acceptance criteria and verification commands | Drives the work |
| 11 | `11-TEST-PLAN.md` | Enumerated test cases and coverage targets | While writing tests |
| 12 | `12-LEARNING-GUIDE.md` | For the human owner: how to study the result, demo script, interview Q&A | After the build |

Also in the project root: `GOAL_PROMPT.md` (the autonomous-run prompt), `GEMINI.md` / `AGENTS.md` (short context file loaded automatically by the CLI agent).

## 3. Precedence rules (what wins in a conflict)

1. **`03-DOMAIN-AND-DATABASE.md`** wins for schema, column names, enum values and state machines.
2. **`05-BUSINESS-RULES.md`** wins for algorithms, thresholds, response codes and decision order.
3. **`04-API-SPEC.md`** wins for HTTP contract details (paths, payload shapes, status codes, error codes).
4. **`06-SECURITY.md`** wins for anything about authentication, authorization and secrets.
5. **`07-BACKEND-STANDARDS.md`** wins for code style and testing practice.
6. **`10-IMPLEMENTATION-PLAN.md`** wins for the order of work.
7. If a conflict still cannot be resolved: choose the safer, simpler interpretation, write the decision in `docs/adr/` (one short ADR), and continue. Never stop to ask unless the rules in `GOAL_PROMPT.md` section "Stop conditions" apply.

## 4. Glossary

| Term | Meaning |
|------|---------|
| **Issuer** | The bank or company that issues cards to cardholders. ATM Card Processor plays the issuer processor. |
| **Acquirer** | The merchant's bank side. Out of scope; the "API client" simulates the acquirer or point-of-sale system calling us. |
| **PAN** | Primary Account Number: the 16-digit card number. Always fake here. Stored only encrypted. |
| **BIN** | First 6 digits of the PAN; identifies the issuer. |
| **Luhn check** | Checksum every real PAN satisfies; catches typing mistakes. |
| **MCC** | Merchant Category Code, 4 digits (5411 = grocery, 6011 = ATM cash, 7995 = betting). |
| **Authorization** | A real-time yes or no to "can this card pay this amount at this merchant now?" |
| **Response code** | Two-digit outcome code borrowed from ISO 8583 (00 approved, 51 insufficient funds, ...). Catalog in `05`. |
| **Ledger** | Append-only list of every balance movement. Rows are never updated or deleted. |
| **Idempotency key** | Client-supplied unique key on a request so a retry can never charge twice. |
| **Velocity** | How many attempts happened in a recent time window. |
| **Risk score** | 0 to 100 number summarizing how suspicious a payment looks. |
| **Chargeback / dispute** | A cardholder contests an already-approved payment and asks for the money back. |
| **Minor units** | Smallest currency unit (paisa for NPR, cents for USD). All money is stored as integer minor units. |
| **Business zone** | `Asia/Kathmandu` (UTC+05:45). "Today" and "daily limit" are computed in this zone. |
| **User** | A human back-office staff member (ADMIN, ANALYST, DISPUTE_OFFICER) logging in through the dashboard. |
| **API client** | A machine caller (simulated POS / acquirer) that authenticates with an API key and may call only `POST /api/v1/authorize`. |
| **ADR** | Architecture Decision Record: a short file in `docs/adr/` explaining one decision and why. |

## 5. Deltas from the earlier study guide ("Mini Card Processor: Spring Boot Project Guide")

The owner studied an earlier, smaller guide. This documentation set **supersedes** it. Where they differ, **this set wins**. The differences, so nothing surprises the owner:

| Topic | Earlier study guide | This specification |
|-------|--------------------|--------------------|
| Database | H2 first, PostgreSQL later | **PostgreSQL + Flyway from day one**, Testcontainers in tests. No H2. |
| Idempotency key | Field in the JSON body | **`Idempotency-Key` HTTP header** (required on `POST /authorize`) |
| Authorize response | Included `remainingBalance` | **No balance** in the authorize response (an issuer does not reveal balances to a merchant) |
| Authorize response detail | Included decline reason, risk data | **Only** `transactionId, status, responseCode, responseMessage, authorizedAt`; reason, risk score and flag are staff-only (`GET /transactions/{id}`) |
| Limit exceeded code | Response code `05` | Response code **`61`** (exceeds amount limit). `05` ("do not honor") is not used. |
| Extra decline checks | Status, expiry, limits, balance, fraud | Adds **currency mismatch (12)** and **card-level MCC restriction (57)** |
| Velocity rule | "5 or more payments in 60 seconds" | Triggers when **5 or more prior attempts** already exist in the window, so the **6th** attempt is the first to trigger |
| Disputes | Optional bonus | **Required** (MUST) |
| Angular dashboard | Optional bonus | **Required** (MUST) |
| Security | Not covered | JWT + refresh rotation, roles, API keys, lockout, audit log, encrypted PAN |
| Tables | cards, transactions, fraud_rules, disputes | Adds users, refresh tokens, API clients, ledger entries, rule hits, dispute events, audit events, card MCC blocklist |
| Reports | Daily totals | Daily + range summary, charts, CSV export |

## 6. Conventions used in these documents

- **MUST / SHOULD / COULD / WON'T** follow RFC 2119 spirit. Scope levels are defined in `01`.
- `code` font is used for identifiers, paths, headers and literal values.
- Example amounts are in **minor units**: `50000` means 500.00.
- Times in APIs are ISO-8601 in UTC (`2026-10-20T09:15:30Z`). Calendar dates (`date=2026-10-20`) are interpreted in the business zone.
- "Agent" means the autonomous coding agent executing `GOAL_PROMPT.md`.
