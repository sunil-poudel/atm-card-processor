package dev.minicard.processor.fraud.web.dto;

import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleAction;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudRuleResponse(
    UUID id,
    String name,
    String description,
    FraudRuleType type,
    boolean enabled,
    Long thresholdValue,
    Integer windowSeconds,
    List<String> mccCodes,
    int scorePoints,
    FraudRuleAction action,
    Instant createdAt,
    Instant updatedAt,
    Long version) {

  public static FraudRuleResponse fromEntity(FraudRule rule) {
    return new FraudRuleResponse(
        rule.getId(),
        rule.getName(),
        rule.getDescription(),
        rule.getRuleType(),
        rule.isEnabled(),
        rule.getThresholdValue(),
        rule.getWindowSeconds(),
        rule.getMccCodesList(),
        rule.getScorePoints(),
        rule.getAction(),
        rule.getCreatedAt(),
        rule.getUpdatedAt(),
        rule.getVersion() != null ? rule.getVersion() : 0L);
  }
}
