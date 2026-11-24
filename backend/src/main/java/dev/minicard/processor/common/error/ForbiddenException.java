package dev.minicard.processor.common.error;

public final class ForbiddenException extends ApiException {

  private static final long serialVersionUID = 1L;

  public ForbiddenException(String detail) {
    super(ErrorCode.FORBIDDEN, detail);
  }

  public ForbiddenException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }
}
