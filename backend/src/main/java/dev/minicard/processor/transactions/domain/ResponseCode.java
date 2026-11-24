package dev.minicard.processor.transactions.domain;

import java.util.Map;

public final class ResponseCode {

  public static final String APPROVED = "00";
  public static final String INVALID_TRANSACTION = "12";
  public static final String INSUFFICIENT_FUNDS = "51";
  public static final String EXPIRED_CARD = "54";
  public static final String NOT_PERMITTED = "57";
  public static final String SUSPECTED_FRAUD = "59";
  public static final String EXCEEDS_LIMIT = "61";
  public static final String RESTRICTED_CARD = "62";

  private static final Map<String, String> MESSAGES =
      Map.of(
          APPROVED, "Approved",
          INVALID_TRANSACTION, "Invalid transaction",
          INSUFFICIENT_FUNDS, "Insufficient funds",
          EXPIRED_CARD, "Expired card",
          NOT_PERMITTED, "Transaction not permitted to cardholder",
          SUSPECTED_FRAUD, "Suspected fraud",
          EXCEEDS_LIMIT, "Exceeds amount limit",
          RESTRICTED_CARD, "Restricted card");

  private ResponseCode() {}

  public static String messageFor(String code) {
    String msg = MESSAGES.get(code);
    return msg != null ? msg : "Transaction declined";
  }
}
