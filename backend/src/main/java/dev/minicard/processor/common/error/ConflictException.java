package dev.minicard.processor.common.error;

public final class ConflictException extends ApiException {

  private static final long serialVersionUID = 1L;

  public ConflictException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }
}
