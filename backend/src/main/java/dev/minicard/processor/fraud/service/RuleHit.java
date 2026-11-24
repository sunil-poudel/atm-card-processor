package dev.minicard.processor.fraud.service;

import java.util.UUID;

public record RuleHit(
    UUID ruleId, String ruleName, String ruleType, int points, String action, String detail) {}
