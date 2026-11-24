package dev.minicard.processor.cards.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateLimitsRequest(
    @NotNull @Min(1) @Max(10000000000L) Long perTransactionLimitMinor,
    @NotNull @Min(1) @Max(10000000000L) Long dailyLimitMinor,
    @NotNull Long version) {}
