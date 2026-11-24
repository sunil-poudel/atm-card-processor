package dev.minicard.processor.fraud.service.evaluators;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.service.AttemptCounter;
import dev.minicard.processor.fraud.service.AuthContext;
import dev.minicard.processor.fraud.service.RuleEvaluator;
import dev.minicard.processor.fraud.service.RuleHit;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class VelocityEvaluator implements RuleEvaluator {

  private final AttemptCounter attemptCounter;

  public VelocityEvaluator(AttemptCounter attemptCounter) {
    this.attemptCounter = attemptCounter;
  }

  @Override
  public boolean supports(FraudRuleType ruleType) {
    return ruleType == FraudRuleType.VELOCITY;
  }

  @Override
  public Optional<RuleHit> evaluate(FraudRule rule, Card card, AuthContext request, Instant now) {
    Long threshold = rule.getThresholdValue();
    Integer windowSeconds = rule.getWindowSeconds();
    if (threshold == null || windowSeconds == null) {
      return Optional.empty();
    }

    Instant since = now.minusSeconds(windowSeconds);
    long count = attemptCounter.countAttemptsSince(card.getId(), since);

    if (count >= threshold) {
      String detail =
          count + " prior attempts in " + windowSeconds + "s (threshold " + threshold + ")";
      return Optional.of(
          new RuleHit(
              rule.getId(),
              rule.getName(),
              rule.getRuleType().name(),
              rule.getScorePoints(),
              rule.getAction().name(),
              detail));
    }
    return Optional.empty();
  }
}
