# 08. Frontend Specification (Angular back-office dashboard)

A staff-facing single-page app that looks like a real fintech back office: calm, dense but readable, consistent. It talks only to the API in `04`. No feature may exist in the UI that the API does not support, and no API capability marked MUST may be missing from the UI.

## 1. Stack and tooling

| Item | Choice |
|------|--------|
| Framework | Latest stable Angular (verify with `npm view @angular/core version`); standalone components only (no NgModules); the new control flow syntax (`@if`, `@for`, `@switch`); `OnPush` change detection everywhere (default in recent versions, still state it explicitly where the generator does not) |
| Language | TypeScript `strict: true`, `noImplicitOverride`, `noPropertyAccessFromIndexSignature`, `noImplicitReturns`; no `any` (lint error) |
| State | Signals for local/UI state and derived values (`computed`), RxJS for HTTP streams. `toSignal`/`toObservable` where they simplify. **No NgRx** (ADR 0013) |
| UI | Angular Material + CDK (use the version matching Angular), light theme with a custom palette; optional dark mode (COULD) |
| Charts | Chart.js (through a thin typed wrapper component; no heavy wrapper library) |
| API types | Generated from `docs/openapi.json` with `openapi-typescript` into `src/app/core/api/schema.d.ts`; a typed `ApiClient` layer on top. Regenerate through `npm run api:types`. CI checks the generated file is up to date. |
| Lint/format | `angular-eslint` (recommended + template rules, accessibility rules) and Prettier; `npm run lint` and `npm run format:check` are gates |
| Unit tests | The default runner of the installed Angular (Vitest in recent versions, Karma/Jasmine in older; use what `ng new` provides) with Angular Testing Library style (query by role/text) where practical |
| E2E | Playwright (`frontend/e2e`), runs against the Compose stack, includes axe accessibility checks (`@axe-core/playwright`) |
| Package manager | npm with a committed `package-lock.json` |

Project creation: `npx @angular/cli@latest new frontend --routing --style=scss --ssr=false --skip-git` inside the repo root (adapt flags to the installed CLI), then add Material (`ng add @angular/material`). Remove unused demo code. Set `prefix` to `mc`.

## 2. Folder structure

```
frontend/src/app
├── app.config.ts  app.routes.ts  app.ts
├── core
│   ├── api            # schema.d.ts (generated), api-client.ts, api-error.ts (problem details model)
│   ├── auth           # auth.service.ts, auth.store.ts, auth.interceptor.ts, auth.guard.ts, role.guard.ts, session-restore
│   ├── http           # request-id.interceptor.ts, error.interceptor.ts, loading tracking
│   ├── config         # app-config.ts (API base path '/api/v1'), tokens
│   └── notify         # notification.service.ts (MatSnackBar wrapper)
├── shared
│   ├── ui             # page-header, empty-state, error-state, loading-skeleton, confirm-dialog, status-chip, money (pipe), masked-pan, copy-button, kpi-card, chart wrapper
│   ├── pipes          # money.pipe.ts, relative-time.pipe.ts
│   ├── forms          # validators (minor-units, mcc, uuid), form-error component
│   └── (no generic "utils" folder: helpers live next to their single consumer)
├── layout             # shell (sidenav + toolbar), nav items filtered by role
└── features
    ├── auth/login
    ├── dashboard
    ├── cards          # list, detail (tabs), issue dialog/page, top-up dialog, limits dialog, mcc dialog
    ├── transactions   # list with filters, detail drawer/page, simulator
    ├── fraud-rules    # list, editor dialog
    ├── disputes       # list, detail (timeline + actions), open-dispute dialog
    ├── reports        # daily, range summary, CSV export
    ├── users          # ADMIN
    ├── api-clients    # ADMIN
    └── audit          # ADMIN
```

Each feature has `*.routes.ts` (lazy loaded with `loadChildren`/`loadComponent`), components, one `*-api.service.ts` (HTTP only) and optional `*.store.ts` (signals) when state is shared across components of the feature.

## 3. Authentication flow in the SPA

1. **Login page** posts credentials. On success the access token is kept in a **signal inside `AuthStore` (memory only)**. Never `localStorage`/`sessionStorage`.
2. **Session restore on page load**: an `APP_INITIALIZER`-style provider (`provideAppInitializer`) calls `POST /auth/refresh` (cookie + `X-Requested-With: XMLHttpRequest`). Success populates the store and the app starts authenticated; `401` leaves the user anonymous (no error toast).
3. **Auth interceptor** (functional): adds `Authorization: Bearer` to `/api/v1/**` requests except the auth endpoints; on `401` from a non-auth endpoint, performs a **single shared refresh** (one in-flight refresh promise/observable; other requests wait), then retries the original once; if refresh fails, clears the store and navigates to `/login?returnUrl=...` with a "session expired" message.
4. **Guards**: `authGuard` (must be logged in), `roleGuard(['ADMIN', ...])` using route `data.roles`. Unauthorized route access shows a friendly "403 Not permitted" page, not a blank screen.
5. **Logout** calls `POST /auth/logout`, clears the store, navigates to `/login`.
6. The nav and buttons are **filtered by role** for usability; the server remains the authority.

## 4. API client and error handling

- `ApiClient` wraps `HttpClient`, builds URLs from `API_BASE='/api/v1'`, and returns typed observables using the generated schema types.
- `error.interceptor` converts `HttpErrorResponse` with `application/problem+json` into an `ApiError` (`code`, `status`, `detail`, `requestId`, `errors[]`). Components switch on `code`.
- Global errors: network failure -> "Cannot reach the server" banner; `500/503` -> toast with the request id ("Reference: ..."); `403` -> toast; `401` handled by the auth interceptor.
- Validation errors (`400 VALIDATION_FAILED`) map `errors[].field` back to form controls through a helper `applyServerErrors(form, apiError)`.
- Every mutating request disables its submit button while in flight (prevents double submit) and shows a progress indicator. The payment simulator generates a **fresh `Idempotency-Key` per user intent** (UUID) and reuses it on automatic retries.
- Money formatting: `money` pipe formats minor units to a localized decimal with the currency code (`Rs 500.00` style for NPR is NOT required; use `NPR 500.00` / `USD 12.34` consistently). Money inputs are entered in **major units** with up to 2 decimals and converted to minor units by a single function with tests (rejects more than 2 decimals, negatives, NaN, and values above the maximum).
- Dates: stored as UTC instants; displayed in the **business zone** (`Asia/Kathmandu`) using `Intl.DateTimeFormat` with explicit `timeZone`, with the zone shown in the report headers. Date pickers for reports represent calendar dates in the business zone.

## 5. Layout and navigation

- Shell: left `mat-sidenav` (collapsible, responsive: becomes an overlay below 960 px), top toolbar with app name "ATM Card Processor", environment badge (from `/actuator/info` is NOT needed; use a build-time constant), user menu (name, role chip, logout).
- Navigation items and required roles:

| Item | Route | Roles |
|------|-------|-------|
| Dashboard | `/dashboard` | ADMIN, ANALYST |
| Cards | `/cards` | ADMIN, ANALYST, DISPUTE_OFFICER |
| Transactions | `/transactions` | ADMIN, ANALYST, DISPUTE_OFFICER |
| Payment simulator | `/simulator` | ADMIN |
| Fraud rules | `/fraud-rules` | ADMIN, ANALYST (read-only for ANALYST) |
| Disputes | `/disputes` | ADMIN, ANALYST (read), DISPUTE_OFFICER |
| Reports | `/reports` | ADMIN, ANALYST |
| Users | `/admin/users` | ADMIN |
| API clients | `/admin/api-clients` | ADMIN |
| Audit log | `/admin/audit` | ADMIN |

- Default landing after login: `/dashboard` for ADMIN/ANALYST, `/disputes` for DISPUTE_OFFICER.
- Breadcrumb-style page headers with a title, optional subtitle, and primary action on the right.
- Responsive: usable on a tablet; tables become horizontally scrollable inside their card, never the whole page.

## 6. Shared UX states (every data view implements all of them)

| State | Requirement |
|-------|-------------|
| Loading | Skeleton rows or `mat-progress-bar`; never a blank panel |
| Empty | `empty-state` with explanation and the next action (for example "No cards yet. Issue your first card.") |
| Error | `error-state` with message, request id, and a **Retry** button |
| Success feedback | Snackbar after mutations ("Card blocked") |
| Destructive/irreversible actions | `confirm-dialog` stating consequences (block, close card, revoke API key, resolve dispute, disable rule/user) |
| Forms | Labels, hints, inline validation messages mirroring `04` §1.2, disabled submit until valid, server errors mapped to fields, focus moves to first invalid field |
| Tables | Paged (page sizes 10/20/50), sortable only on whitelisted columns, filters reflected in the URL query string (shareable, survives refresh), "clear filters" action, total count |
| Accessibility | Keyboard reachable, visible focus, `aria-label`s on icon buttons, color is never the only signal (status chips include text), contrast AA, `lang` set, table headers with scope, dialogs trap focus |

## 7. Screens

### 7.1 Login (`/login`)
Email and password, show/hide password toggle, error message for `INVALID_CREDENTIALS` ("Email or password is incorrect"; when locked the API returns the same error), loading state, `returnUrl` support, "Session expired" notice. No "register" or "forgot password" (WON'T).

### 7.2 Dashboard (`/dashboard`)
Data from `GET /reports/summary` (default last 7 days) and `GET /reports/daily` (today).
- KPI cards: Today's attempts, approval rate, approved volume (per currency), flagged today, open disputes, active cards.
- Charts: approved vs declined per day (stacked bars) over the range; hourly attempts for today (line); decline reasons (horizontal bars from `declinesByCode`).
- Range selector (7 / 30 / 90 days); refresh button; "last updated" time.
- "Recent flagged transactions" table (latest 5 via `GET /transactions?flagged=true&size=5`) linking to details.

### 7.3 Cards list (`/cards`)
Columns: masked PAN (monospace, copy disabled because it is masked), holder, currency, status chip, balance, expiry (`MM/YY`, red when `expired`), created. Filters: status, currency, expired, search (`q`). Row click opens detail. Primary action **Issue card** (ADMIN only).

### 7.4 Issue card (dialog or `/cards/new`)
Fields per `04` §6 request (holder name, currency, validity years, per-transaction limit, daily limit, initial balance, blocked MCCs multi-select from reference data). Money in major units. After success: if the response includes `pan`, show a **one-time reveal panel** ("Shown once. Copy it now.") with a copy button and an acknowledgment checkbox before closing; then navigate to the detail page.

### 7.5 Card detail (`/cards/:id`)
Header: masked PAN, status chip, holder, balance (large), currency, expiry.
Tabs:
1. **Overview**: limits, blocked MCCs (chips), lifecycle timestamps, block reason, "Spent today" is not returned by the API, so omit it.
2. **Ledger**: paged table of `LedgerEntryResponse` (type chip, signed amount colored with +/- text, balance after, description, who, when) with link to transaction/dispute.
3. **Transactions**: paged table scoped to the card.
Actions (ADMIN; buttons enabled by status): Activate, Block (reason dialog), Unblock, Close (confirm; disabled with tooltip when balance != 0), Top up (dialog with amount and description), Edit limits (dialog; handles `OPTIMISTIC_LOCK_FAILURE` by reloading and informing), Edit blocked MCCs.

### 7.6 Transactions list (`/transactions`)
Filters per `04` §7 (status, response code, decline reason, MCC, flagged, amount range, date range, card, merchant search). Columns: time (business zone), card last4 + holder, merchant, MCC, amount, status chip, response code, risk score (badge), flagged icon, source. **Export CSV** button (ADMIN, ANALYST) using the current filters; handles `EXPORT_TOO_LARGE` with a clear message. Row click opens a **detail drawer** or page: all fields, rule hits table, balance after, processing time, idempotency key, dispute link or **Open dispute** button (ADMIN, DISPUTE_OFFICER; only for approved transactions).

### 7.7 Payment simulator (`/simulator`, ADMIN)
Purpose: let a demo user act as a POS. Form: card selector (search cards; shows masked PAN, balance), amount, currency (defaults to card currency; allow changing to demonstrate decline 12), merchant name, MCC (select with labels), reference. Submit calls `POST /authorize` as the logged-in admin (JWT). Show the result card: big APPROVED/DECLINED, response code and message, transaction id link, and a "Replay same request" button that re-sends the **same** idempotency key to demonstrate idempotency (shows the `Idempotent-Replayed` indicator). Include quick presets ("Grocery 500", "Betting 100", "Rapid-fire x6" which fires six requests sequentially with distinct keys). The simulator is clearly labelled as a testing tool.

### 7.8 Fraud rules (`/fraud-rules`)
Table of rules: name, type, parameters summary, score points, action, enabled toggle (ADMIN), last updated. Editor dialog adapts fields by type (`MAX_AMOUNT`: threshold; `VELOCITY`: threshold + window; `BLOCKED_MCC`: MCC list), shows the risk thresholds from reference text (flag 50 / decline 80 are documented in help text). ANALYST sees read-only. No delete.

### 7.9 Disputes list (`/disputes`)
Filters: status, reason, overdue toggle. Columns: opened, card last4, amount, reason, status chip, evidence due (countdown; red when overdue), opened by. Row opens detail.

### 7.10 Dispute detail (`/disputes/:id`)
Left: transaction summary and dispute facts. Right: **timeline** of events (status transitions with actor, note, time). Action panel renders only the buttons in `allowedActions` (**the UI never re-implements the state machine**): Request evidence, Submit evidence (note required), Resolve won/lost (note required, confirm dialog stating money effect: "Won credits NPR X to the card"). After actions, refresh from the server response.

### 7.11 Reports (`/reports`)
Tabs: **Daily** (date picker defaulting to today, KPI row, hourly chart, declines by code table, top MCCs table) and **Range summary** (from/to pickers, max 92 days validation, series chart, totals). Currency-separated volumes are shown per currency (never summed across currencies). Export link to transactions CSV pre-filled with the same date range.

### 7.12 Users (`/admin/users`, ADMIN)
Table with role, enabled, locked indicator; create dialog (email, name, role, password with strength hint), edit dialog (name, role, enabled, optional new password). Handles `LAST_ADMIN_PROTECTED` and `EMAIL_ALREADY_USED` messages.

### 7.13 API clients (`/admin/api-clients`, ADMIN)
Table; **Create** dialog; on success a **one-time key reveal** (copy button, warning that it cannot be shown again, acknowledgment). Revoke with confirm.

### 7.14 Audit log (`/admin/audit`, ADMIN)
Filters: action (select), entity type, entity id, actor label, date range. Paged table with details column (truncated with tooltip), request id (copyable).

### 7.15 System pages
`/403`, `/404` (wildcard route), and an error boundary for failed lazy loads ("Reload").

## 8. Design guidance

Make it look like a real product, not a template:
- A deliberate palette (one primary, one accent, neutral greys, semantic success/warning/danger) defined with Angular Material theme tokens and CSS custom properties; consistent 8 px spacing scale; card surfaces with subtle borders instead of heavy shadows; 12 px radius; readable type scale (system font stack or a self-hosted variable font, no external CDN).
- Monospace tabular numerals for money and PANs; right-align amounts; consistent status chip colors (APPROVED green, DECLINED red, FLAGGED amber, ACTIVE green, INACTIVE grey, BLOCKED red, CLOSED neutral, dispute states distinct).
- Dense data tables (compact density), sticky header, zebra off, hover highlight.
- No lorem ipsum, no placeholder images, no emoji in UI chrome. Use Material icons (self-hosted) sparingly.
- Provide an app icon/favicon (simple SVG) and page titles per route (`title` in routes).

## 9. Frontend testing

| Level | What |
|-------|------|
| Unit | `money` pipe and minor-unit parser (many edge cases), auth store, auth interceptor (attach token, single shared refresh, retry once, logout on failure), error interceptor mapping, `applyServerErrors`, role guard, status chips, validators |
| Component | Login form behaviour; card issue form validation; filters sync to URL; dispute action panel renders only `allowedActions`; empty/error/loading states; one-time reveal panel |
| E2E (Playwright) | See `11` §6: login, issue + activate + top-up card, authorize via simulator (approve and decline), idempotent replay, fraud flag, dispute lifecycle with ledger credit, report numbers, role restrictions (analyst cannot see Users), session expiry refresh, CSV download |
| Accessibility | axe checks on login, dashboard, cards list, card detail, simulator in e2e; zero serious/critical violations |
| Build | `npm run build` with production configuration and budgets (initial bundle under 1 MB gzip-ish; set Angular budgets accordingly and keep warnings at zero) |

Coverage target for unit/component tests: >= 80% lines for `core` and `shared`, >= 70% for features.

## 10. Frontend scripts (`package.json`)

`start` (dev server with proxy), `build`, `test` (single run, CI mode), `lint`, `format`, `format:check`, `api:types` (openapi-typescript from `../docs/openapi.json`), `e2e` (Playwright), `e2e:ci`. `proxy.conf.json` proxies `/api` to `http://localhost:8080`.

## 11. Definition of done (frontend)

All screens above implemented; `npm run lint`, `npm run format:check`, `npm test`, `npm run build` green; e2e green against Compose; no console errors in e2e runs; no `any`; no TODOs; responsive down to tablet width; screenshots (login, dashboard, cards, card detail, simulator result, dispute detail, reports) captured by a Playwright script into `docs/screenshots/` and referenced from `README.md`.
