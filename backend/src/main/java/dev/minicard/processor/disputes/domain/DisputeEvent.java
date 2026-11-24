package dev.minicard.processor.disputes.domain;

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
@Table(name = "dispute_events")
public class DisputeEvent {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dispute_id", nullable = false)
  private Dispute dispute;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 30)
  private DisputeStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", length = 30, nullable = false)
  private DisputeStatus toStatus;

  @Column(name = "actor", length = 100, nullable = false)
  private String actor;

  @Column(name = "note", length = 1000)
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DisputeEvent() {}

  public DisputeEvent(
      UUID id,
      Dispute dispute,
      DisputeStatus fromStatus,
      DisputeStatus toStatus,
      String actor,
      String note,
      Instant createdAt) {
    this.id = id != null ? id : UuidV7.newId();
    this.dispute = Objects.requireNonNull(dispute, "dispute cannot be null");
    this.fromStatus = fromStatus;
    this.toStatus = Objects.requireNonNull(toStatus, "toStatus cannot be null");
    this.actor = Objects.requireNonNull(actor, "actor cannot be null");
    this.note = note;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
  }

  public UUID getId() {
    return id;
  }

  public Dispute getDispute() {
    return dispute;
  }

  public DisputeStatus getFromStatus() {
    return fromStatus;
  }

  public DisputeStatus getToStatus() {
    return toStatus;
  }

  public String getActor() {
    return actor;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
