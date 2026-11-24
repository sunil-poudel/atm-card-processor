package dev.minicard.processor.fraud.repository;

import dev.minicard.processor.fraud.domain.FraudRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

  List<FraudRule> findByEnabledTrueOrderByNameAsc();

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  Optional<FraudRule> findByNameIgnoreCase(String name);
}
