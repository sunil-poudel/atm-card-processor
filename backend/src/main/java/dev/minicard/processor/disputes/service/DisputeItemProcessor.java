package dev.minicard.processor.disputes.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.disputes.domain.Dispute;
import dev.minicard.processor.disputes.domain.DisputeEvent;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.disputes.repository.DisputeEventRepository;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DisputeItemProcessor {

  private static final Logger log = LoggerFactory.getLogger(DisputeItemProcessor.class);

  private final DisputeRepository disputeRepository;
  private final DisputeEventRepository disputeEventRepository;
  private final AuditService auditService;
  private final Clock clock;
  private final MeterRegistry meterRegistry;

  public DisputeItemProcessor(
      DisputeRepository disputeRepository,
      DisputeEventRepository disputeEventRepository,
      AuditService auditService,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.disputeRepository = disputeRepository;
    this.disputeEventRepository = disputeEventRepository;
    this.auditService = auditService;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean processOverdueDispute(UUID disputeId) {
    Optional<Dispute> opt = disputeRepository.findByIdForUpdateSkipLocked(disputeId);
    if (opt.isEmpty()) {
      return false;
    }

    Dispute dispute = opt.get();
    Instant now = clock.instant();

    if (dispute.getStatus() != DisputeStatus.EVIDENCE_REQUESTED) {
      return false;
    }

    if (dispute.getEvidenceDueAt() == null || !now.isAfter(dispute.getEvidenceDueAt())) {
      return false;
    }

    dispute.transitionTo(DisputeStatus.RESOLVED_LOST, "SYSTEM", "Evidence deadline passed", now);
    disputeRepository.save(dispute);

    DisputeEvent event =
        new DisputeEvent(
            null,
            dispute,
            DisputeStatus.EVIDENCE_REQUESTED,
            DisputeStatus.RESOLVED_LOST,
            "SYSTEM",
            "Evidence deadline passed",
            now);
    disputeEventRepository.save(event);

    auditService.record(
        ActorType.SYSTEM,
        null,
        "SYSTEM",
        "DISPUTE_AUTO_CLOSED",
        "Dispute",
        dispute.getId(),
        null,
        "Evidence deadline passed");

    log.info("Dispute {} auto-closed as RESOLVED_LOST due to passed deadline", dispute.getId());
    Counter.builder("minicard.disputes.auto_closed").register(meterRegistry).increment();
    return true;
  }
}
