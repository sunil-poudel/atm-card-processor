package dev.minicard.processor.transactions.domain;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.common.id.UuidV7;
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

@Entity
@Table(name = "transactions")
public class Transaction {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "card_id", nullable = false, updatable = false)
  private Card card;

  @Column(name = "api_client_id", updatable = false)
  private UUID apiClientId;

  @Column(name = "initiated_by_user_id", updatable = false)
  private UUID initiatedByUserId;

  @Column(name = "idempotency_scope", nullable = false, length = 80, updatable = false)
  private String idempotencyScope;

  @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
  private String requestFingerprint;

  @Column(name = "amount_minor", nullable = false, updatable = false)
  private long amountMinor;

  @Column(name = "currency", nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(name = "merchant_name", nullable = false, length = 100, updatable = false)
  private String merchantName;

  @Column(name = "mcc", nullable = false, length = 4, updatable = false)
  private String mcc;

  @Column(name = "reference", length = 64, updatable = false)
  private String reference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10, updatable = false)
  private TransactionStatus status;

  @Column(name = "response_code", nullable = false, length = 2, updatable = false)
  private String responseCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "decline_reason", length = 30, updatable = false)
  private DeclineReason declineReason;

  @Column(name = "flagged", nullable = false, updatable = false)
  private boolean flagged;

  @Column(name = "risk_score", nullable = false, updatable = false)
  private int riskScore;

  @Column(name = "balance_after_minor", updatable = false)
  private Long balanceAfterMinor;

  @Column(name = "processing_millis", nullable = false, updatable = false)
  private int processingMillis;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Transaction() {}

  public Transaction(
      UUID id,
      Card card,
      UUID apiClientId,
      UUID initiatedByUserId,
      String idempotencyScope,
      String idempotencyKey,
      String requestFingerprint,
      long amountMinor,
      String currency,
      String merchantName,
      String mcc,
      String reference,
      TransactionStatus status,
      String responseCode,
      DeclineReason declineReason,
      boolean flagged,
      int riskScore,
      Long balanceAfterMinor,
      int processingMillis,
      Instant createdAt) {
    this.id = id != null ? id : UuidV7.newId();
    this.card = Objects.requireNonNull(card, "card cannot be null");
    this.apiClientId = apiClientId;
    this.initiatedByUserId = initiatedByUserId;
    this.idempotencyScope =
        Objects.requireNonNull(idempotencyScope, "idempotencyScope cannot be null");
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
    this.requestFingerprint =
        Objects.requireNonNull(requestFingerprint, "requestFingerprint cannot be null");
    this.amountMinor = amountMinor;
    this.currency = Objects.requireNonNull(currency, "currency cannot be null");
    this.merchantName = Objects.requireNonNull(merchantName, "merchantName cannot be null").trim();
    this.mcc = Objects.requireNonNull(mcc, "mcc cannot be null");
    this.reference = reference;
    this.status = Objects.requireNonNull(status, "status cannot be null");
    this.responseCode = Objects.requireNonNull(responseCode, "responseCode cannot be null");
    this.declineReason = declineReason;
    this.flagged = flagged;
    this.riskScore = riskScore;
    this.balanceAfterMinor = balanceAfterMinor;
    this.processingMillis = processingMillis;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");

    validateConsistency();
  }

  private void validateConsistency() {
    if (apiClientId == null && initiatedByUserId == null) {
      throw new IllegalStateException(
          "Transaction must have either apiClientId or initiatedByUserId");
    }
    if (apiClientId != null && initiatedByUserId != null) {
      throw new IllegalStateException(
          "Transaction cannot have both apiClientId and initiatedByUserId");
    }
    if (status == TransactionStatus.APPROVED) {
      if (!"00".equals(responseCode)) {
        throw new IllegalStateException("Approved transaction must have responseCode 00");
      }
      if (declineReason != null) {
        throw new IllegalStateException("Approved transaction must have null declineReason");
      }
      if (balanceAfterMinor == null) {
        throw new IllegalStateException(
            "Approved transaction must have non-null balanceAfterMinor");
      }
    } else {
      if ("00".equals(responseCode)) {
        throw new IllegalStateException("Declined transaction cannot have responseCode 00");
      }
      if (declineReason == null) {
        throw new IllegalStateException("Declined transaction must have non-null declineReason");
      }
      if (balanceAfterMinor != null) {
        throw new IllegalStateException("Declined transaction must have null balanceAfterMinor");
      }
      if (flagged) {
        throw new IllegalStateException("Declined transaction cannot be flagged");
      }
    }
  }

  public UUID getId() {
    return id;
  }

  public Card getCard() {
    return card;
  }

  public UUID getApiClientId() {
    return apiClientId;
  }

  public UUID getInitiatedByUserId() {
    return initiatedByUserId;
  }

  public String getIdempotencyScope() {
    return idempotencyScope;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public long getAmountMinor() {
    return amountMinor;
  }

  public String getCurrency() {
    return currency;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getMcc() {
    return mcc;
  }

  public String getReference() {
    return reference;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public String getResponseCode() {
    return responseCode;
  }

  public DeclineReason getDeclineReason() {
    return declineReason;
  }

  public boolean isFlagged() {
    return flagged;
  }

  public int getRiskScore() {
    return riskScore;
  }

  public Long getBalanceAfterMinor() {
    return balanceAfterMinor;
  }

  public int getProcessingMillis() {
    return processingMillis;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
