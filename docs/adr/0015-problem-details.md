# ADR 0015: RFC 9457 Problem Details for HTTP APIs

## Status
Accepted

## Context
REST APIs require consistent, standardized error payloads so clients (such as the Angular frontend or external POS clients) can handle failures predictably without parsing fragile freeform error strings.

## Decision
Adopt RFC 9457 (`application/problem+json`) Problem Details as the uniform error representation for all error responses (4xx and 5xx). Include standardized extension fields:
- `code`: machine-readable enum string from the defined catalog (e.g. `CARD_NOT_FOUND`, `VALIDATION_FAILED`).
- `requestId`: request correlation identifier traced across logs and responses.
- `timestamp`: UTC ISO-8601 instant when the error occurred.
- `errors`: structured field-level validation errors array when applicable.

Clients switch exclusively on `code`, never on human-readable `detail` messages.

## Consequences
- Predictable client-side error handling and automated mapping.
- Standardized logging and tracing via `requestId`.
- Sensitive internal stack traces or database errors are strictly prevented from leaking to clients.
