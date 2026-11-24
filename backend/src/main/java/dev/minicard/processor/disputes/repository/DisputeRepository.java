package dev.minicard.processor.disputes.repository;

import dev.minicard.processor.disputes.domain.Dispute;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository
    extends JpaRepository<Dispute, UUID>, JpaSpecificationExecutor<Dispute> {

  @Query("SELECT d FROM Dispute d WHERE d.transaction.id = :transactionId")
  Optional<Dispute> findByTransactionId(@Param("transactionId") UUID transactionId);

  @Query(
      "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dispute d WHERE d.transaction.id = :transactionId")
  boolean existsByTransactionId(@Param("transactionId") UUID transactionId);

  long countByStatusNotIn(Collection<DisputeStatus> statuses);

  @Query(
      value =
          "SELECT id FROM disputes WHERE status = 'EVIDENCE_REQUESTED' AND evidence_due_at < :now ORDER BY evidence_due_at ASC LIMIT :limit",
      nativeQuery = true)
  List<UUID> findOverdueDisputeIds(@Param("now") Instant now, @Param("limit") int limit);

  @Query(value = "SELECT * FROM disputes WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
  Optional<Dispute> findByIdForUpdateSkipLocked(@Param("id") UUID id);
}
