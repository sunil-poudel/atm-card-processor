package dev.minicard.processor.identity.web.dto;

import dev.minicard.processor.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to create a new back-office user")
public record CreateUserRequest(
    @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Schema(example = "analyst@minicard.local")
        String email,
    @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        @Schema(example = "John Analyst")
        String displayName,
    @NotNull(message = "Role is required") @Schema(example = "ANALYST") UserRole role,
    @NotBlank(message = "Password is required") @Schema(example = "StrongPassword123!")
        String password) {}
