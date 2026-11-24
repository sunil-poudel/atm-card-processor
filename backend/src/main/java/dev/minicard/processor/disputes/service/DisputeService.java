package dev.minicard.processor.disputes.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.disputes.domain.Dispute;
import dev.minicard.processor.disputes.domain.DisputeEvent;
import dev.minicard.processor.disputes.domain.DisputeReason;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.disputes.repository.DisputeEventRepository;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.disputes.web.dto.DisputeDetail;
import dev.minicard.processor.disputes.web.dto.DisputeEventResponse;
import dev.minicard.processor.disputes.web.dto.DisputeOutcome;
import dev.minicard.processor.disputes.web.dto.DisputeResponse;
import dev.minicard.processor.disputes.web.dto.OpenDisputeRequest;
import dev.minicard.processor.disputes.web.dto.RequestEvidenceRequest;
import dev.minicard.processor.disputes.web.dto.ResolveDisputeRequest;
import dev.minicard.processor.disputes.web.dto.SubmitEvidenceRequest;
import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.ledger.service.LedgerService;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import dev.minicard.processor.transactions.service.TransactionDisputeLookup;
import dev.minicard.processor.transactions.web.dto.DisputeSummaryResponse;
import dev.minicard.processor.transactions.web.dto.TransactionSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisputeService implements TransactionDisputeLookup {

  private final DisputeRepository disputeRepository;
  private final DisputeEventRepository disputeEventRepository;
  private final TransactionRepository transactionRepository;
  private final ApiClientRepository apiClientRepository;
  private final UserRepository userRepository;
  private final LedgerService ledgerService;
  private final AuditService auditService;
  private final AppProperties appProperties;
  private final Clock clock;
  private final MeterRegistry meterRegistry;

  public DisputeService(
      DisputeRepository disputeRepository,
      DisputeEventRepository disputeEventRepository,
      TransactionRepository transactionRepository,
      ApiClientRepository apiClientRepository,
      UserRepository userRepository,
      LedgerService ledgerService,
      AuditService auditService,
      AppProperties appProperties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.disputeRepository = disputeRepository;
    this.disputeEventRepository = disputeEventRepository;
    this.transactionRepository = transactionRepository;
    this.apiClientRepository = apiClientRepository;
    this.userRepository = userRepository;
    this.ledgerService = ledgerService;
    this.auditService = auditService;
    this.appProperties = appProperties;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public DisputeDetail openDispute(
      OpenDisputeRequest request,
      UUID actorId,
      String actorLabel,
      ActorType actorType,
      String ipAddress) {
    Objects.requireNonNull(request, "request cannot be null");
    Objects.requireNonNull(actorLabel, "actorLabel cannot be null");

    Transaction transaction =
        transactionRepository
            .findById(request.transactionId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction not found: " + request.transactionId()));

    if (transaction.getStatus() != TransactionStatus.APPROVED) {
      throw new UnprocessableException(
          ErrorCode.DISPUTE_NOT_ELIGIBLE,
          "Only approved transactions can be disputed; status: " + transaction.getStatus());
    }

    Instant now = clock.instant();
    int maxAgeDays = appProperties.disputes().maxAgeDays();
    Instant cutoff = now.minus(maxAgeDays, ChronoUnit.DAYS);
    if (transaction.getCreatedAt().isBefore(cutoff)) {
      throw new UnprocessableException(
          ErrorCode.DISPUTE_NOT_ELIGIBLE,
          "Transaction exceeds maximum dispute age of " + maxAgeDays + " days");
    }

    if (disputeRepository.existsByTransactionId(transaction.getId())) {
      throw new ConflictException(
          ErrorCode.DISPUTE_ALREADY_EXISTS,
          "Dispute already exists for transaction " + transaction.getId());
    }

    Dispute dispute =
        new Dispute(
            null,
            transaction,
            transaction.getCard(),
            request.reason(),
            request.description(),
            actorLabel,
            now);

    try {
      dispute = disputeRepository.saveAndFlush(dispute);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          ErrorCode.DISPUTE_ALREADY_EXISTS,
          "Dispute already exists for transaction " + transaction.getId());
    }

    DisputeEvent event =
        new DisputeEvent(
            null, dispute, null, DisputeStatus.OPENED, actorLabel, request.description(), now);
    disputeEventRepository.save(event);

    auditService.record(
        actorType,
        actorId,
        actorLabel,
        "DISPUTE_OPENED",
        "Dispute",
        dispute.getId(),
        ipAddress,
        "Reason: " + request.reason());

    Counter.builder("minicard.disputes.opened").register(meterRegistry).increment();

    return toDisputeDetail(dispute, now);
  }

  @Transactional(readOnly = true)
  public DisputeDetail getDispute(UUID id) {
    Dispute dispute =
        disputeRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(ErrorCode.DISPUTE_NOT_FOUND, "Dispute not found: " + id));
    return toDisputeDetail(dispute, clock.instant());
  }

  @Transactional(readOnly = true)
  public PageResponse<DisputeResponse> listDisputes(
      DisputeStatus status, DisputeReason reason, UUID cardId, Boolean overdue, Pageable pageable) {
    Instant now = clock.instant();

    Specification<Dispute> spec =
        (root, query, cb) -> {
          if (query != null
              && Long.class != query.getResultType()
              && long.class != query.getResultType()) {
            root.fetch("card", JoinType.LEFT);
          }

          List<Predicate> predicates = new ArrayList<>();

          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (reason != null) {
            predicates.add(cb.equal(root.get("reason"), reason));
          }
          if (cardId != null) {
            predicates.add(cb.equal(root.get("card").get("id"), cardId));
          }
          if (overdue != null) {
            Predicate isOverdue =
                cb.and(
                    cb.equal(root.get("status"), DisputeStatus.EVIDENCE_REQUESTED),
                    cb.lessThan(root.get("evidenceDueAt"), now));
            predicates.add(overdue ? isOverdue : cb.not(isOverdue));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<Dispute> page = disputeRepository.findAll(spec, pageable);
    return PageResponse.from(page.map(d -> DisputeResponse.fromEntity(d, now)));
  }

  @Transactional
  public DisputeDetail requestEvidence(
      UUID id,
      RequestEvidenceRequest request,
      UUID actorId,
      String actorLabel,
      ActorType actorType,
      String ipAddress) {
    Dispute dispute =
        disputeRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(ErrorCode.DISPUTE_NOT_FOUND, "Dispute not found: " + id));

    Instant now = clock.instant();
    Instant evidenceDueAt = now.plus(appProperties.disputes().evidenceDays(), ChronoUnit.DAYS);

    String note = request != null ? request.note() : null;
    dispute.transitionTo(DisputeStatus.EVIDENCE_REQUESTED, actorLabel, note, now);
    dispute.setEvidenceDueAt(evidenceDueAt);
    dispute = disputeRepository.save(dispute);

    DisputeEvent event =
        new DisputeEvent(
            null,
            dispute,
            DisputeStatus.OPENED,
            DisputeStatus.EVIDENCE_REQUESTED,
            actorLabel,
            note,
            now);
    disputeEventRepository.save(event);

    auditService.record(
        actorType,
        actorId,
        actorLabel,
        "DISPUTE_EVIDENCE_REQUESTED",
        "Dispute",
        dispute.getId(),
        ipAddress,
        note);

    return toDisputeDetail(dispute, now);
  }

  @Transactional
  public DisputeDetail submitEvidence(
      UUID id,
      SubmitEvidenceRequest request,
      UUID actorId,
      String actorLabel,
      ActorType actorType,
      String ipAddress) {
    Objects.requireNonNull(request, "request cannot be null");

    Dispute dispute =
        disputeRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(ErrorCode.DISPUTE_NOT_FOUND, "Dispute not found: " + id));

    Instant now = clock.instant();
    if (dispute.getStatus() != DisputeStatus.EVIDENCE_REQUESTED) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Cannot transition dispute from "
              + dispute.getStatus()
              + " to "
              + DisputeStatus.UNDER_REVIEW);
    }

    if (dispute.getEvidenceDueAt() != null && now.isAfter(dispute.getEvidenceDueAt())) {
      throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "evidence deadline passed");
    }

    dispute.transitionTo(DisputeStatus.UNDER_REVIEW, actorLabel, request.note(), now);
    dispute = disputeRepository.save(dispute);

    DisputeEvent event =
        new DisputeEvent(
            null,
            dispute,
            DisputeStatus.EVIDENCE_REQUESTED,
            DisputeStatus.UNDER_REVIEW,
            actorLabel,
            request.note(),
            now);
    disputeEventRepository.save(event);

    auditService.record(
        actorType,
        actorId,
        actorLabel,
        "DISPUTE_EVIDENCE_SUBMITTED",
        "Dispute",
        dispute.getId(),
        ipAddress,
        request.note());

    return toDisputeDetail(dispute, now);
  }

  @Transactional
  public DisputeDetail resolveDispute(
      UUID id,
      ResolveDisputeRequest request,
      UUID actorId,
      String actorLabel,
      ActorType actorType,
      String ipAddress) {
    Objects.requireNonNull(request, "request cannot be null");

    Dispute dispute =
        disputeRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(ErrorCode.DISPUTE_NOT_FOUND, "Dispute not found: " + id));

    DisputeStatus target =
        request.outcome() == DisputeOutcome.WON
            ? DisputeStatus.RESOLVED_WON
            : DisputeStatus.RESOLVED_LOST;

    if (dispute.getStatus() != DisputeStatus.UNDER_REVIEW) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Cannot transition dispute from " + dispute.getStatus() + " to " + target);
    }

    Instant now = clock.instant();

    if (target == DisputeStatus.RESOLVED_WON) {
      if (dispute.getCard().getStatus() == CardStatus.CLOSED) {
        throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "card is closed");
      }
      ledgerService.recordDisputeCredit(
          dispute.getCard(),
          dispute.getId(),
          dispute.getAmountMinor(),
          "Dispute won: " + dispute.getId(),
          actorLabel);
    }

    dispute.transitionTo(target, actorLabel, request.note(), now);
    dispute = disputeRepository.save(dispute);

    DisputeEvent event =
        new DisputeEvent(
            null, dispute, DisputeStatus.UNDER_REVIEW, target, actorLabel, request.note(), now);
    disputeEventRepository.save(event);

    auditService.record(
        actorType,
        actorId,
        actorLabel,
        "DISPUTE_RESOLVED",
        "Dispute",
        dispute.getId(),
        ipAddress,
        "Outcome: " + request.outcome() + ", Note: " + request.note());

    return toDisputeDetail(dispute, now);
  }

  private DisputeDetail toDisputeDetail(Dispute dispute, Instant now) {
    List<DisputeEventResponse> events =
        disputeEventRepository.findByDisputeIdOrderByCreatedAtAsc(dispute.getId()).stream()
            .map(DisputeEventResponse::fromEntity)
            .toList();

    Transaction t = dispute.getTransaction();
    String sourceName;
    if (t.getApiClientId() != null) {
      sourceName =
          apiClientRepository
              .findById(t.getApiClientId())
              .map(ApiClient::getName)
              .orElse("Unknown Client");
    } else if (t.getInitiatedByUserId() != null) {
      sourceName =
          userRepository
              .findById(t.getInitiatedByUserId())
              .map(AppUser::getEmail)
              .orElse("Unknown User");
    } else {
      sourceName = "SYSTEM";
    }

    TransactionSummary transactionSummary =
        TransactionSummary.fromEntity(
            t, dispute.getCard().getLast4(), dispute.getCard().getHolderName(), sourceName);

    return DisputeDetail.fromEntity(dispute, events, transactionSummary, now);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Optional<DisputeSummaryResponse> findDisputeSummary(UUID transactionId) {
    return disputeRepository
        .findByTransactionId(transactionId)
        .map(d -> new DisputeSummaryResponse(d.getId(), d.getStatus().name()));
  }
}
