package dev.minicard.processor.common.error;

public final class UnauthenticatedException extends ApiException {

  private static final long serialVersionUID = 1L;

  public UnauthenticatedException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }

  public UnauthenticatedException(String detail) {
    super(ErrorCode.UNAUTHENTICATED, detail);
  }
}
