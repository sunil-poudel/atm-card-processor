package dev.minicard.processor.transactions.web.dto;

import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

public record AuthorizeResponse(
    UUID transactionId,
    TransactionStatus status,
    String responseCode,
    String responseMessage,
    Instant authorizedAt) {

  public static AuthorizeResponse fromEntity(Transaction transaction, String responseMessage) {
    return new AuthorizeResponse(
        transaction.getId(),
        transaction.getStatus(),
        transaction.getResponseCode(),
        responseMessage,
        transaction.getCreatedAt());
  }
}
