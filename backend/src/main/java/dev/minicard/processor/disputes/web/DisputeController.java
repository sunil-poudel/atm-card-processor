package dev.minicard.processor.disputes.web;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.disputes.domain.DisputeReason;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.disputes.service.DisputeService;
import dev.minicard.processor.disputes.web.dto.DisputeDetail;
import dev.minicard.processor.disputes.web.dto.DisputeResponse;
import dev.minicard.processor.disputes.web.dto.OpenDisputeRequest;
import dev.minicard.processor.disputes.web.dto.RequestEvidenceRequest;
import dev.minicard.processor.disputes.web.dto.ResolveDisputeRequest;
import dev.minicard.processor.disputes.web.dto.SubmitEvidenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disputes")
@Tag(name = "Disputes", description = "Dispute management and workflow operations")
public class DisputeController {

  private final DisputeService disputeService;

  public DisputeController(DisputeService disputeService) {
    this.disputeService = disputeService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'DISPUTE_OFFICER')")
  @Operation(summary = "Open a dispute on an approved transaction")
  public ResponseEntity<DisputeDetail> openDispute(
      @Valid @RequestBody OpenDisputeRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel =
        jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject();
    String ipAddress = httpRequest.getRemoteAddr();

    DisputeDetail detail =
        disputeService.openDispute(request, actorId, actorLabel, ActorType.USER, ipAddress);
    URI location = URI.create("/api/v1/disputes/" + detail.id());
    return ResponseEntity.created(location).body(detail);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "List disputes with optional filtering and pagination")
  public ResponseEntity<PageResponse<DisputeResponse>> listDisputes(
      @RequestParam(required = false) DisputeStatus status,
      @RequestParam(required = false) DisputeReason reason,
      @RequestParam(required = false) UUID cardId,
      @RequestParam(required = false) Boolean overdue,
      @PageableDefault(sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(
        disputeService.listDisputes(status, reason, cardId, overdue, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "Get dispute details by id")
  public ResponseEntity<DisputeDetail> getDispute(@PathVariable UUID id) {
    return ResponseEntity.ok(disputeService.getDispute(id));
  }

  @PostMapping("/{id}/request-evidence")
  @PreAuthorize("hasAnyRole('ADMIN', 'DISPUTE_OFFICER')")
  @Operation(summary = "Request evidence for an opened dispute")
  public ResponseEntity<DisputeDetail> requestEvidence(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) RequestEvidenceRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel =
        jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject();
    String ipAddress = httpRequest.getRemoteAddr();

    DisputeDetail detail =
        disputeService.requestEvidence(id, request, actorId, actorLabel, ActorType.USER, ipAddress);
    return ResponseEntity.ok(detail);
  }

  @PostMapping("/{id}/submit-evidence")
  @PreAuthorize("hasAnyRole('ADMIN', 'DISPUTE_OFFICER')")
  @Operation(summary = "Submit evidence for a dispute")
  public ResponseEntity<DisputeDetail> submitEvidence(
      @PathVariable UUID id,
      @Valid @RequestBody SubmitEvidenceRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel =
        jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject();
    String ipAddress = httpRequest.getRemoteAddr();

    DisputeDetail detail =
        disputeService.submitEvidence(id, request, actorId, actorLabel, ActorType.USER, ipAddress);
    return ResponseEntity.ok(detail);
  }

  @PostMapping("/{id}/resolve")
  @PreAuthorize("hasAnyRole('ADMIN', 'DISPUTE_OFFICER')")
  @Operation(summary = "Resolve a dispute under review")
  public ResponseEntity<DisputeDetail> resolveDispute(
      @PathVariable UUID id,
      @Valid @RequestBody ResolveDisputeRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel =
        jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject();
    String ipAddress = httpRequest.getRemoteAddr();

    DisputeDetail detail =
        disputeService.resolveDispute(id, request, actorId, actorLabel, ActorType.USER, ipAddress);
    return ResponseEntity.ok(detail);
  }
}
