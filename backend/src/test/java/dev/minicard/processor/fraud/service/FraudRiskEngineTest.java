package dev.minicard.processor.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleAction;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.repository.FraudRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FraudRiskEngineTest {

  private FraudRuleRepository fraudRuleRepository;
  private AppProperties appProperties;
  private RuleEvaluator evaluator;
  private FraudRiskEngine engine;

  @BeforeEach
  void setUp() {
    fraudRuleRepository = mock(FraudRuleRepository.class);
    appProperties = mock(AppProperties.class);
    evaluator = mock(RuleEvaluator.class);

    AppProperties.FraudProperties fraudProps = new AppProperties.FraudProperties(50, 80);
    when(appProperties.fraud()).thenReturn(fraudProps);

    engine = new FraudRiskEngine(fraudRuleRepository, List.of(evaluator), appProperties);
  }

  @Test
  @DisplayName("no rules enabled results in clean APPROVE")
  void testNoRules() {
    when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of());

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    RiskAssessment assessment = engine.assess(card, req, Instant.now());
    assertThat(assessment.decision()).isEqualTo(RiskDecision.APPROVE);
    assertThat(assessment.score()).isZero();
    assertThat(assessment.ruleHits()).isEmpty();
  }

  @Test
  @DisplayName("single hit with FLAG action triggers FLAG decision (Example 12)")
  void testFlagHit() {
    FraudRule rule =
        new FraudRule(
            UUID.randomUUID(),
            "High value payment",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            5_000_000L,
            null,
            null,
            40,
            FraudRuleAction.FLAG,
            Instant.now(),
            Instant.now(),
            0L);

    when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of(rule));
    when(evaluator.supports(FraudRuleType.MAX_AMOUNT)).thenReturn(true);
    when(evaluator.evaluate(any(), any(), any(), any()))
        .thenReturn(
            Optional.of(
                new RuleHit(
                    rule.getId(),
                    rule.getName(),
                    rule.getRuleType().name(),
                    40,
                    "FLAG",
                    "amount 6000000 exceeds 5000000")));

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(6_000_000L, "NPR", "5411");

    RiskAssessment assessment = engine.assess(card, req, Instant.now());
    assertThat(assessment.decision()).isEqualTo(RiskDecision.FLAG);
    assertThat(assessment.score()).isEqualTo(40);
    assertThat(assessment.ruleHits()).hasSize(1);
  }

  @Test
  @DisplayName("single hit with DECLINE action triggers DECLINE decision (Example 13 & 14)")
  void testDeclineHit() {
    FraudRule rule =
        new FraudRule(
            UUID.randomUUID(),
            "Rapid-fire attempts",
            "desc",
            FraudRuleType.VELOCITY,
            true,
            5L,
            60,
            null,
            60,
            FraudRuleAction.DECLINE,
            Instant.now(),
            Instant.now(),
            0L);

    when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of(rule));
    when(evaluator.supports(FraudRuleType.VELOCITY)).thenReturn(true);
    when(evaluator.evaluate(any(), any(), any(), any()))
        .thenReturn(
            Optional.of(
                new RuleHit(
                    rule.getId(),
                    rule.getName(),
                    rule.getRuleType().name(),
                    60,
                    "DECLINE",
                    "5 prior attempts in 60s (threshold 5)")));

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    RiskAssessment assessment = engine.assess(card, req, Instant.now());
    assertThat(assessment.decision()).isEqualTo(RiskDecision.DECLINE);
    assertThat(assessment.score()).isEqualTo(60);
    assertThat(assessment.ruleHits()).hasSize(1);
  }

  @Test
  @DisplayName(
      "accumulated score >= declineThreshold triggers DECLINE even if rule actions are FLAG")
  void testScoreThresholdDecline() {
    FraudRule rule1 =
        new FraudRule(
            UUID.randomUUID(),
            "Rule 1",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            100L,
            null,
            null,
            45,
            FraudRuleAction.FLAG,
            Instant.now(),
            Instant.now(),
            0L);
    FraudRule rule2 =
        new FraudRule(
            UUID.randomUUID(),
            "Rule 2",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            100L,
            null,
            null,
            40,
            FraudRuleAction.FLAG,
            Instant.now(),
            Instant.now(),
            0L);

    when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of(rule1, rule2));
    when(evaluator.supports(FraudRuleType.MAX_AMOUNT)).thenReturn(true);
    when(evaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              FraudRule r = inv.getArgument(0);
              if (r.getId().equals(rule1.getId())) {
                return Optional.of(
                    new RuleHit(rule1.getId(), "Rule 1", "MAX_AMOUNT", 45, "FLAG", "detail 1"));
              } else {
                return Optional.of(
                    new RuleHit(rule2.getId(), "Rule 2", "MAX_AMOUNT", 40, "FLAG", "detail 2"));
              }
            });

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    RiskAssessment assessment = engine.assess(card, req, Instant.now());
    assertThat(assessment.score()).isEqualTo(85);
    assertThat(assessment.decision()).isEqualTo(RiskDecision.DECLINE);
    assertThat(assessment.ruleHits()).hasSize(2);
  }

  @Test
  @DisplayName("score is capped at 100")
  void testScoreCap() {
    FraudRule rule1 =
        new FraudRule(
            UUID.randomUUID(),
            "Rule 1",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            100L,
            null,
            null,
            70,
            FraudRuleAction.FLAG,
            Instant.now(),
            Instant.now(),
            0L);
    FraudRule rule2 =
        new FraudRule(
            UUID.randomUUID(),
            "Rule 2",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            100L,
            null,
            null,
            70,
            FraudRuleAction.FLAG,
            Instant.now(),
            Instant.now(),
            0L);

    when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of(rule1, rule2));
    when(evaluator.supports(FraudRuleType.MAX_AMOUNT)).thenReturn(true);
    when(evaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              FraudRule r = inv.getArgument(0);
              return Optional.of(
                  new RuleHit(r.getId(), r.getName(), "MAX_AMOUNT", 70, "FLAG", "detail"));
            });

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    RiskAssessment assessment = engine.assess(card, req, Instant.now());
    assertThat(assessment.score()).isEqualTo(100);
    assertThat(assessment.decision()).isEqualTo(RiskDecision.DECLINE);
  }
}
