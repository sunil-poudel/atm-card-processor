package dev.minicard.processor.fraud.service.evaluators;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.service.AuthContext;
import dev.minicard.processor.fraud.service.RuleEvaluator;
import dev.minicard.processor.fraud.service.RuleHit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BlockedMccEvaluator implements RuleEvaluator {

  @Override
  public boolean supports(FraudRuleType ruleType) {
    return ruleType == FraudRuleType.BLOCKED_MCC;
  }

  @Override
  public Optional<RuleHit> evaluate(FraudRule rule, Card card, AuthContext request, Instant now) {
    List<String> blockedMccs = rule.getMccCodesList();
    String mcc = request.mcc() != null ? request.mcc().trim() : "";

    if (blockedMccs.contains(mcc)) {
      String detail = "mcc " + mcc + " is restricted";
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
