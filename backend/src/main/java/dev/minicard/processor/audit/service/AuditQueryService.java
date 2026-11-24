package dev.minicard.processor.audit.service;

import dev.minicard.processor.audit.domain.AuditEvent;
import dev.minicard.processor.audit.repository.AuditEventRepository;
import dev.minicard.processor.audit.web.dto.AuditEventResponse;
import dev.minicard.processor.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

  private final AuditEventRepository auditEventRepository;

  public AuditQueryService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  public PageResponse<AuditEventResponse> listAuditEvents(
      String action,
      String entityType,
      UUID entityId,
      String actorLabel,
      Instant from,
      Instant to,
      Pageable pageable) {

    Specification<AuditEvent> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (action != null && !action.isBlank()) {
            predicates.add(cb.equal(root.get("action"), action.trim()));
          }
          if (entityType != null && !entityType.isBlank()) {
            predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
          }
          if (entityId != null) {
            predicates.add(cb.equal(root.get("entityId"), entityId));
          }
          if (actorLabel != null && !actorLabel.isBlank()) {
            predicates.add(
                cb.like(
                    cb.lower(root.get("actorLabel")), "%" + actorLabel.trim().toLowerCase() + "%"));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThan(root.get("occurredAt"), to));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<AuditEvent> page = auditEventRepository.findAll(spec, pageable);
    return PageResponse.from(page.map(AuditEventResponse::fromEntity));
  }
}
