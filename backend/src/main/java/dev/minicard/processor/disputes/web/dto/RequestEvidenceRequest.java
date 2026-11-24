package dev.minicard.processor.disputes.web.dto;

import jakarta.validation.constraints.Size;

public record RequestEvidenceRequest(@Size(max = 1000) String note) {}
