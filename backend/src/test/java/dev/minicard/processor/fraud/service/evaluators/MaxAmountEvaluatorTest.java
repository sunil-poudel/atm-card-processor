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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaxAmountEvaluatorTest {

  private final MaxAmountEvaluator evaluator = new MaxAmountEvaluator();

  @Test
  @DisplayName("supports only MAX_AMOUNT")
  void testSupports() {
    assertThat(evaluator.supports(FraudRuleType.MAX_AMOUNT)).isTrue();
    assertThat(evaluator.supports(FraudRuleType.VELOCITY)).isFalse();
    assertThat(evaluator.supports(FraudRuleType.BLOCKED_MCC)).isFalse();
  }

  @Test
  @DisplayName("amount below or equal to threshold does not trigger hit")
  void testBelowOrEqualThreshold() {
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

    Card card = mock(Card.class);
    AuthContext reqEqual = new AuthContext(5_000_000L, "NPR", "5411");
    AuthContext reqBelow = new AuthContext(4_999_999L, "NPR", "5411");

    assertThat(evaluator.evaluate(rule, card, reqEqual, Instant.now())).isEmpty();
    assertThat(evaluator.evaluate(rule, card, reqBelow, Instant.now())).isEmpty();
  }

  @Test
  @DisplayName("amount above threshold triggers hit with exact detail format")
  void testAboveThreshold() {
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

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(6_000_000L, "NPR", "5411");

    Optional<RuleHit> hitOpt = evaluator.evaluate(rule, card, req, Instant.now());
    assertThat(hitOpt).isPresent();

    RuleHit hit = hitOpt.get();
    assertThat(hit.ruleId()).isEqualTo(rule.getId());
    assertThat(hit.ruleName()).isEqualTo("High value payment");
    assertThat(hit.ruleType()).isEqualTo("MAX_AMOUNT");
    assertThat(hit.points()).isEqualTo(40);
    assertThat(hit.action()).isEqualTo("FLAG");
    assertThat(hit.detail()).isEqualTo("amount 6000000 exceeds 5000000");
  }
}
