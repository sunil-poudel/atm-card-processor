package dev.minicard.processor.identity.web.dto;

import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.domain.ApiClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "API client response containing the one-time raw API key")
public record ApiClientCreatedResponse(
    UUID id,
    String name,
    String keyPrefix,
    ApiClientStatus status,
    Instant createdAt,
    Instant revokedAt,
    Instant lastUsedAt,
    @Schema(
            description = "Full API key, returned ONCE upon creation only",
            example = "mck_a1b2c3d4_...")
        String apiKey) {

  public static ApiClientCreatedResponse from(ApiClient client, String fullKey) {
    return new ApiClientCreatedResponse(
        client.getId(),
        client.getName(),
        client.getKeyPrefix(),
        client.getStatus(),
        client.getCreatedAt(),
        client.getRevokedAt(),
        client.getLastUsedAt(),
        fullKey);
  }
}
