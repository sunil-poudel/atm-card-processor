package dev.minicard.processor.transactions.repository;

import dev.minicard.processor.transactions.domain.Transaction;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByIdempotencyScopeAndIdempotencyKey(
      String idempotencyScope, String idempotencyKey);

  boolean existsByIdempotencyScopeAndIdempotencyKey(String idempotencyScope, String idempotencyKey);

  @Query("SELECT COUNT(t) FROM Transaction t WHERE t.card.id = :cardId AND t.createdAt >= :since")
  long countByCardIdAndCreatedAtGreaterThanEqual(
      @Param("cardId") UUID cardId, @Param("since") Instant since);
}
