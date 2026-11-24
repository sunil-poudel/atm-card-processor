package dev.minicard.processor.common.error;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;

@SuppressWarnings("serial")
public abstract sealed class ApiException extends RuntimeException
    permits BadRequestException,
        UnauthenticatedException,
        ForbiddenException,
        NotFoundException,
        ConflictException,
        UnprocessableException,
        ServiceUnavailableException {

  private final ErrorCode errorCode;
  private final List<ValidationErrorDetail> validationErrors;

  protected ApiException(ErrorCode errorCode, String detail) {
    this(errorCode, detail, List.of());
  }

  protected ApiException(
      ErrorCode errorCode, String detail, List<ValidationErrorDetail> validationErrors) {
    super(detail);
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode cannot be null");
    this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return errorCode.httpStatus();
  }

  public String getDetail() {
    return getMessage();
  }

  public List<ValidationErrorDetail> getValidationErrors() {
    return validationErrors;
  }

  public record ValidationErrorDetail(String field, String message)
      implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
  }
}
