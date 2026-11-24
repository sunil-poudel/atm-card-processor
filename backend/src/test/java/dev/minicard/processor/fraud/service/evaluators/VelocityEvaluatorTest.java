package dev.minicard.processor.fraud.service.evaluators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleAction;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.service.AuthContext;
import dev.minicard.processor.fraud.service.RuleHit;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VelocityEvaluatorTest {

  @Test
  @DisplayName("supports only VELOCITY")
  void testSupports() {
    VelocityEvaluator evaluator = new VelocityEvaluator((cardId, since) -> 0L);
    assertThat(evaluator.supports(FraudRuleType.VELOCITY)).isTrue();
    assertThat(evaluator.supports(FraudRuleType.MAX_AMOUNT)).isFalse();
    assertThat(evaluator.supports(FraudRuleType.BLOCKED_MCC)).isFalse();
  }

  @Test
  @DisplayName("prior attempts below threshold does not trigger hit")
  void testBelowThreshold() {
    VelocityEvaluator evaluator = new VelocityEvaluator((cardId, since) -> 4L);

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

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    assertThat(evaluator.evaluate(rule, card, req, Instant.now())).isEmpty();
  }

  @Test
  @DisplayName(
      "prior attempts equal to or greater than threshold triggers hit with exact detail format")
  void testEqualOrAboveThreshold() {
    AtomicReference<Instant> capturedSince = new AtomicReference<>();
    VelocityEvaluator evaluator =
        new VelocityEvaluator(
            (cardId, since) -> {
              capturedSince.set(since);
              return 5L;
            });

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

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    Instant now = Instant.parse("2026-10-20T12:00:00Z");
    Optional<RuleHit> hitOpt = evaluator.evaluate(rule, card, req, now);

    assertThat(hitOpt).isPresent();
    assertThat(capturedSince.get()).isEqualTo(Instant.parse("2026-10-20T11:59:00Z"));

    RuleHit hit = hitOpt.get();
    assertThat(hit.ruleId()).isEqualTo(rule.getId());
    assertThat(hit.ruleName()).isEqualTo("Rapid-fire attempts");
    assertThat(hit.ruleType()).isEqualTo("VELOCITY");
    assertThat(hit.points()).isEqualTo(60);
    assertThat(hit.action()).isEqualTo("DECLINE");
    assertThat(hit.detail()).isEqualTo("5 prior attempts in 60s (threshold 5)");
  }
}
