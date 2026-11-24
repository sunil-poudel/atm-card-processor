package dev.minicard.processor.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "audit_events")
@Immutable
public class AuditEvent {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false, updatable = false)
  private ActorType actorType;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(name = "actor_label", nullable = false, updatable = false)
  private String actorLabel;

  @Column(name = "action", nullable = false, updatable = false)
  private String action;

  @Column(name = "entity_type", updatable = false)
  private String entityType;

  @Column(name = "entity_id", updatable = false)
  private UUID entityId;

  @Column(name = "request_id", updatable = false)
  private String requestId;

  @Column(name = "ip_address", updatable = false)
  private String ipAddress;

  @Column(name = "details", updatable = false)
  private String details;

  protected AuditEvent() {}

  public AuditEvent(
      UUID id,
      Instant occurredAt,
      ActorType actorType,
      UUID actorId,
      String actorLabel,
      String action,
      String entityType,
      UUID entityId,
      String requestId,
      String ipAddress,
      String details) {
    this.id = id;
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    this.actorType = Objects.requireNonNull(actorType, "actorType cannot be null");
    this.actorId = actorId;
    this.actorLabel = Objects.requireNonNull(actorLabel, "actorLabel cannot be null");
    this.action = Objects.requireNonNull(action, "action cannot be null");
    this.entityType = entityType;
    this.entityId = entityId;
    this.requestId = requestId;
    this.ipAddress = ipAddress;
    this.details = details;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public ActorType getActorType() {
    return actorType;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorLabel() {
    return actorLabel;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getDetails() {
    return details;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AuditEvent that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
