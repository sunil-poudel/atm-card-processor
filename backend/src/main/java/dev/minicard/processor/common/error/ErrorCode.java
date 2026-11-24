package dev.minicard.processor.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_FAILED(
      HttpStatus.BAD_REQUEST, "Validation failed", "urn:minicard:problem:validation-failed"),
  MALFORMED_REQUEST(
      HttpStatus.BAD_REQUEST, "Malformed request", "urn:minicard:problem:malformed-request"),
  IDEMPOTENCY_KEY_REQUIRED(
      HttpStatus.BAD_REQUEST,
      "Idempotency key required",
      "urn:minicard:problem:idempotency-key-required"),

  UNAUTHENTICATED(
      HttpStatus.UNAUTHORIZED, "Authentication required", "urn:minicard:problem:unauthenticated"),
  INVALID_CREDENTIALS(
      HttpStatus.UNAUTHORIZED, "Invalid credentials", "urn:minicard:problem:invalid-credentials"),
  REFRESH_TOKEN_INVALID(
      HttpStatus.UNAUTHORIZED,
      "Invalid refresh token",
      "urn:minicard:problem:refresh-token-invalid"),

  FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden", "urn:minicard:problem:forbidden"),

  CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "Card not found", "urn:minicard:problem:card-not-found"),
  TRANSACTION_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Transaction not found", "urn:minicard:problem:transaction-not-found"),
  DISPUTE_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Dispute not found", "urn:minicard:problem:dispute-not-found"),
  FRAUD_RULE_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Fraud rule not found", "urn:minicard:problem:fraud-rule-not-found"),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found", "urn:minicard:problem:user-not-found"),
  API_CLIENT_NOT_FOUND(
      HttpStatus.NOT_FOUND, "API client not found", "urn:minicard:problem:api-client-not-found"),
  RESOURCE_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Resource not found", "urn:minicard:problem:resource-not-found"),

  INVALID_STATE_TRANSITION(
      HttpStatus.CONFLICT,
      "Invalid state transition",
      "urn:minicard:problem:invalid-state-transition"),
  IDEMPOTENCY_CONFLICT(
      HttpStatus.CONFLICT, "Idempotency conflict", "urn:minicard:problem:idempotency-conflict"),
  OPTIMISTIC_LOCK_FAILURE(
      HttpStatus.CONFLICT,
      "Optimistic lock failure",
      "urn:minicard:problem:optimistic-lock-failure"),
  EMAIL_ALREADY_USED(
      HttpStatus.CONFLICT, "Email already used", "urn:minicard:problem:email-already-used"),
  NAME_ALREADY_USED(
      HttpStatus.CONFLICT, "Name already used", "urn:minicard:problem:name-already-used"),
  DISPUTE_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "Dispute already exists", "urn:minicard:problem:dispute-already-exists"),
  LAST_ADMIN_PROTECTED(
      HttpStatus.CONFLICT, "Last admin protected", "urn:minicard:problem:last-admin-protected"),

  IDEMPOTENCY_KEY_REUSED(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Idempotency key reused",
      "urn:minicard:problem:idempotency-key-reused"),
  DISPUTE_NOT_ELIGIBLE(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Dispute not eligible",
      "urn:minicard:problem:dispute-not-eligible"),
  UNSUPPORTED_CURRENCY(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Unsupported currency",
      "urn:minicard:problem:unsupported-currency"),
  LIMIT_CONFIGURATION_INVALID(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Limit configuration invalid",
      "urn:minicard:problem:limit-configuration-invalid"),
  AMOUNT_OVERFLOW(
      HttpStatus.UNPROCESSABLE_CONTENT, "Amount overflow", "urn:minicard:problem:amount-overflow"),
  EXPORT_TOO_LARGE(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Export too large",
      "urn:minicard:problem:export-too-large"),

  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Rate limited", "urn:minicard:problem:rate-limited"),

  TEMPORARILY_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Temporarily unavailable",
      "urn:minicard:problem:temporarily-unavailable"),
  INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Internal server error",
      "urn:minicard:problem:internal-error");

  private final HttpStatus httpStatus;
  private final String defaultTitle;
  private final String typeUri;

  ErrorCode(HttpStatus httpStatus, String defaultTitle, String typeUri) {
    this.httpStatus = httpStatus;
    this.defaultTitle = defaultTitle;
    this.typeUri = typeUri;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }

  public String defaultTitle() {
    return defaultTitle;
  }

  public String typeUri() {
    return typeUri;
  }
}
