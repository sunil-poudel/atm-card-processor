package dev.minicard.processor.transactions.service;

import dev.minicard.processor.transactions.web.dto.AuthorizeRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprintCalculator {

  private RequestFingerprintCalculator() {}

  public static String calculate(AuthorizeRequest req) {
    String cardId = req.cardId() != null ? req.cardId().toString() : "";
    String amount = req.amountMinor() != null ? req.amountMinor().toString() : "";
    String currency = req.currency() != null ? req.currency() : "";
    String merchant = req.merchantName() != null ? req.merchantName().trim() : "";
    String mcc = req.mcc() != null ? req.mcc() : "";
    String reference = req.reference() != null ? req.reference() : "";

    StringBuilder sb = new StringBuilder();
    appendPrefixed(sb, cardId);
    appendPrefixed(sb, amount);
    appendPrefixed(sb, currency);
    appendPrefixed(sb, merchant);
    appendPrefixed(sb, mcc);
    appendPrefixed(sb, reference);

    return sha256Hex(sb.toString());
  }

  private static void appendPrefixed(StringBuilder sb, String val) {
    sb.append(val.length()).append(':').append(val).append(':');
  }

  private static String sha256Hex(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
