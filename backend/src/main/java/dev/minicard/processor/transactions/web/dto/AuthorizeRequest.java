package dev.minicard.processor.transactions.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AuthorizeRequest(
    @NotNull(message = "cardId is required") UUID cardId,
    @NotNull(message = "amountMinor is required")
        @Positive(message = "amountMinor must be greater than zero")
        @jakarta.validation.constraints.Max(
            value = 10_000_000_000L,
            message = "amountMinor cannot exceed 10,000,000,000")
        Long amountMinor,
    @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter code")
        String currency,
    @NotBlank(message = "merchantName is required")
        @Size(max = 100, message = "merchantName cannot exceed 100 characters")
        String merchantName,
    @NotBlank(message = "mcc is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "mcc must be exactly 4 digits")
        String mcc,
    @Size(max = 64, message = "reference cannot exceed 64 characters") String reference) {}
