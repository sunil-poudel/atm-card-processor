package dev.minicard.processor.fraud.service;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface AttemptCounter {

  long countAttemptsSince(UUID cardId, Instant since);
}
