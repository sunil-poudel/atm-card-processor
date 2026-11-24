package dev.minicard.processor.common.error;

public final class ServiceUnavailableException extends ApiException {

  private static final long serialVersionUID = 1L;

  public ServiceUnavailableException(String detail) {
    super(ErrorCode.TEMPORARILY_UNAVAILABLE, detail);
  }

  public ServiceUnavailableException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }
}
