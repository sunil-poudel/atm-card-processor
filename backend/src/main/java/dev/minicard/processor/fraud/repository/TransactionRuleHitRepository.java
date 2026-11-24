package dev.minicard.processor.fraud.repository;

import dev.minicard.processor.fraud.domain.TransactionRuleHit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRuleHitRepository extends JpaRepository<TransactionRuleHit, UUID> {

  List<TransactionRuleHit> findByTransactionId(UUID transactionId);

  List<TransactionRuleHit> findByTransactionIdOrderByRuleNameAsc(UUID transactionId);

  List<TransactionRuleHit> findByTransactionIdIn(Collection<UUID> transactionIds);
}
