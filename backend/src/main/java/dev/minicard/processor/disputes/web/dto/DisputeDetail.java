package dev.minicard.processor.disputes.web.dto;

import dev.minicard.processor.disputes.domain.Dispute;
import dev.minicard.processor.disputes.domain.DisputeAction;
import dev.minicard.processor.disputes.domain.DisputeReason;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.transactions.web.dto.TransactionSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisputeDetail(
    UUID id,
    UUID transactionId,
    UUID cardId,
    String cardLast4,
    DisputeReason reason,
    String description,
    DisputeStatus status,
    long amountMinor,
    String currency,
    String openedBy,
    Instant openedAt,
    Instant evidenceDueAt,
    Instant resolvedAt,
    String resolvedBy,
    String resolutionNote,
    List<DisputeAction> allowedActions,
    List<DisputeEventResponse> events,
    TransactionSummary transaction) {

  public static DisputeDetail fromEntity(
      Dispute d, List<DisputeEventResponse> events, TransactionSummary transaction, Instant now) {
    return new DisputeDetail(
        d.getId(),
        d.getTransactionId(),
        d.getCardId(),
        d.getCard().getLast4(),
        d.getReason(),
        d.getDescription(),
        d.getStatus(),
        d.getAmountMinor(),
        d.getCurrency(),
        d.getOpenedBy(),
        d.getOpenedAt(),
        d.getEvidenceDueAt(),
        d.getResolvedAt(),
        d.getResolvedBy(),
        d.getResolutionNote(),
        d.getAllowedActions(now),
        events,
        transaction);
  }
}
