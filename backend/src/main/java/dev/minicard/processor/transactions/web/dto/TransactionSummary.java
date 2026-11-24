package dev.minicard.processor.transactions.web.dto;

import dev.minicard.processor.transactions.domain.DeclineReason;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

public record TransactionSummary(
    UUID id,
    Instant createdAt,
    UUID cardId,
    String cardLast4,
    String holderName,
    long amountMinor,
    String currency,
    String merchantName,
    String mcc,
    TransactionStatus status,
    String responseCode,
    DeclineReason declineReason,
    boolean flagged,
    int riskScore,
    String source,
    String sourceName) {

  public static TransactionSummary fromEntity(
      Transaction t, String cardLast4, String holderName, String sourceName) {
    String source = t.getApiClientId() != null ? "API_CLIENT" : "USER";
    return new TransactionSummary(
        t.getId(),
        t.getCreatedAt(),
        t.getCard().getId(),
        cardLast4 != null ? cardLast4 : t.getCard().getLast4(),
        holderName != null ? holderName : t.getCard().getHolderName(),
        t.getAmountMinor(),
        t.getCurrency(),
        t.getMerchantName(),
        t.getMcc(),
        t.getStatus(),
        t.getResponseCode(),
        t.getDeclineReason(),
        t.isFlagged(),
        t.getRiskScore(),
        source,
        sourceName);
  }
}
