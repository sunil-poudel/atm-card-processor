package dev.minicard.processor.cards.web.dto;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record CardResponse(
    UUID id,
    String maskedPan,
    String bin,
    String last4,
    String holderName,
    int expiryMonth,
    int expiryYear,
    boolean expired,
    String currency,
    CardStatus status,
    long balanceMinor,
    long perTransactionLimitMinor,
    long dailyLimitMinor,
    List<String> blockedMccs,
    String blockReason,
    Instant activatedAt,
    Instant blockedAt,
    Instant closedAt,
    Instant createdAt,
    Instant updatedAt,
    Long version,
    String pan) {

  public static CardResponse fromEntity(Card card, Instant now, ZoneId zone, String plainPan) {
    return new CardResponse(
        card.getId(),
        card.getMaskedPan(),
        card.getBin(),
        card.getLast4(),
        card.getHolderName(),
        card.getExpiryMonth(),
        card.getExpiryYear(),
        card.isExpired(now, zone),
        card.getCurrency(),
        card.getStatus(),
        card.getBalanceMinor(),
        card.getPerTransactionLimitMinor(),
        card.getDailyLimitMinor(),
        card.getBlockedMccs().stream().sorted().toList(),
        card.getBlockReason(),
        card.getActivatedAt(),
        card.getBlockedAt(),
        card.getClosedAt(),
        card.getCreatedAt(),
        card.getUpdatedAt(),
        card.getVersion(),
        plainPan);
  }
}
