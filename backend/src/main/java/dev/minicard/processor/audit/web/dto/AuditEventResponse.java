package dev.minicard.processor.audit.web.dto;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
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

  public static AuditEventResponse fromEntity(AuditEvent e) {
    return new AuditEventResponse(
        e.getId(),
        e.getOccurredAt(),
        e.getActorType(),
        e.getActorId(),
        e.getActorLabel(),
        e.getAction(),
        e.getEntityType(),
        e.getEntityId(),
        e.getRequestId(),
        e.getIpAddress(),
        e.getDetails());
  }
}
