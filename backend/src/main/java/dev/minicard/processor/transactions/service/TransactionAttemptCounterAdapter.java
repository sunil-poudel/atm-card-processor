package dev.minicard.processor.transactions.service;

import dev.minicard.processor.fraud.service.AttemptCounter;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TransactionAttemptCounterAdapter implements AttemptCounter {

  private final TransactionRepository transactionRepository;

  public TransactionAttemptCounterAdapter(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  @Override
  public long countAttemptsSince(UUID cardId, Instant since) {
    return transactionRepository.countByCardIdAndCreatedAtGreaterThanEqual(cardId, since);
  }
}
