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

class BlockedMccEvaluatorTest {

  private final BlockedMccEvaluator evaluator = new BlockedMccEvaluator();

  @Test
  @DisplayName("supports only BLOCKED_MCC")
  void testSupports() {
    assertThat(evaluator.supports(FraudRuleType.BLOCKED_MCC)).isTrue();
    assertThat(evaluator.supports(FraudRuleType.MAX_AMOUNT)).isFalse();
    assertThat(evaluator.supports(FraudRuleType.VELOCITY)).isFalse();
  }

  @Test
  @DisplayName("mcc not in blocked list does not trigger hit")
  void testMccAllowed() {
    FraudRule rule =
        new FraudRule(
            UUID.randomUUID(),
            "Restricted merchant categories",
            "desc",
            FraudRuleType.BLOCKED_MCC,
            true,
            null,
            null,
            "7995,6011",
            100,
            FraudRuleAction.DECLINE,
            Instant.now(),
            Instant.now(),
            0L);

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "5411");

    assertThat(evaluator.evaluate(rule, card, req, Instant.now())).isEmpty();
  }

  @Test
  @DisplayName("mcc in blocked list triggers hit with exact detail format")
  void testMccBlocked() {
    FraudRule rule =
        new FraudRule(
            UUID.randomUUID(),
            "Restricted merchant categories",
            "desc",
            FraudRuleType.BLOCKED_MCC,
            true,
            null,
            null,
            "7995,6011",
            100,
            FraudRuleAction.DECLINE,
            Instant.now(),
            Instant.now(),
            0L);

    Card card = mock(Card.class);
    AuthContext req = new AuthContext(1000L, "NPR", "7995");

    Optional<RuleHit> hitOpt = evaluator.evaluate(rule, card, req, Instant.now());
    assertThat(hitOpt).isPresent();

    RuleHit hit = hitOpt.get();
    assertThat(hit.ruleId()).isEqualTo(rule.getId());
    assertThat(hit.ruleName()).isEqualTo("Restricted merchant categories");
    assertThat(hit.ruleType()).isEqualTo("BLOCKED_MCC");
    assertThat(hit.points()).isEqualTo(100);
    assertThat(hit.action()).isEqualTo("DECLINE");
    assertThat(hit.detail()).isEqualTo("mcc 7995 is restricted");
  }
}
