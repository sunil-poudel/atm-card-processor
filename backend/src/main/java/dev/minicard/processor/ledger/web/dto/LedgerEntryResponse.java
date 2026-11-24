package dev.minicard.processor.ledger.web.dto;

import dev.minicard.processor.ledger.domain.LedgerEntry;
import dev.minicard.processor.ledger.domain.LedgerEntryType;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    UUID cardId,
    LedgerEntryType type,
    long amountMinor,
    long balanceAfterMinor,
    UUID transactionId,
    UUID disputeId,
    String description,
    String createdBy,
    Instant createdAt) {

  public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
    return new LedgerEntryResponse(
        entry.getId(),
        entry.getCard().getId(),
        entry.getEntryType(),
        entry.getAmountMinor(),
        entry.getBalanceAfterMinor(),
        entry.getTransactionId(),
        entry.getDisputeId(),
        entry.getDescription(),
        entry.getCreatedBy(),
        entry.getCreatedAt());
  }
}
