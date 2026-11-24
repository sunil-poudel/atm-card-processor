package dev.minicard.processor.ledger.repository;

import dev.minicard.processor.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

  Page<LedgerEntry> findByCardIdOrderByCreatedAtDesc(UUID cardId, Pageable pageable);

  List<LedgerEntry> findByCardIdOrderByCreatedAtAsc(UUID cardId);

  @Query(
      "SELECT COALESCE(SUM(ABS(l.amountMinor)), 0) FROM LedgerEntry l "
          + "WHERE l.card.id = :cardId "
          + "AND l.entryType = dev.minicard.processor.ledger.domain.LedgerEntryType.PURCHASE "
          + "AND l.createdAt >= :startOfDay AND l.createdAt < :now")
  long sumSpentToday(
      @Param("cardId") UUID cardId,
      @Param("startOfDay") Instant startOfDay,
      @Param("now") Instant now);

  boolean existsByDisputeId(UUID disputeId);

  boolean existsByTransactionId(UUID transactionId);
}
