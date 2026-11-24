package dev.minicard.processor.disputes.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitEvidenceRequest(@NotBlank @Size(min = 3, max = 1000) String note) {}
