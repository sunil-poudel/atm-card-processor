package dev.minicard.processor.security;

import java.util.Objects;
import java.util.UUID;

public record CallerIdentity(UUID apiClientId, UUID userId, String scope, String actorLabel) {

  public CallerIdentity {
    if (apiClientId == null && userId == null) {
      throw new IllegalArgumentException("Either apiClientId or userId must be provided");
    }
    if (apiClientId != null && userId != null) {
      throw new IllegalArgumentException("Cannot provide both apiClientId and userId");
    }
    Objects.requireNonNull(scope, "scope cannot be null");
    Objects.requireNonNull(actorLabel, "actorLabel cannot be null");
  }

  public static CallerIdentity forApiClient(UUID apiClientId, String clientName) {
    return new CallerIdentity(apiClientId, null, "CLIENT:" + apiClientId, clientName);
  }

  public static CallerIdentity forUser(UUID userId, String email) {
    return new CallerIdentity(null, userId, "USER:" + userId, email);
  }
}
