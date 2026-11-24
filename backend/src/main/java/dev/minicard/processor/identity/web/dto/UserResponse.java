package dev.minicard.processor.identity.web.dto;

import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Detailed representation of a back-office user")
public record UserResponse(
    UUID id,
    String email,
    String displayName,
    UserRole role,
    boolean enabled,
    boolean locked,
    Instant createdAt,
    Instant updatedAt,
    Long version) {

  public static UserResponse from(AppUser user, Instant now) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRole(),
        user.isEnabled(),
        user.isLocked(now),
        user.getCreatedAt(),
        user.getUpdatedAt(),
        user.getVersion());
  }
}
