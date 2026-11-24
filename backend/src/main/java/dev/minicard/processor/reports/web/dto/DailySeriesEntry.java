package dev.minicard.processor.reports.web.dto;

import java.time.LocalDate;
import java.util.List;

public record DailySeriesEntry(
    LocalDate date,
    long approved,
    long declined,
    long flagged,
    List<VolumeByCurrency> approvedVolume) {}
