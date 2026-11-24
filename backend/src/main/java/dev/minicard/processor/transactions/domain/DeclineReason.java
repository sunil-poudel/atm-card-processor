package dev.minicard.processor.transactions.domain;

public enum DeclineReason {
  CARD_NOT_ACTIVE("62", "Restricted card"),
  CARD_EXPIRED("54", "Expired card"),
  CURRENCY_MISMATCH("12", "Invalid transaction"),
  MCC_RESTRICTED("57", "Transaction not permitted to cardholder"),
  LIMIT_PER_TRANSACTION("61", "Exceeds amount limit"),
  LIMIT_DAILY("61", "Exceeds amount limit"),
  INSUFFICIENT_FUNDS("51", "Insufficient funds"),
  SUSPECTED_FRAUD("59", "Suspected fraud");

  private final String responseCode;
  private final String message;

  DeclineReason(String responseCode, String message) {
    this.responseCode = responseCode;
    this.message = message;
  }

  public String responseCode() {
    return responseCode;
  }

  public String message() {
    return message;
  }
}
