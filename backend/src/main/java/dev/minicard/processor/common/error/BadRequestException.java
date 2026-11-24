package dev.minicard.processor.common.error;

import java.util.List;

public final class BadRequestException extends ApiException {

  private static final long serialVersionUID = 1L;

  public BadRequestException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }

  public BadRequestException(
      ErrorCode errorCode, String detail, List<ValidationErrorDetail> errors) {
    super(errorCode, detail, errors);
  }
}
