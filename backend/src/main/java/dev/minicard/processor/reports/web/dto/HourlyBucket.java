package dev.minicard.processor.reports.web.dto;

public record HourlyBucket(int hour, long attempts, long approved, long declined) {}
