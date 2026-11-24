package dev.minicard.processor.cards.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateBlockedMccsRequest(
    @NotNull @Size(max = 50)
        Set<@Pattern(regexp = "^\\d{4}$", message = "each MCC must be 4 digits") String> mccs,
    @NotNull Long version) {}
