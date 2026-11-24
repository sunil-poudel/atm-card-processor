package dev.minicard.processor.cards.service;

import dev.minicard.processor.cards.domain.Card;
import java.util.UUID;

public interface CardLedgerService {

  void recordInitialLoad(
      Card card, long amountMinor, UUID actorId, String actorLabel, String clientIp);
}
