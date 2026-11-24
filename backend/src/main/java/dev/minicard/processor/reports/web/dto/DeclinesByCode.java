package dev.minicard.processor.reports.web.dto;

public record DeclinesByCode(String responseCode, String message, long count) {}
