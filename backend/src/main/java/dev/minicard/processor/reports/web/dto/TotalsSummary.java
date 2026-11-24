package dev.minicard.processor.reports.web.dto;

import java.math.BigDecimal;

public record TotalsSummary(
    long attempts, long approved, long declined, long flagged, BigDecimal approvalRatePercent) {}
