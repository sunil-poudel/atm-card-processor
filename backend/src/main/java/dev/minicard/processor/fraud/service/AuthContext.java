package dev.minicard.processor.fraud.service;

public record AuthContext(long amountMinor, String currency, String mcc) {}
