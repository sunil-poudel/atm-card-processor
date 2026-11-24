package dev.minicard.processor.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.ledger.domain.LedgerEntry;
import dev.minicard.processor.ledger.domain.LedgerEntryType;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {

  private LedgerEntryRepository ledgerEntryRepository;
  private CardRepository cardRepository;
  private Clock clock;
  private LedgerService ledgerService;

  private final Instant now = Instant.parse("2026-10-21T07:00:00Z");

  @BeforeEach
  void setUp() {
    ledgerEntryRepository = mock(LedgerEntryRepository.class);
    cardRepository = mock(CardRepository.class);
    dev.minicard.processor.audit.service.AuditService auditService =
        mock(dev.minicard.processor.audit.service.AuditService.class);
    clock = Clock.fixed(now, ZoneOffset.UTC);
    ledgerService =
        new LedgerService(
            ledgerEntryRepository,
            cardRepository,
            auditService,
            clock,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Card buildCard(CardStatus status, long balance) {
    Card card =
        new Card(
            UUID.randomUUID(),
            "enc",
            "k1",
            "hash",
            "990000",
            "1234",
            "Sita Sharma",
            10,
            2029,
            "NPR",
            5000000L,
            20000000L,
            Set.of(),
            UUID.randomUUID(),
            now);
    if (status == CardStatus.ACTIVE) {
      card.activate(now);
    } else if (status == CardStatus.BLOCKED) {
      card.block("Stolen", now);
    } else if (status == CardStatus.CLOSED) {
      card.close(now);
    }
    card.updateBalance(balance);
    return card;
  }

  @Test
  @DisplayName("recordTopUp increases balance and creates TOPUP ledger entry")
  void recordTopUpSuccess() {
    Card card = buildCard(CardStatus.ACTIVE, 1000L);

    LedgerEntry entry = ledgerService.recordTopUp(card, 500L, "ATM load", "admin@minicard.local");

    assertThat(card.getBalanceMinor()).isEqualTo(1500L);
    assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.TOPUP);
    assertThat(entry.getAmountMinor()).isEqualTo(500L);
    assertThat(entry.getBalanceAfterMinor()).isEqualTo(1500L);
    assertThat(entry.getTransactionId()).isNull();
    assertThat(entry.getDisputeId()).isNull();
    assertThat(entry.getCreatedBy()).isEqualTo("admin@minicard.local");
  }

  @Test
  @DisplayName("recordTopUp fails on BLOCKED or CLOSED cards")
  void recordTopUpStatusCheck() {
    Card blockedCard = buildCard(CardStatus.BLOCKED, 1000L);
    assertThatThrownBy(() -> ledgerService.recordTopUp(blockedCard, 500L, "load", "admin"))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION);

    Card closedCard = buildCard(CardStatus.CLOSED, 0L);
    assertThatThrownBy(() -> ledgerService.recordTopUp(closedCard, 500L, "load", "admin"))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION);
  }

  @Test
  @DisplayName("recordTopUp detects balance overflow and throws AMOUNT_OVERFLOW")
  void recordTopUpOverflow() {
    Card card = buildCard(CardStatus.ACTIVE, Long.MAX_VALUE - 100);

    assertThatThrownBy(() -> ledgerService.recordTopUp(card, 200L, "load", "admin"))
        .isInstanceOf(UnprocessableException.class)
        .matches(e -> ((UnprocessableException) e).getErrorCode() == ErrorCode.AMOUNT_OVERFLOW);
  }

  @Test
  @DisplayName(
      "recordPurchase decreases balance and creates PURCHASE ledger entry with negative amount")
  void recordPurchaseSuccess() {
    Card card = buildCard(CardStatus.ACTIVE, 2000L);
    UUID txnId = UUID.randomUUID();

    LedgerEntry entry = ledgerService.recordPurchase(card, txnId, 800L, "Grocery", "POS Simulator");

    assertThat(card.getBalanceMinor()).isEqualTo(1200L);
    assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.PURCHASE);
    assertThat(entry.getAmountMinor()).isEqualTo(-800L);
    assertThat(entry.getBalanceAfterMinor()).isEqualTo(1200L);
    assertThat(entry.getTransactionId()).isEqualTo(txnId);
    assertThat(entry.getDisputeId()).isNull();
  }

  @Test
  @DisplayName("recordPurchase throws VALIDATION_FAILED when amount exceeds balance")
  void recordPurchaseInsufficientFunds() {
    Card card = buildCard(CardStatus.ACTIVE, 500L);
    UUID txnId = UUID.randomUUID();

    assertThatThrownBy(() -> ledgerService.recordPurchase(card, txnId, 600L, "item", "POS"))
        .isInstanceOf(UnprocessableException.class)
        .matches(e -> ((UnprocessableException) e).getErrorCode() == ErrorCode.VALIDATION_FAILED);
  }

  @Test
  @DisplayName(
      "recordDisputeCredit increases balance and throws DISPUTE_ALREADY_EXISTS on duplicate")
  void recordDisputeCreditSuccessAndDuplicateCheck() {
    Card card = buildCard(CardStatus.ACTIVE, 1000L);
    UUID disputeId = UUID.randomUUID();
    when(ledgerEntryRepository.existsByDisputeId(disputeId)).thenReturn(false);

    LedgerEntry entry =
        ledgerService.recordDisputeCredit(card, disputeId, 500L, "Dispute won", "officer");

    assertThat(card.getBalanceMinor()).isEqualTo(1500L);
    assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.DISPUTE_CREDIT);
    assertThat(entry.getAmountMinor()).isEqualTo(500L);
    assertThat(entry.getBalanceAfterMinor()).isEqualTo(1500L);
    assertThat(entry.getDisputeId()).isEqualTo(disputeId);

    // Duplicate check
    when(ledgerEntryRepository.existsByDisputeId(disputeId)).thenReturn(true);
    assertThatThrownBy(
            () -> ledgerService.recordDisputeCredit(card, disputeId, 500L, "re-credit", "officer"))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.DISPUTE_ALREADY_EXISTS);
  }
}
