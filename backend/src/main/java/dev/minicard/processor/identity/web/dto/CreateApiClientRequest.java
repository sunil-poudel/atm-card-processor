package dev.minicard.processor.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to register a new API client machine caller")
public record CreateApiClientRequest(
    @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        @Schema(example = "Simulator Acquirer Client")
        String name) {}
