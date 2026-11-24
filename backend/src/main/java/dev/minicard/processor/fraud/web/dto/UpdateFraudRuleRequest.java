package dev.minicard.processor.fraud.web.dto;

import dev.minicard.processor.fraud.domain.FraudRuleAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateFraudRuleRequest(
    @NotBlank(message = "name is required")
        @Size(max = 100, message = "name cannot exceed 100 characters")
        String name,
    @Size(max = 300, message = "description cannot exceed 300 characters") String description,
    Boolean enabled,
    Long thresholdValue,
    Integer windowSeconds,
    List<String> mccCodes,
    @NotNull(message = "scorePoints is required")
        @Min(value = 1, message = "scorePoints must be between 1 and 100")
        @Max(value = 100, message = "scorePoints must be between 1 and 100")
        Integer scorePoints,
    @NotNull(message = "action is required") FraudRuleAction action,
    @NotNull(message = "version is required") Long version) {}
