package dev.minicard.processor.identity.repository;

import dev.minicard.processor.identity.domain.ApiClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApiClientRepository
    extends JpaRepository<ApiClient, UUID>, JpaSpecificationExecutor<ApiClient> {

  Optional<ApiClient> findByKeyPrefix(String keyPrefix);

  Optional<ApiClient> findByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCase(String name);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE ApiClient c SET c.lastUsedAt = :now WHERE c.id = :id")
  void updateLastUsedAt(
      @org.springframework.data.repository.query.Param("id") UUID id,
      @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
