package dev.minicard.processor.identity.web.dto;

import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.domain.ApiClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Details of an API client credential")
public record ApiClientResponse(
    UUID id,
    String name,
    String keyPrefix,
    ApiClientStatus status,
    Instant createdAt,
    Instant revokedAt,
    Instant lastUsedAt) {

  public static ApiClientResponse from(ApiClient client) {
    return new ApiClientResponse(
        client.getId(),
        client.getName(),
        client.getKeyPrefix(),
        client.getStatus(),
        client.getCreatedAt(),
        client.getRevokedAt(),
        client.getLastUsedAt());
  }
}
