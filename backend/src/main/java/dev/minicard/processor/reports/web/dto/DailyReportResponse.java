package dev.minicard.processor.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportResponse(
    LocalDate date,
    String zone,
    long attempts,
    long approved,
    long declined,
    long flagged,
    BigDecimal approvalRatePercent,
    List<VolumeByCurrency> approvedVolume,
    List<DeclinesByCode> declinesByCode,
    List<TopMcc> topMccs,
    List<HourlyBucket> hourly) {}
