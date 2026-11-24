package dev.minicard.processor.disputes.repository;

import dev.minicard.processor.disputes.domain.DisputeEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeEventRepository extends JpaRepository<DisputeEvent, UUID> {

  List<DisputeEvent> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);
}
