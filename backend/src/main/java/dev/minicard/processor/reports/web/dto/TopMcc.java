package dev.minicard.processor.reports.web.dto;

import java.util.List;

public record TopMcc(
    String mcc, String label, long attempts, List<VolumeByCurrency> approvedVolume) {}
