package dev.minicard.processor.disputes.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveDisputeRequest(
    @NotNull DisputeOutcome outcome, @NotBlank @Size(max = 1000) String note) {}
