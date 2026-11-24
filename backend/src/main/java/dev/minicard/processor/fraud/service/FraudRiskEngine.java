package dev.minicard.processor.fraud.service;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.repository.FraudRuleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class FraudRiskEngine implements RiskEngine {

  private final FraudRuleRepository fraudRuleRepository;
  private final List<RuleEvaluator> evaluators;
  private final AppProperties appProperties;

  public FraudRiskEngine(
      FraudRuleRepository fraudRuleRepository,
      List<RuleEvaluator> evaluators,
      AppProperties appProperties) {
    this.fraudRuleRepository = fraudRuleRepository;
    this.evaluators = evaluators;
    this.appProperties = appProperties;
  }

  @Override
  public RiskAssessment assess(Card card, AuthContext request, Instant now) {
    List<FraudRule> rules = fraudRuleRepository.findByEnabledTrueOrderByNameAsc();
    List<RuleHit> hits = new ArrayList<>();

    for (FraudRule rule : rules) {
      RuleEvaluator evaluator = findEvaluator(rule.getRuleType());
      if (evaluator != null) {
        evaluator.evaluate(rule, card, request, now).ifPresent(hits::add);
      }
    }

    int score = Math.min(100, hits.stream().mapToInt(RuleHit::points).sum());

    int declineThreshold = appProperties.fraud().declineThreshold();
    int flagThreshold = appProperties.fraud().flagThreshold();

    boolean hasDeclineHit = hits.stream().anyMatch(h -> "DECLINE".equalsIgnoreCase(h.action()));
    boolean hasFlagHit = hits.stream().anyMatch(h -> "FLAG".equalsIgnoreCase(h.action()));

    RiskDecision decision;
    if (hasDeclineHit || score >= declineThreshold) {
      decision = RiskDecision.DECLINE;
    } else if (hasFlagHit || score >= flagThreshold) {
      decision = RiskDecision.FLAG;
    } else {
      decision = RiskDecision.APPROVE;
    }

    return new RiskAssessment(decision, score, hits);
  }

  private RuleEvaluator findEvaluator(FraudRuleType ruleType) {
    for (RuleEvaluator evaluator : evaluators) {
      if (evaluator.supports(ruleType)) {
        return evaluator;
      }
    }
    return null;
  }
}
