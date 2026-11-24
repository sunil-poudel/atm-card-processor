package dev.minicard.processor.disputes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.disputes.domain.Dispute;
import dev.minicard.processor.disputes.domain.DisputeReason;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.disputes.repository.DisputeEventRepository;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.disputes.web.dto.DisputeDetail;
import dev.minicard.processor.disputes.web.dto.DisputeOutcome;
import dev.minicard.processor.disputes.web.dto.OpenDisputeRequest;
import dev.minicard.processor.disputes.web.dto.ResolveDisputeRequest;
import dev.minicard.processor.disputes.web.dto.SubmitEvidenceRequest;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.ledger.service.LedgerService;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

  @Mock private DisputeRepository disputeRepository;
  @Mock private DisputeEventRepository disputeEventRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private ApiClientRepository apiClientRepository;
  @Mock private UserRepository userRepository;
  @Mock private LedgerService ledgerService;
  @Mock private AuditService auditService;
  @Mock private AppProperties appProperties;
  @Mock private AppProperties.DisputesProperties disputesProperties;

  private Clock clock;
  private Instant now;
  private DisputeService disputeService;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-10-20T10:00:00Z");
    clock = Clock.fixed(now, ZoneOffset.UTC);

    lenient().when(appProperties.disputes()).thenReturn(disputesProperties);
    lenient().when(disputesProperties.maxAgeDays()).thenReturn(120);
    lenient().when(disputesProperties.evidenceDays()).thenReturn(7);

    disputeService =
        new DisputeService(
            disputeRepository,
            disputeEventRepository,
            transactionRepository,
            apiClientRepository,
            userRepository,
            ledgerService,
            auditService,
            appProperties,
            clock,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  @Test
  @DisplayName("openDispute fails if transaction does not exist")
  void openDisputeUnknownTransaction() {
    UUID txId = UUID.randomUUID();
    when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

    OpenDisputeRequest request =
        new OpenDisputeRequest(txId, DisputeReason.UNAUTHORIZED_TRANSACTION, "Not me");

    assertThatThrownBy(
            () ->
                disputeService.openDispute(
                    request,
                    UUID.randomUUID(),
                    "officer@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(NotFoundException.class)
        .satisfies(
            ex ->
                assertThat(((NotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
  }

  @Test
  @DisplayName("openDispute fails if transaction is not APPROVED")
  void openDisputeDeclinedTransaction() {
    UUID txId = UUID.randomUUID();
    Transaction tx = mock(Transaction.class);
    when(tx.getStatus()).thenReturn(TransactionStatus.DECLINED);
    when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

    OpenDisputeRequest request =
        new OpenDisputeRequest(txId, DisputeReason.UNAUTHORIZED_TRANSACTION, "Not me");

    assertThatThrownBy(
            () ->
                disputeService.openDispute(
                    request,
                    UUID.randomUUID(),
                    "officer@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(UnprocessableException.class)
        .satisfies(
            ex ->
                assertThat(((UnprocessableException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DISPUTE_NOT_ELIGIBLE));
  }

  @Test
  @DisplayName("openDispute fails if transaction is older than maxAgeDays")
  void openDisputeTooOldTransaction() {
    UUID txId = UUID.randomUUID();
    Transaction tx = mock(Transaction.class);
    when(tx.getStatus()).thenReturn(TransactionStatus.APPROVED);
    when(tx.getCreatedAt()).thenReturn(now.minus(121, ChronoUnit.DAYS));
    when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

    OpenDisputeRequest request =
        new OpenDisputeRequest(txId, DisputeReason.UNAUTHORIZED_TRANSACTION, "Not me");

    assertThatThrownBy(
            () ->
                disputeService.openDispute(
                    request,
                    UUID.randomUUID(),
                    "officer@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(UnprocessableException.class)
        .satisfies(
            ex ->
                assertThat(((UnprocessableException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DISPUTE_NOT_ELIGIBLE));
  }

  @Test
  @DisplayName("openDispute fails if dispute already exists for transaction")
  void openDisputeDuplicate() {
    UUID txId = UUID.randomUUID();
    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn(txId);
    when(tx.getStatus()).thenReturn(TransactionStatus.APPROVED);
    when(tx.getCreatedAt()).thenReturn(now.minus(10, ChronoUnit.DAYS));
    when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
    when(disputeRepository.existsByTransactionId(txId)).thenReturn(true);

    OpenDisputeRequest request =
        new OpenDisputeRequest(txId, DisputeReason.UNAUTHORIZED_TRANSACTION, "Not me");

    assertThatThrownBy(
            () ->
                disputeService.openDispute(
                    request,
                    UUID.randomUUID(),
                    "officer@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(ConflictException.class)
        .satisfies(
            ex ->
                assertThat(((ConflictException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DISPUTE_ALREADY_EXISTS));
  }

  @Test
  @DisplayName("openDispute succeeds on eligible approved transaction")
  void openDisputeSuccess() {
    UUID txId = UUID.randomUUID();
    Card card = mock(Card.class);
    when(card.getId()).thenReturn(UUID.randomUUID());
    when(card.getLast4()).thenReturn("1234");
    when(card.getHolderName()).thenReturn("Hari");

    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn(txId);
    when(tx.getCard()).thenReturn(card);
    when(tx.getStatus()).thenReturn(TransactionStatus.APPROVED);
    when(tx.getCreatedAt()).thenReturn(now.minus(5, ChronoUnit.DAYS));
    when(tx.getAmountMinor()).thenReturn(50000L);
    when(tx.getCurrency()).thenReturn("NPR");
    when(tx.getMerchantName()).thenReturn("Supermarket");
    when(tx.getMcc()).thenReturn("5411");
    when(tx.getResponseCode()).thenReturn("00");

    when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
    when(disputeRepository.existsByTransactionId(txId)).thenReturn(false);
    when(disputeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(disputeEventRepository.findByDisputeIdOrderByCreatedAtAsc(any()))
        .thenReturn(Collections.emptyList());

    OpenDisputeRequest request =
        new OpenDisputeRequest(txId, DisputeReason.UNAUTHORIZED_TRANSACTION, "Customer was abroad");

    UUID actorId = UUID.randomUUID();
    DisputeDetail detail =
        disputeService.openDispute(
            request, actorId, "officer@minicard.dev", ActorType.USER, "10.0.0.1");

    assertThat(detail).isNotNull();
    assertThat(detail.status()).isEqualTo(DisputeStatus.OPENED);
    assertThat(detail.reason()).isEqualTo(DisputeReason.UNAUTHORIZED_TRANSACTION);
    assertThat(detail.amountMinor()).isEqualTo(50000L);

    verify(disputeEventRepository).save(any());
    verify(auditService)
        .record(
            eq(ActorType.USER),
            eq(actorId),
            eq("officer@minicard.dev"),
            eq("DISPUTE_OPENED"),
            eq("Dispute"),
            any(),
            eq("10.0.0.1"),
            anyString());
  }

  @Test
  @DisplayName("submitEvidence after evidence deadline throws 409 with exact message")
  void submitEvidenceDeadlinePassed() {
    UUID disputeId = UUID.randomUUID();
    Dispute dispute = mock(Dispute.class);
    when(dispute.getStatus()).thenReturn(DisputeStatus.EVIDENCE_REQUESTED);
    when(dispute.getEvidenceDueAt()).thenReturn(now.minusSeconds(1)); // passed
    when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

    SubmitEvidenceRequest request = new SubmitEvidenceRequest("Proof of receipt");

    assertThatThrownBy(
            () ->
                disputeService.submitEvidence(
                    disputeId,
                    request,
                    UUID.randomUUID(),
                    "officer@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(ConflictException.class)
        .satisfies(
            ex -> {
              ConflictException ce = (ConflictException) ex;
              assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
              assertThat(ce.getMessage()).isEqualTo("evidence deadline passed");
            });
  }

  @Test
  @DisplayName("resolve WON on CLOSED card throws 409 with exact message and does not credit")
  void resolveWonOnClosedCard() {
    UUID disputeId = UUID.randomUUID();
    Card card = mock(Card.class);
    when(card.getStatus()).thenReturn(CardStatus.CLOSED);

    Dispute dispute = mock(Dispute.class);
    when(dispute.getStatus()).thenReturn(DisputeStatus.UNDER_REVIEW);
    when(dispute.getCard()).thenReturn(card);
    when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

    ResolveDisputeRequest request =
        new ResolveDisputeRequest(DisputeOutcome.WON, "Legitimate claim");

    assertThatThrownBy(
            () ->
                disputeService.resolveDispute(
                    disputeId,
                    request,
                    UUID.randomUUID(),
                    "officer@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(ConflictException.class)
        .satisfies(
            ex -> {
              ConflictException ce = (ConflictException) ex;
              assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
              assertThat(ce.getMessage()).isEqualTo("card is closed");
            });

    verify(ledgerService, never())
        .recordDisputeCredit(any(), any(), anyLong(), anyString(), anyString());
  }

  @Test
  @DisplayName("resolve WON on ACTIVE card credits balance via LedgerService")
  void resolveWonOnActiveCard() {
    UUID disputeId = UUID.randomUUID();
    Card card = mock(Card.class);
    when(card.getStatus()).thenReturn(CardStatus.ACTIVE);
    when(card.getLast4()).thenReturn("1234");
    when(card.getHolderName()).thenReturn("Hari");

    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn(UUID.randomUUID());
    when(tx.getCard()).thenReturn(card);
    when(tx.getAmountMinor()).thenReturn(20000L);
    when(tx.getCurrency()).thenReturn("NPR");
    when(tx.getMerchantName()).thenReturn("Store");
    when(tx.getMcc()).thenReturn("5411");
    when(tx.getResponseCode()).thenReturn("00");

    Dispute dispute =
        new Dispute(
            disputeId,
            tx,
            card,
            DisputeReason.UNAUTHORIZED_TRANSACTION,
            "Card stolen",
            "officer@minicard.dev",
            now.minus(5, ChronoUnit.DAYS));
    dispute.transitionTo(DisputeStatus.EVIDENCE_REQUESTED, "officer", "need proof", now);
    dispute.transitionTo(DisputeStatus.UNDER_REVIEW, "officer", "proof provided", now);

    when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
    when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(disputeEventRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId))
        .thenReturn(Collections.emptyList());

    ResolveDisputeRequest request =
        new ResolveDisputeRequest(DisputeOutcome.WON, "Legitimate claim");

    UUID actorId = UUID.randomUUID();
    DisputeDetail detail =
        disputeService.resolveDispute(
            disputeId, request, actorId, "officer@minicard.dev", ActorType.USER, "127.0.0.1");

    assertThat(detail.status()).isEqualTo(DisputeStatus.RESOLVED_WON);
    verify(ledgerService)
        .recordDisputeCredit(
            eq(card),
            eq(disputeId),
            eq(20000L),
            eq("Dispute won: " + disputeId),
            eq("officer@minicard.dev"));
    verify(auditService)
        .record(
            eq(ActorType.USER),
            eq(actorId),
            eq("officer@minicard.dev"),
            eq("DISPUTE_RESOLVED"),
            eq("Dispute"),
            eq(disputeId),
            eq("127.0.0.1"),
            anyString());
  }

  @Test
  @DisplayName("resolve LOST does not move money")
  void resolveLostMovesNoMoney() {
    UUID disputeId = UUID.randomUUID();
    Card card = mock(Card.class);
    when(card.getLast4()).thenReturn("1234");
    when(card.getHolderName()).thenReturn("Hari");

    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn(UUID.randomUUID());
    when(tx.getCard()).thenReturn(card);
    when(tx.getAmountMinor()).thenReturn(20000L);
    when(tx.getCurrency()).thenReturn("NPR");
    when(tx.getMerchantName()).thenReturn("Store");
    when(tx.getMcc()).thenReturn("5411");
    when(tx.getResponseCode()).thenReturn("00");

    Dispute dispute =
        new Dispute(
            disputeId,
            tx,
            card,
            DisputeReason.UNAUTHORIZED_TRANSACTION,
            "Card stolen",
            "officer@minicard.dev",
            now.minus(5, ChronoUnit.DAYS));
    dispute.transitionTo(DisputeStatus.EVIDENCE_REQUESTED, "officer", "need proof", now);
    dispute.transitionTo(DisputeStatus.UNDER_REVIEW, "officer", "proof provided", now);

    when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
    when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(disputeEventRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId))
        .thenReturn(Collections.emptyList());

    ResolveDisputeRequest request =
        new ResolveDisputeRequest(DisputeOutcome.LOST, "Merchant proved delivery");

    UUID actorId = UUID.randomUUID();
    DisputeDetail detail =
        disputeService.resolveDispute(
            disputeId, request, actorId, "officer@minicard.dev", ActorType.USER, "127.0.0.1");

    assertThat(detail.status()).isEqualTo(DisputeStatus.RESOLVED_LOST);
    verify(ledgerService, never())
        .recordDisputeCredit(any(), any(), anyLong(), anyString(), anyString());
    verify(auditService)
        .record(
            eq(ActorType.USER),
            eq(actorId),
            eq("officer@minicard.dev"),
            eq("DISPUTE_RESOLVED"),
            eq("Dispute"),
            eq(disputeId),
            eq("127.0.0.1"),
            anyString());
  }
}
