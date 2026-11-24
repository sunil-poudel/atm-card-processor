package dev.minicard.processor.cards.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record IssueCardRequest(
    @NotBlank
        @Size(min = 2, max = 100)
        @Pattern(
            regexp = "^[\\p{L} .'-]+$",
            message = "holderName may only contain letters, spaces, dots, apostrophes and hyphens")
        String holderName,
    @NotBlank @Pattern(regexp = "^(NPR|USD)$", message = "currency must be NPR or USD")
        String currency,
    @Min(1) @Max(5) Integer validityYears,
    @Min(1) @Max(10000000000L) Long perTransactionLimitMinor,
    @Min(1) @Max(10000000000L) Long dailyLimitMinor,
    @Min(0) @Max(10000000000L) Long initialBalanceMinor,
    @Size(max = 50)
        Set<@Pattern(regexp = "^\\d{4}$", message = "each MCC must be 4 digits") String>
            blockedMccs) {}
