# ADR 0011: Business-Zone Day Boundaries (Asia/Kathmandu)

## Status
Accepted

## Context
Card limits (such as daily spend limits) and business reports are defined on a calendar-day basis. The business operates in the `Asia/Kathmandu` time zone (UTC+05:45, with no daylight saving time transitions). Evaluating daily limits against UTC midnight would produce incorrect spend resets at 05:45 AM local time, violating customer expectations and issuer rules.

## Decision
All timestamps are stored in UTC as `TIMESTAMPTZ`. Calendar-day calculations (such as today's accumulated spend and daily reports) explicitly compute local day boundaries using `Asia/Kathmandu` (`UTC+05:45`) via the injected `BusinessCalendar` component. The start of day for a date `D` is `D` at 00:00:00+05:45 (which corresponds to `D - 1` at 18:15:00 UTC).

## Consequences
- Accurate daily limit resets at exactly local midnight in Nepal.
- Consistent report grouping across SQL and application logic.
- Timezone awareness is centralized in `BusinessCalendar`.
