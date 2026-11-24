package dev.minicard.processor.cards.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockCardRequest(
    @NotBlank @Size(min = 3, max = 200, message = "reason must be between 3 and 200 characters")
        String reason) {}
