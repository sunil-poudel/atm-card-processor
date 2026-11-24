package dev.minicard.processor.audit.web;

import dev.minicard.processor.audit.service.AuditQueryService;
import dev.minicard.processor.audit.web.dto.AuditEventResponse;
import dev.minicard.processor.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit", description = "Audit event query operations")
public class AuditController {

  private final AuditQueryService auditQueryService;

  public AuditController(AuditQueryService auditQueryService) {
    this.auditQueryService = auditQueryService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Query audit events with filters and pagination")
  public ResponseEntity<PageResponse<AuditEventResponse>> listAuditEvents(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) String actorLabel,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(
        auditQueryService.listAuditEvents(
            action, entityType, entityId, actorLabel, from, to, pageable));
  }
}
