package dev.minicard.processor.cards.repository;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, UUID>, JpaSpecificationExecutor<Card> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
  @Query("SELECT c FROM Card c WHERE c.id = :id")
  Optional<Card> findByIdForUpdate(@Param("id") UUID id);

  Optional<Card> findByPanHash(String panHash);

  boolean existsByPanHash(String panHash);

  long countByStatus(CardStatus status);
}
