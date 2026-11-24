package dev.minicard.processor.ledger.domain;

import dev.minicard.processor.cards.domain.Card;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "card_id", nullable = false, updatable = false)
  private Card card;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 20, updatable = false)
  private LedgerEntryType entryType;

  @Column(name = "amount_minor", nullable = false, updatable = false)
  private long amountMinor;

  @Column(name = "balance_after_minor", nullable = false, updatable = false)
  private long balanceAfterMinor;

  @Column(name = "transaction_id", updatable = false)
  private UUID transactionId;

  @Column(name = "dispute_id", updatable = false)
  private UUID disputeId;

  @Column(name = "description", length = 200, updatable = false)
  private String description;

  @Column(name = "created_by", nullable = false, length = 100, updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected LedgerEntry() {}

  public LedgerEntry(
      UUID id,
      Card card,
      LedgerEntryType entryType,
      long amountMinor,
      long balanceAfterMinor,
      UUID transactionId,
      UUID disputeId,
      String description,
      String createdBy,
      Instant createdAt) {
    this.id = id;
    this.card = Objects.requireNonNull(card, "card cannot be null");
    this.entryType = Objects.requireNonNull(entryType, "entryType cannot be null");
    this.amountMinor = amountMinor;
    this.balanceAfterMinor = balanceAfterMinor;
    this.transactionId = transactionId;
    this.disputeId = disputeId;
    this.description = description;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");

    validateConstraints();
  }

  private void validateConstraints() {
    if (amountMinor == 0) {
      throw new IllegalArgumentException("Ledger amount cannot be zero");
    }
    if (balanceAfterMinor < 0) {
      throw new IllegalArgumentException(
          "Ledger balance_after cannot be negative: " + balanceAfterMinor);
    }
    switch (entryType) {
      case PURCHASE -> {
        if (amountMinor >= 0) {
          throw new IllegalArgumentException(
              "PURCHASE amount must be negative, got " + amountMinor);
        }
        if (transactionId == null) {
          throw new IllegalArgumentException("PURCHASE entry must reference a transactionId");
        }
        if (disputeId != null) {
          throw new IllegalArgumentException("PURCHASE entry must not reference a disputeId");
        }
      }
      case TOPUP -> {
        if (amountMinor <= 0) {
          throw new IllegalArgumentException("TOPUP amount must be positive, got " + amountMinor);
        }
        if (transactionId != null || disputeId != null) {
          throw new IllegalArgumentException(
              "TOPUP entry must not reference transactionId or disputeId");
        }
      }
      case DISPUTE_CREDIT -> {
        if (amountMinor <= 0) {
          throw new IllegalArgumentException(
              "DISPUTE_CREDIT amount must be positive, got " + amountMinor);
        }
        if (disputeId == null) {
          throw new IllegalArgumentException("DISPUTE_CREDIT entry must reference a disputeId");
        }
        if (transactionId != null) {
          throw new IllegalArgumentException(
              "DISPUTE_CREDIT entry must not reference a transactionId");
        }
      }
    }
  }

  public UUID getId() {
    return id;
  }

  public Card getCard() {
    return card;
  }

  public LedgerEntryType getEntryType() {
    return entryType;
  }

  public long getAmountMinor() {
    return amountMinor;
  }

  public long getBalanceAfterMinor() {
    return balanceAfterMinor;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public UUID getDisputeId() {
    return disputeId;
  }

  public String getDescription() {
    return description;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LedgerEntry that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
