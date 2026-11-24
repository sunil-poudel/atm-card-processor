package dev.minicard.processor.disputes.web.dto;

import dev.minicard.processor.disputes.domain.DisputeEvent;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import java.time.Instant;
import java.util.UUID;

public record DisputeEventResponse(
    UUID id,
    DisputeStatus fromStatus,
    DisputeStatus toStatus,
    String actor,
    String note,
    Instant createdAt) {

  public static DisputeEventResponse fromEntity(DisputeEvent event) {
    return new DisputeEventResponse(
        event.getId(),
        event.getFromStatus(),
        event.getToStatus(),
        event.getActor(),
        event.getNote(),
        event.getCreatedAt());
  }
}
