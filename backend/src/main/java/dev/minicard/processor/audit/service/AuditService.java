package dev.minicard.processor.audit.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.domain.AuditEvent;
import dev.minicard.processor.audit.repository.AuditEventRepository;
import dev.minicard.processor.common.web.RequestIdFilter;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditEventRepository auditEventRepository;
  private final Clock clock;

  public AuditService(AuditEventRepository auditEventRepository, Clock clock) {
    this.auditEventRepository = auditEventRepository;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      ActorType actorType,
      UUID actorId,
      String actorLabel,
      String action,
      String entityType,
      UUID entityId,
      String ipAddress,
      String details) {

    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    AuditEvent event =
        new AuditEvent(
            null,
            clock.instant(),
            actorType,
            actorId,
            actorLabel,
            action,
            entityType,
            entityId,
            requestId,
            ipAddress,
            details);

    auditEventRepository.save(event);
    log.info(
        "AUDIT: action={}, actorType={}, actorLabel={}, entityType={}, entityId={}",
        action,
        actorType,
        actorLabel,
        entityType,
        entityId);
  }
}
