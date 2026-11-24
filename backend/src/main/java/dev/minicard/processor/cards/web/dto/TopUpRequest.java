package dev.minicard.processor.cards.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TopUpRequest(
    @NotNull @Min(1) @Max(10000000000L) Long amountMinor, @Size(max = 200) String description) {}
