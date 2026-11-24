package dev.minicard.processor.common.error;

import dev.minicard.processor.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final MediaType PROBLEM_JSON =
      MediaType.parseMediaType("application/problem+json");

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApiException(
      ApiException ex, HttpServletRequest request) {
    log.debug("API exception occurred: code={}, detail={}", ex.getErrorCode(), ex.getDetail());
    return buildProblemResponse(
        ex.getErrorCode(), ex.getDetail(), ex.getValidationErrors(), request, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ApiException.ValidationErrorDetail> errors = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.add(
          new ApiException.ValidationErrorDetail(
              error.getField(),
              error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"));
    }
    errors.sort(Comparator.comparing(ApiException.ValidationErrorDetail::field));
    return buildProblemResponse(
        ErrorCode.VALIDATION_FAILED, "One or more fields are invalid", errors, request, null);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    List<ApiException.ValidationErrorDetail> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String paramName = result.getMethodParameter().getParameterName();
              result
                  .getResolvableErrors()
                  .forEach(
                      err ->
                          errors.add(
                              new ApiException.ValidationErrorDetail(
                                  paramName != null ? paramName : "parameter",
                                  err.getDefaultMessage() != null
                                      ? err.getDefaultMessage()
                                      : "Invalid value")));
            });
    errors.sort(Comparator.comparing(ApiException.ValidationErrorDetail::field));
    return buildProblemResponse(
        ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid", errors, request, null);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ApiException.ValidationErrorDetail> errors = new ArrayList<>();
    ex.getConstraintViolations()
        .forEach(
            violation ->
                errors.add(
                    new ApiException.ValidationErrorDetail(
                        violation.getPropertyPath().toString(), violation.getMessage())));
    errors.sort(Comparator.comparing(ApiException.ValidationErrorDetail::field));
    return buildProblemResponse(
        ErrorCode.VALIDATION_FAILED, "Validation constraints violated", errors, request, null);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    HttpMediaTypeNotSupportedException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ProblemDetail> handleMalformedRequest(
      Exception ex, HttpServletRequest request) {
    log.debug("Malformed request: {}", ex.getMessage());
    return buildProblemResponse(
        ErrorCode.MALFORMED_REQUEST,
        "Malformed request payload or parameter",
        List.of(),
        request,
        null);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLock(
      OptimisticLockingFailureException ex, HttpServletRequest request) {
    log.info("Optimistic locking failure: {}", ex.getMessage());
    return buildProblemResponse(
        ErrorCode.OPTIMISTIC_LOCK_FAILURE,
        "Resource has been modified by another transaction; please refresh and retry",
        List.of(),
        request,
        null);
  }

  @ExceptionHandler({
    PessimisticLockingFailureException.class,
    CannotAcquireLockException.class,
    QueryTimeoutException.class
  })
  public ResponseEntity<ProblemDetail> handleLockFailure(Exception ex, HttpServletRequest request) {
    log.warn("Database lock or query timeout: {}", ex.getMessage());
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.RETRY_AFTER, "1");
    return buildProblemResponse(
        ErrorCode.TEMPORARILY_UNAVAILABLE,
        "Service is temporarily unavailable due to concurrent resource access; please retry",
        List.of(),
        request,
        headers);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    return buildProblemResponse(
        ErrorCode.RESOURCE_NOT_FOUND,
        "Resource not found: " + request.getRequestURI(),
        List.of(),
        request,
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return buildProblemResponse(
        ErrorCode.FORBIDDEN,
        "Access is denied for the requested resource",
        List.of(),
        request,
        null);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthenticationException(
      AuthenticationException ex, HttpServletRequest request) {
    return buildProblemResponse(
        ErrorCode.UNAUTHENTICATED, "Authentication required", List.of(), request, null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
    String reqId = currentRequestId();
    log.error("Unhandled exception for requestId={}", reqId, ex);
    return buildProblemResponse(
        ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of(), request, null);
  }

  public ProblemDetail createProblemDetail(
      ErrorCode errorCode,
      String detail,
      List<ApiException.ValidationErrorDetail> errors,
      String uriPath) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(errorCode.httpStatus().value()), detail);
    problemDetail.setType(URI.create(errorCode.typeUri()));
    problemDetail.setTitle(errorCode.defaultTitle());
    problemDetail.setInstance(URI.create(uriPath));
    problemDetail.setProperty("code", errorCode.name());
    problemDetail.setProperty("requestId", currentRequestId());
    problemDetail.setProperty("timestamp", clock.instant().toString());

    if (errors != null && !errors.isEmpty()) {
      problemDetail.setProperty("errors", errors);
    }
    return problemDetail;
  }

  private ResponseEntity<ProblemDetail> buildProblemResponse(
      ErrorCode errorCode,
      String detail,
      List<ApiException.ValidationErrorDetail> errors,
      HttpServletRequest request,
      HttpHeaders extraHeaders) {

    ProblemDetail problem =
        createProblemDetail(
            errorCode, detail, errors, request != null ? request.getRequestURI() : "/");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);
    if (extraHeaders != null) {
      headers.addAll(extraHeaders);
    }
    return new ResponseEntity<>(
        problem, headers, HttpStatus.valueOf(errorCode.httpStatus().value()));
  }

  private String currentRequestId() {
    String id = MDC.get(RequestIdFilter.MDC_KEY);
    return id != null ? id : "unknown";
  }
}
