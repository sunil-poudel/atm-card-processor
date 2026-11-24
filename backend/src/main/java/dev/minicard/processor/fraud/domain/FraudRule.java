package dev.minicard.processor.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fraud_rules")
public class FraudRule {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 300)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_type", nullable = false, length = 20, updatable = false)
  private FraudRuleType ruleType;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "threshold_value")
  private Long thresholdValue;

  @Column(name = "window_seconds")
  private Integer windowSeconds;

  @Column(name = "mcc_codes", length = 300)
  private String mccCodes;

  @Column(name = "score_points", nullable = false)
  private int scorePoints;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 10)
  private FraudRuleAction action;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  protected FraudRule() {}

  public FraudRule(
      UUID id,
      String name,
      String description,
      FraudRuleType ruleType,
      boolean enabled,
      Long thresholdValue,
      Integer windowSeconds,
      String mccCodes,
      int scorePoints,
      FraudRuleAction action,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.ruleType = ruleType;
    this.enabled = enabled;
    this.thresholdValue = thresholdValue;
    this.windowSeconds = windowSeconds;
    this.mccCodes = mccCodes;
    this.scorePoints = scorePoints;
    this.action = action;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public FraudRuleType getRuleType() {
    return ruleType;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Long getThresholdValue() {
    return thresholdValue;
  }

  public void setThresholdValue(Long thresholdValue) {
    this.thresholdValue = thresholdValue;
  }

  public Integer getWindowSeconds() {
    return windowSeconds;
  }

  public void setWindowSeconds(Integer windowSeconds) {
    this.windowSeconds = windowSeconds;
  }

  public String getMccCodes() {
    return mccCodes;
  }

  public void setMccCodes(String mccCodes) {
    this.mccCodes = mccCodes;
  }

  public List<String> getMccCodesList() {
    if (mccCodes == null || mccCodes.isBlank()) {
      return Collections.emptyList();
    }
    return Arrays.stream(mccCodes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public int getScorePoints() {
    return scorePoints;
  }

  public void setScorePoints(int scorePoints) {
    this.scorePoints = scorePoints;
  }

  public FraudRuleAction getAction() {
    return action;
  }

  public void setAction(FraudRuleAction action) {
    this.action = action;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  public String toSummaryString() {
    return "name="
        + name
        + ",enabled="
        + enabled
        + ",type="
        + ruleType
        + ",threshold="
        + thresholdValue
        + ",window="
        + windowSeconds
        + ",mcc="
        + mccCodes
        + ",points="
        + scorePoints
        + ",action="
        + action;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FraudRule other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
