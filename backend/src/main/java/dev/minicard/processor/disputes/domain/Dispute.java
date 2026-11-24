package dev.minicard.processor.disputes.domain;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.id.UuidV7;
import dev.minicard.processor.transactions.domain.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "disputes")
public class Dispute {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false, unique = true)
  private Transaction transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private Card card;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", length = 30, nullable = false)
  private DisputeReason reason;

  @Column(name = "description", length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 30, nullable = false)
  private DisputeStatus status;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "opened_by", length = 100, nullable = false)
  private String openedBy;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "evidence_due_at")
  private Instant evidenceDueAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by", length = 100)
  private String resolvedBy;

  @Column(name = "resolution_note", length = 1000)
  private String resolutionNote;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  protected Dispute() {}

  public Dispute(
      UUID id,
      Transaction transaction,
      Card card,
      DisputeReason reason,
      String description,
      String openedBy,
      Instant openedAt) {
    this.id = id != null ? id : UuidV7.newId();
    this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
    this.card = Objects.requireNonNull(card, "card cannot be null");
    this.reason = Objects.requireNonNull(reason, "reason cannot be null");
    this.description = description;
    this.status = DisputeStatus.OPENED;
    this.amountMinor = transaction.getAmountMinor();
    this.currency = transaction.getCurrency();
    this.openedBy = Objects.requireNonNull(openedBy, "openedBy cannot be null");
    this.openedAt = Objects.requireNonNull(openedAt, "openedAt cannot be null");
    this.version = null;
  }

  public void transitionTo(DisputeStatus target, String actor, String note, Instant now) {
    Objects.requireNonNull(target, "target status cannot be null");
    Objects.requireNonNull(actor, "actor cannot be null");
    Objects.requireNonNull(now, "now cannot be null");

    if (!this.status.canTransitionTo(target)) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Cannot transition dispute from " + this.status + " to " + target);
    }

    if (target.isTerminal()) {
      this.resolvedAt = now;
      this.resolvedBy = actor;
      this.resolutionNote = note;
    }

    this.status = target;
  }

  public void setEvidenceDueAt(Instant evidenceDueAt) {
    this.evidenceDueAt = evidenceDueAt;
  }

  public List<DisputeAction> getAllowedActions(Instant now) {
    Objects.requireNonNull(now, "now cannot be null");
    return switch (this.status) {
      case OPENED -> List.of(DisputeAction.REQUEST_EVIDENCE);
      case EVIDENCE_REQUESTED -> {
        if (this.evidenceDueAt != null && !now.isAfter(this.evidenceDueAt)) {
          yield List.of(DisputeAction.SUBMIT_EVIDENCE);
        }
        yield Collections.emptyList();
      }
      case UNDER_REVIEW -> List.of(DisputeAction.RESOLVE_WON, DisputeAction.RESOLVE_LOST);
      case RESOLVED_WON, RESOLVED_LOST -> Collections.emptyList();
    };
  }

  public UUID getId() {
    return id;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public UUID getTransactionId() {
    return transaction.getId();
  }

  public Card getCard() {
    return card;
  }

  public UUID getCardId() {
    return card.getId();
  }

  public DisputeReason getReason() {
    return reason;
  }

  public String getDescription() {
    return description;
  }

  public DisputeStatus getStatus() {
    return status;
  }

  public long getAmountMinor() {
    return amountMinor;
  }

  public String getCurrency() {
    return currency;
  }

  public String getOpenedBy() {
    return openedBy;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getEvidenceDueAt() {
    return evidenceDueAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getResolvedBy() {
    return resolvedBy;
  }

  public String getResolutionNote() {
    return resolutionNote;
  }

  public Long getVersion() {
    return version;
  }
}
