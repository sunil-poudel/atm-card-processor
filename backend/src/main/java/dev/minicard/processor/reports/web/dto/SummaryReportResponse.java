package dev.minicard.processor.reports.web.dto;

import java.time.LocalDate;
import java.util.List;

public record SummaryReportResponse(
    LocalDate from,
    LocalDate to,
    String zone,
    TotalsSummary totals,
    List<VolumeByCurrency> approvedVolume,
    List<DailySeriesEntry> series,
    long openDisputes,
    long activeCards) {}
