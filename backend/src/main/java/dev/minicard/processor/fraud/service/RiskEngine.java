package dev.minicard.processor.fraud.service;

import dev.minicard.processor.cards.domain.Card;
import java.time.Instant;

public interface RiskEngine {

  RiskAssessment assess(Card card, AuthContext request, Instant now);
}
