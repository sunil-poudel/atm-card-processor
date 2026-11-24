package dev.minicard.processor.common.error;

public final class UnprocessableException extends ApiException {

  private static final long serialVersionUID = 1L;

  public UnprocessableException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }
}
