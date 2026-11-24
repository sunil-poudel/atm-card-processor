# 12. Learning Guide (for the human owner)

This file is for **you**, not for the agent. The agent builds the project; this guide turns it into knowledge you own, so you can walk into an office in Pokhara and talk about it honestly and confidently.

## 1. Honest framing first

You will have used an AI agent to build most of this. That is normal in 2026, and good companies know it. What they test is whether **you understand what you show**. So:

- Say it plainly: "I built this with AI assistance, then studied it until I could explain and change it." That is true and respectable. Never claim you typed every line.
- Be able to **change something live**: a response code, a limit rule, a new fraud rule. That one skill proves understanding better than any story.
- If you do not know something, say "I have not worked with that yet, but here is how I would find out." Interviewers like that more than bluffing.

## 2. Study plan (8 to 10 days, about 3 hours a day)

| Day | Focus | Do this |
|-----|-------|---------|
| 1 | Domain and big picture | Read `01`, `02`, glossary in `00`. Run the stack with Docker Compose. Click every screen. Draw the payment flow on paper from memory |
| 2 | Spring Boot basics through this code | Read `docs/CODE-TOUR.md` sections 1 to 3. Open `CardController`, `CardService`, `Card`. Learn: controller -> service -> repository, DTOs, validation, constructor injection |
| 3 | The core: authorization | Read `05` §2 and `AuthorizationService`. Step through it in the debugger with one approved and one declined request. Read the decision-matrix test |
| 4 | Money safety | Read ledger code, `LedgerService`, the concurrency test. Explain in your own words: why `long`, why row lock, why immutable ledger |
| 5 | Idempotency and security | Read idempotency code and tests. Read `06` §3 (JWT, refresh rotation). Try login, refresh, logout with `curl` |
| 6 | Fraud and disputes | Read evaluators, `RiskEngine`, dispute state machine. Add one **new fraud rule type** yourself (see exercises) |
| 7 | Database and tests | Read the Flyway migrations, run a few SQL queries in `psql`. Read a unit test, a slice test, an integration test. Break a rule on purpose and watch a test fail |
| 8 | Angular side | Read `AuthStore`, interceptor, one feature (cards). Change a label, a column, a filter and see it update |
| 9 | Rehearsal | Do the demo script (§4) out loud. Answer the Q&A (§5) without looking. Fix gaps |
| 10 | Buffer + visit prep | Final run of `scripts/verify-all.sh`, pick clothes, print a one-page summary (§7), prepare your questions for them |

## 3. Exercises that prove you understand (do at least four)

1. **Add a response code path**: make transactions at MCC `6011` (ATM) above 20,000 decline with `61`. Find where the order of checks lives, add a test first, make it pass.
2. **New fraud rule type** `NIGHT_TIME` (flag transactions between 01:00 and 04:00 Kathmandu time): one enum value, one evaluator class, validation, tests. You will touch the strategy pattern.
3. **Change the velocity threshold** through the UI and watch the 6th rapid payment decline; then explain why the *6th*, not the 5th.
4. **Break idempotency on purpose** (remove the unique index in a scratch branch) and watch the concurrency test fail. Restore it.
5. **Add a field** `cardNickname` to the card: migration, entity, DTO, OpenAPI, Angular form, tests.
6. **Add an endpoint** `GET /cards/{id}/summary` returning approved volume for the last 30 days.
7. **Explain a bug**: in a scratch branch change `>` to `>=` in the per-transaction limit check, run the tests, read which tests fail and why.
8. **Read the audit log** after performing a dispute lifecycle and map each row to the code that wrote it.

## 4. Demo script (5 to 7 minutes, on your laptop, offline-safe)

Prepare: stack running, demo data loaded, browser tabs open, a backup screen recording in case Wi-Fi or Docker misbehaves.

1. **30 s, the pitch**: "This is a small issuer card-processing system: it issues prepaid cards, authorizes payments in real time, scores fraud, handles chargebacks, and reports. I built it with Spring Boot, PostgreSQL and Angular to learn how card platforms behave."
2. **60 s, cards**: show the card list, issue a card, activate, top up, show the ledger.
3. **90 s, authorization**: simulator approves a payment; replay the same request to show idempotency; show a decline for insufficient funds; mention the response codes are ISO-8583 style.
4. **60 s, fraud**: fire the rapid-fire preset; show the 6th decline and the rule hit; show that rules are editable.
5. **60 s, disputes**: open a dispute on an approved payment, move it through, resolve as won, show the ledger credit.
6. **45 s, reports and audit**: dashboard, CSV export, audit log.
7. **60 s, engineering**: open the code at `AuthorizationService`, point at the lock and the idempotency; show tests green and coverage; show the CI file and README.
8. Close: "The part I enjoyed most was making money handling safe under concurrency. I would love to learn how your team does this in production."

## 5. Interview Q&A topics (the agent writes full answers in `docs/INTERVIEW-QA.md`; rehearse them)

Spring Boot and Java: how auto-configuration works; what `@Transactional` really does (proxies, rollback rules, self-invocation); constructor injection vs field injection; `ddl-auto=validate` and why Flyway; lazy loading and N+1; why `open-in-view=false`; difference between `@WebMvcTest` and `@SpringBootTest`; what Testcontainers solves; records vs entities; why no Lombok here.

Money and correctness: why `long` minor units; what a ledger is and why immutable; how double-spend is prevented (row lock vs optimistic lock, trade-offs); what idempotency is and how the key, scope and fingerprint work; what happens on a retry after a timeout; what would change with holds and settlement in the real world.

Security: how JWT and refresh rotation work; why the access token is kept in memory; what reuse detection protects against; why API keys can use SHA-256 but passwords need BCrypt; why the PAN is encrypted with AAD and a blind index; what you would do for real key management (KMS/HSM); how you avoid logging secrets.

Architecture: why a modular monolith; how ArchUnit enforces boundaries; strategy pattern for fraud rules and how to add a rule; state machines for cards and disputes; how the deadline job behaves with multiple instances (`SKIP LOCKED`); how you would scale out (stateless app, DB bottleneck, read replicas for reports, caching rules).

Frontend: signals vs RxJS; interceptors and single-flight refresh; why generated API types; how you avoid duplicating business rules in the UI (`allowedActions`).

Process: how you tested concurrency; what coverage means and its limits; what you would improve next (rate limiting, outbox/events, real settlement flow, observability dashboards).

## 6. Questions to ask them (shows genuine interest)

- "How does your team handle idempotency and retries on payment APIs?"
- "Do you run Spring Boot on virtual threads or classic threads, and how do you size database pools?"
- "How do you test concurrency-sensitive code?"
- "What does a typical first month look like for an intern on the team?"
- "Is there a small task I could try or a codebase area you would suggest I study first?"

## 7. Office-visit checklist

- A one-page printed summary: project name, one-paragraph description, three bullets of what you are proud of, GitHub link, your phone and email. Your CV (one page).
- Laptop charged, stack pre-started, a screen recording backup, charger.
- Visit the three offices at times when people are likely free (mid-morning, mid-week), be brief and polite, ask for the right person (HR or tech lead), offer to leave the one-pager and to email a link.
- Follow up by email within 24 hours with the repo link and a thank-you.
- Be honest about being a beginner who learns fast, and about AI assistance.

## 8. After the agent finishes: your first reading order

`README.md` -> `docs/CODE-TOUR.md` -> `AuthorizationService` and its tests -> `LedgerService` -> `FraudEngine` -> `DisputeService` -> `SecurityConfig` -> frontend `AuthStore` and one feature. Keep `docs/FINAL_REPORT.md` open; it lists known limitations and things to double check manually.
