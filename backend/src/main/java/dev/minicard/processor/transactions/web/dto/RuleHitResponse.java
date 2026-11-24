package dev.minicard.processor.transactions.web.dto;

import java.util.UUID;

public record RuleHitResponse(
    UUID ruleId, String ruleName, String ruleType, int points, String action, String detail) {}
