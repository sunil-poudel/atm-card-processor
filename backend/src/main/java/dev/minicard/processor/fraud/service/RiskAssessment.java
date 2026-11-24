package dev.minicard.processor.fraud.service;

import java.util.Collections;
import java.util.List;

public record RiskAssessment(RiskDecision decision, int score, List<RuleHit> ruleHits) {

  public static RiskAssessment approveClean() {
    return new RiskAssessment(RiskDecision.APPROVE, 0, Collections.emptyList());
  }
}
