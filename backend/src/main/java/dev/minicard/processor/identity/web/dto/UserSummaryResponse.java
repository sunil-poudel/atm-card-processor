package dev.minicard.processor.identity.web.dto;

import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Summary representation of an authenticated user")
public record UserSummaryResponse(UUID id, String email, String displayName, UserRole role) {

  public static UserSummaryResponse from(AppUser user) {
    return new UserSummaryResponse(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
  }
}
