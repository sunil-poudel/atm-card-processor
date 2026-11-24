package dev.minicard.processor.cards.domain;

import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.UnprocessableException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "pan_encrypted", nullable = false, length = 300)
  private String panEncrypted;

  @Column(name = "pan_key_id", nullable = false, length = 20)
  private String panKeyId;

  @Column(name = "pan_hash", nullable = false, length = 64, unique = true)
  private String panHash;

  @Column(name = "bin", nullable = false, length = 6)
  private String bin;

  @Column(name = "last4", nullable = false, length = 4)
  private String last4;

  @Column(name = "holder_name", nullable = false, length = 100)
  private String holderName;

  @Column(name = "expiry_month", nullable = false)
  private int expiryMonth;

  @Column(name = "expiry_year", nullable = false)
  private int expiryYear;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private CardStatus status;

  @Column(name = "balance_minor", nullable = false)
  private long balanceMinor;

  @Column(name = "per_transaction_limit_minor", nullable = false)
  private long perTransactionLimitMinor;

  @Column(name = "daily_limit_minor", nullable = false)
  private long dailyLimitMinor;

  @Column(name = "block_reason", length = 200)
  private String blockReason;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "blocked_at")
  private Instant blockedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "card_blocked_mcc", joinColumns = @JoinColumn(name = "card_id"))
  @Column(name = "mcc", nullable = false, length = 4)
  @org.hibernate.annotations.BatchSize(size = 50)
  private Set<String> blockedMccs = new HashSet<>();

  protected Card() {}

  public Card(
      UUID id,
      String panEncrypted,
      String panKeyId,
      String panHash,
      String bin,
      String last4,
      String holderName,
      int expiryMonth,
      int expiryYear,
      String currency,
      long perTransactionLimitMinor,
      long dailyLimitMinor,
      Set<String> blockedMccs,
      UUID createdBy,
      Instant now) {
    this.id = id;
    this.panEncrypted = Objects.requireNonNull(panEncrypted, "panEncrypted cannot be null");
    this.panKeyId = Objects.requireNonNull(panKeyId, "panKeyId cannot be null");
    this.panHash = Objects.requireNonNull(panHash, "panHash cannot be null");
    this.bin = Objects.requireNonNull(bin, "bin cannot be null");
    this.last4 = Objects.requireNonNull(last4, "last4 cannot be null");
    this.holderName = Objects.requireNonNull(holderName, "holderName cannot be null").trim();
    this.expiryMonth = expiryMonth;
    this.expiryYear = expiryYear;
    this.currency = Objects.requireNonNull(currency, "currency cannot be null");
    this.status = CardStatus.INACTIVE;
    this.balanceMinor = 0L;
    this.perTransactionLimitMinor = perTransactionLimitMinor;
    this.dailyLimitMinor = dailyLimitMinor;
    if (blockedMccs != null) {
      this.blockedMccs.addAll(blockedMccs);
    }
    this.createdBy = createdBy;
    this.createdAt = Objects.requireNonNull(now, "now cannot be null");
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getPanEncrypted() {
    return panEncrypted;
  }

  public String getPanKeyId() {
    return panKeyId;
  }

  public String getPanHash() {
    return panHash;
  }

  public String getBin() {
    return bin;
  }

  public String getLast4() {
    return last4;
  }

  public String getMaskedPan() {
    return bin + "******" + last4;
  }

  public String getHolderName() {
    return holderName;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public CardStatus getStatus() {
    return status;
  }

  public long getBalanceMinor() {
    return balanceMinor;
  }

  public long getPerTransactionLimitMinor() {
    return perTransactionLimitMinor;
  }

  public long getDailyLimitMinor() {
    return dailyLimitMinor;
  }

  public String getBlockReason() {
    return blockReason;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public Instant getBlockedAt() {
    return blockedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  public Set<String> getBlockedMccs() {
    return Collections.unmodifiableSet(blockedMccs);
  }

  public boolean isExpired(Instant now, ZoneId zone) {
    YearMonth cardMonth = YearMonth.of(expiryYear, expiryMonth);
    Instant expiryThreshold = cardMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
    return !now.isBefore(expiryThreshold);
  }

  public void activate(Instant now) {
    if (this.status != CardStatus.INACTIVE) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Card can only be activated from INACTIVE; current status: " + status);
    }
    this.status = CardStatus.ACTIVE;
    if (this.activatedAt == null) {
      this.activatedAt = now;
    }
    this.updatedAt = now;
  }

  public void block(String reason, Instant now) {
    if (this.status == CardStatus.CLOSED || this.status == CardStatus.BLOCKED) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION, "Card cannot be blocked from status: " + status);
    }
    if (reason == null || reason.trim().length() < 3 || reason.trim().length() > 200) {
      throw new UnprocessableException(
          ErrorCode.VALIDATION_FAILED, "Block reason must be between 3 and 200 characters");
    }
    this.status = CardStatus.BLOCKED;
    this.blockReason = reason.trim();
    this.blockedAt = now;
    this.updatedAt = now;
  }

  public void unblock(Instant now) {
    if (this.status != CardStatus.BLOCKED) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Only BLOCKED cards can be unblocked; current status: " + status);
    }
    this.status = (this.activatedAt != null) ? CardStatus.ACTIVE : CardStatus.INACTIVE;
    this.blockReason = null;
    this.blockedAt = null;
    this.updatedAt = now;
  }

  public void close(Instant now) {
    if (this.status == CardStatus.CLOSED) {
      throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "Card is already CLOSED");
    }
    if (this.balanceMinor != 0L) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Card balance must be zero before closing; current balance: " + balanceMinor);
    }
    this.status = CardStatus.CLOSED;
    this.closedAt = now;
    this.updatedAt = now;
  }

  public void updateLimits(long perTransaction, long daily, Instant now) {
    if (this.status == CardStatus.CLOSED) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION, "Cannot update limits on CLOSED card");
    }
    if (perTransaction < 1 || daily < perTransaction || daily > 10_000_000_000L) {
      throw new UnprocessableException(
          ErrorCode.LIMIT_CONFIGURATION_INVALID,
          "Limits must satisfy: 1 <= perTransaction <= daily <= 10,000,000,000");
    }
    this.perTransactionLimitMinor = perTransaction;
    this.dailyLimitMinor = daily;
    this.updatedAt = now;
  }

  public void updateBlockedMccs(Set<String> mccs, Instant now) {
    if (this.status == CardStatus.CLOSED) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION, "Cannot update blocked MCCs on CLOSED card");
    }
    if (mccs != null && mccs.size() > 50) {
      throw new UnprocessableException(
          ErrorCode.VALIDATION_FAILED, "A maximum of 50 MCCs may be blocked");
    }
    if (mccs != null) {
      for (String mcc : mccs) {
        if (mcc == null || !mcc.matches("^\\d{4}$")) {
          throw new UnprocessableException(
              ErrorCode.VALIDATION_FAILED, "Each MCC must be exactly 4 digits: " + mcc);
        }
      }
    }
    this.blockedMccs.clear();
    if (mccs != null) {
      this.blockedMccs.addAll(mccs);
    }
    this.updatedAt = now;
  }

  /** Sole mutator for card balance. Per architecture rules, ONLY LedgerService may invoke this. */
  public void updateBalance(long newBalanceMinor) {
    if (newBalanceMinor < 0) {
      throw new IllegalArgumentException("Card balance cannot be negative: " + newBalanceMinor);
    }
    this.balanceMinor = newBalanceMinor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Card that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
