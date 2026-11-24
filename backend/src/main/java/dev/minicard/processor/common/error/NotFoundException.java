package dev.minicard.processor.common.error;

public final class NotFoundException extends ApiException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }
}
