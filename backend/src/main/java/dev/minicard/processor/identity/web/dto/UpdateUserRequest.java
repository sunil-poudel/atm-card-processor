package dev.minicard.processor.identity.web.dto;

import dev.minicard.processor.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to update a back-office user")
public record UpdateUserRequest(
    @Size(max = 100, message = "Display name must not exceed 100 characters")
        @Schema(example = "John Updated")
        String displayName,
    @Schema(example = "DISPUTE_OFFICER") UserRole role,
    @Schema(example = "true") Boolean enabled,
    @Schema(example = "NewSecretPassword123!") String newPassword,
    @NotNull(message = "Version is required for optimistic locking") @Schema(example = "0")
        Long version) {}
