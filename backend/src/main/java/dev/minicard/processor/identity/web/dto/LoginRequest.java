package dev.minicard.processor.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials for back-office staff authentication")
public record LoginRequest(
    @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Schema(example = "admin@minicard.local")
        String email,
    @NotBlank(message = "Password is required") @Schema(example = "AdminPassword123!")
        String password) {}
