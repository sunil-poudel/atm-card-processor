package dev.minicard.processor.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transaction_rule_hits")
public class TransactionRuleHit {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "transaction_id", nullable = false, updatable = false)
  private UUID transactionId;

  @Column(name = "rule_id", nullable = false, updatable = false)
  private UUID ruleId;

  @Column(name = "rule_name", nullable = false, length = 100, updatable = false)
  private String ruleName;

  @Column(name = "rule_type", nullable = false, length = 20, updatable = false)
  private String ruleType;

  @Column(name = "points", nullable = false, updatable = false)
  private int points;

  @Column(name = "action", nullable = false, length = 10, updatable = false)
  private String action;

  @Column(name = "detail", length = 200, updatable = false)
  private String detail;

  protected TransactionRuleHit() {}

  public TransactionRuleHit(
      UUID id,
      UUID transactionId,
      UUID ruleId,
      String ruleName,
      String ruleType,
      int points,
      String action,
      String detail) {
    this.id = id;
    this.transactionId = transactionId;
    this.ruleId = ruleId;
    this.ruleName = ruleName;
    this.ruleType = ruleType;
    this.points = points;
    this.action = action;
    this.detail = detail;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public UUID getRuleId() {
    return ruleId;
  }

  public String getRuleName() {
    return ruleName;
  }

  public String getRuleType() {
    return ruleType;
  }

  public int getPoints() {
    return points;
  }

  public String getAction() {
    return action;
  }

  public String getDetail() {
    return detail;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TransactionRuleHit other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
