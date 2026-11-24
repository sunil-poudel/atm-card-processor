package dev.minicard.processor.disputes.web.dto;

import dev.minicard.processor.disputes.domain.DisputeReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record OpenDisputeRequest(
    @NotNull UUID transactionId,
    @NotNull DisputeReason reason,
    @Size(max = 1000) String description) {}
