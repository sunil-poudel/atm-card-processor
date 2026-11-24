package dev.minicard.processor.fraud.service;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import java.time.Instant;
import java.util.Optional;

public interface RuleEvaluator {

  boolean supports(FraudRuleType ruleType);

  Optional<RuleHit> evaluate(FraudRule rule, Card card, AuthContext request, Instant now);
}
