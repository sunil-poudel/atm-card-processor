package dev.minicard.processor.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.common.time.BusinessCalendar;
import dev.minicard.processor.fraud.service.RiskAssessment;
import dev.minicard.processor.fraud.service.RiskDecision;
import dev.minicard.processor.fraud.service.RiskEngine;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import dev.minicard.processor.ledger.service.LedgerService;
import dev.minicard.processor.security.CallerIdentity;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import dev.minicard.processor.transactions.web.dto.AuthorizeRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DecisionMatrixTest {

  private CardRepository cardRepository;
  private TransactionRepository transactionRepository;
  private dev.minicard.processor.fraud.repository.TransactionRuleHitRepository
      transactionRuleHitRepository;
  private LedgerEntryRepository ledgerEntryRepository;
  private LedgerService ledgerService;
  private RiskEngine riskEngine;
  private AppProperties appProperties;
  private BusinessCalendar businessCalendar;
  private Clock clock;
  private AuthorizationService service;

  private final ZoneId zone = ZoneId.of("Asia/Kathmandu");
  private final Instant baseNow = Instant.parse("2026-10-20T10:00:00Z");
  private final CallerIdentity caller = CallerIdentity.forApiClient(UUID.randomUUID(), "demo-pos");

  @BeforeEach
  void setUp() {
    cardRepository = mock(CardRepository.class);
    transactionRepository = mock(TransactionRepository.class);
    transactionRuleHitRepository =
        mock(dev.minicard.processor.fraud.repository.TransactionRuleHitRepository.class);
    ledgerEntryRepository = mock(LedgerEntryRepository.class);
    ledgerService = mock(LedgerService.class);
    riskEngine = mock(RiskEngine.class);
    appProperties = mock(AppProperties.class);
    clock = mock(Clock.class);

    when(clock.instant()).thenReturn(baseNow);
    when(appProperties.businessZone()).thenReturn(zone);

    businessCalendar = new BusinessCalendar(zone, clock);

    service =
        new AuthorizationService(
            cardRepository,
            transactionRepository,
            transactionRuleHitRepository,
            ledgerEntryRepository,
            ledgerService,
            riskEngine,
            appProperties,
            businessCalendar,
            clock,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

    when(riskEngine.assess(any(), any(), any())).thenReturn(RiskAssessment.approveClean());
    when(transactionRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Card createCard(
      long balanceMinor,
      long perTxnLimit,
      long dailyLimit,
      Set<String> blockedMccs,
      String currency) {
    UUID cardId = UUID.randomUUID();
    Card card =
        new Card(
            cardId,
            "cipher",
            "k1",
            "hash-" + cardId,
            "990000",
            "1234",
            "Sita Sharma",
            10,
            2029,
            currency,
            perTxnLimit,
            dailyLimit,
            blockedMccs,
            UUID.randomUUID(),
            baseNow);
    card.activate(baseNow);
    card.updateBalance(balanceMinor);
    return card;
  }

  @Test
  @DisplayName("Example 1: 20000 NPR, MCC 5411 -> APPROVED 00, balance 80000")
  void example1_approvedNormal() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 20000L, "NPR", "Demo Mart", "5411", "POS-1");
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex1", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.APPROVED);
    assertThat(result.response().responseCode()).isEqualTo("00");
    assertThat(result.response().responseMessage()).isEqualTo("Approved");

    verify(ledgerService)
        .recordPurchase(eq(card), any(UUID.class), eq(20000L), eq("Demo Mart"), eq("demo-pos"));
  }

  @Test
  @DisplayName("Example 2: 50000 NPR (equals per-txn limit) -> APPROVED 00, balance 50000")
  void example2_approvedAtLimit() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 50000L, "NPR", "Demo Mart", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex2", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.APPROVED);
    assertThat(result.response().responseCode()).isEqualTo("00");
  }

  @Test
  @DisplayName("Example 3: 50001 NPR -> DECLINED 61 LIMIT_PER_TRANSACTION")
  void example3_exceedsPerTransactionLimit() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 50001L, "NPR", "Demo Mart", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex3", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("61");
    assertThat(result.response().responseMessage()).isEqualTo("Exceeds amount limit");
    verify(ledgerService, never()).recordPurchase(any(), any(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("Example 4: Card INACTIVE -> DECLINED 62 CARD_NOT_ACTIVE")
  void example4_cardInactive() {
    Card card =
        new Card(
            UUID.randomUUID(),
            "cipher",
            "k1",
            "hash-inactive",
            "990000",
            "1234",
            "Sita Sharma",
            10,
            2029,
            "NPR",
            50000L,
            120000L,
            Collections.emptySet(),
            UUID.randomUUID(),
            baseNow);
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 100L, "NPR", "Demo Mart", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex4", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("62");
  }

  @Test
  @DisplayName("Example 5: Card BLOCKED -> DECLINED 62 CARD_NOT_ACTIVE")
  void example5_cardBlocked() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    card.block("Suspected compromised", baseNow);
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 100L, "NPR", "Demo Mart", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex5", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("62");
  }

  @Test
  @DisplayName("Example 6: Expiry month passed -> DECLINED 54 CARD_EXPIRED")
  void example6_cardExpired() {
    Card card =
        new Card(
            UUID.randomUUID(),
            "cipher",
            "k1",
            "hash-exp",
            "990000",
            "1234",
            "Sita Sharma",
            5,
            2026,
            "NPR",
            50000L,
            120000L,
            Collections.emptySet(),
            UUID.randomUUID(),
            baseNow);
    card.activate(baseNow);
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 100L, "NPR", "Demo Mart", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex6", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("54");
  }

  @Test
  @DisplayName("Example 7: 100 USD on NPR card -> DECLINED 12 CURRENCY_MISMATCH")
  void example7_currencyMismatch() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 100L, "USD", "Demo Mart", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex7", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("12");
  }

  @Test
  @DisplayName("Example 8: Card blocks MCC 6011, request MCC 6011 -> DECLINED 57 MCC_RESTRICTED")
  void example8_mccRestricted() {
    Card card = createCard(100000L, 50000L, 120000L, Set.of("6011", "7995"), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 1000L, "NPR", "ATM Demo", "6011", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex8", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("57");
  }

  @Test
  @DisplayName(
      "Example 9: Daily limit breach: 3 payments of 40000 approved, 4th payment of 1 NPR -> DECLINED 61 LIMIT_DAILY")
  void example9_dailyLimitBreach() {
    Card card = createCard(500000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    // Already spent 120000 today
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(120000L);

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 1L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex9", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("61");
  }

  @Test
  @DisplayName("Example 10: Balance 100, request 101 -> DECLINED 51 INSUFFICIENT_FUNDS")
  void example10_insufficientFunds() {
    Card card = createCard(100L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 101L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex10", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("51");
  }

  @Test
  @DisplayName("Example 11: Balance 100, request 100 -> APPROVED 00, balance 0")
  void example11_exactBalanceApproved() {
    Card card = createCard(100L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 100L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex11", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.APPROVED);
    assertThat(result.response().responseCode()).isEqualTo("00");
  }

  @Test
  @DisplayName("Example 12: Fraud hook FLAG (score 40) -> APPROVED 00 flagged=true")
  void example12_fraudFlagApproved() {
    Card card = createCard(10_000_000L, 10_000_000L, 20_000_000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);
    when(riskEngine.assess(any(), any(), any()))
        .thenReturn(new RiskAssessment(RiskDecision.FLAG, 40, Collections.emptyList()));

    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 6_000_000L, "NPR", "Jewelers", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex12", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.APPROVED);
    assertThat(result.response().responseCode()).isEqualTo("00");

    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().isFlagged()).isTrue();
    assertThat(captor.getValue().getRiskScore()).isEqualTo(40);
  }

  @Test
  @DisplayName("Example 13: Fraud hook DECLINE (score 60) -> DECLINED 59 SUSPECTED_FRAUD")
  void example13_fraudDecline() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);
    when(riskEngine.assess(any(), any(), any()))
        .thenReturn(new RiskAssessment(RiskDecision.DECLINE, 60, Collections.emptyList()));

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 1000L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex13", req);

    assertThat(result.response().status()).isEqualTo(TransactionStatus.DECLINED);
    assertThat(result.response().responseCode()).isEqualTo("59");
    assertThat(result.response().responseMessage()).isEqualTo("Suspected fraud");

    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getRiskScore()).isEqualTo(60);
  }

  @Test
  @DisplayName("Example 15: Same key and payload twice -> replay stored outcome with replayed=true")
  void example15_replayStoredOutcome() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    AuthorizeRequest req =
        new AuthorizeRequest(card.getId(), 20000L, "NPR", "Demo Mart", "5411", "ref-1");
    String fp = RequestFingerprintCalculator.calculate(req);

    Transaction existingTx =
        new Transaction(
            UUID.randomUUID(),
            card,
            caller.apiClientId(),
            null,
            caller.scope(),
            "idemp-key-ex15",
            fp,
            20000L,
            "NPR",
            "Demo Mart",
            "5411",
            "ref-1",
            TransactionStatus.APPROVED,
            "00",
            null,
            false,
            0,
            80000L,
            12,
            baseNow);

    when(transactionRepository.findByIdempotencyScopeAndIdempotencyKey(
            caller.scope(), "idemp-key-ex15"))
        .thenReturn(Optional.of(existingTx));

    AuthorizeExecutionResult result = service.authorize(caller, "idemp-key-ex15", req);

    assertThat(result.replayed()).isTrue();
    assertThat(result.response().transactionId()).isEqualTo(existingTx.getId());
    assertThat(result.response().status()).isEqualTo(TransactionStatus.APPROVED);
    assertThat(result.response().responseCode()).isEqualTo("00");

    verify(ledgerService, never()).recordPurchase(any(), any(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("Example 16: Same key, amount changed -> 422 IDEMPOTENCY_KEY_REUSED")
  void example16_keyReusedWithDifferentPayload() {
    Card card = createCard(100000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    AuthorizeRequest originalReq =
        new AuthorizeRequest(card.getId(), 20000L, "NPR", "Demo Mart", "5411", "ref-1");
    String fp = RequestFingerprintCalculator.calculate(originalReq);

    Transaction existingTx =
        new Transaction(
            UUID.randomUUID(),
            card,
            caller.apiClientId(),
            null,
            caller.scope(),
            "idemp-key-ex16",
            fp,
            20000L,
            "NPR",
            "Demo Mart",
            "5411",
            "ref-1",
            TransactionStatus.APPROVED,
            "00",
            null,
            false,
            0,
            80000L,
            12,
            baseNow);

    when(transactionRepository.findByIdempotencyScopeAndIdempotencyKey(
            caller.scope(), "idemp-key-ex16"))
        .thenReturn(Optional.of(existingTx));

    AuthorizeRequest changedReq =
        new AuthorizeRequest(card.getId(), 25000L, "NPR", "Demo Mart", "5411", "ref-1");

    assertThatThrownBy(() -> service.authorize(caller, "idemp-key-ex16", changedReq))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("Idempotency key has already been used");
  }

  @Test
  @DisplayName("Example 17: Unknown cardId -> 404 CARD_NOT_FOUND, no transaction row")
  void example17_unknownCard() {
    UUID unknownId = UUID.randomUUID();
    when(cardRepository.findByIdForUpdate(unknownId)).thenReturn(Optional.empty());

    AuthorizeRequest req = new AuthorizeRequest(unknownId, 1000L, "NPR", "Demo Mart", "5411", null);

    assertThatThrownBy(() -> service.authorize(caller, "idemp-key-ex17", req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Card not found");

    verify(transactionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Decline precedence: inactive card + expired card -> step 1 CARD_NOT_ACTIVE (62)")
  void declinePrecedence_inactiveBeforeExpired() {
    Card card =
        new Card(
            UUID.randomUUID(),
            "cipher",
            "k1",
            "hash-inact-exp",
            "990000",
            "1234",
            "Sita Sharma",
            5,
            2026, // expired
            "NPR",
            50000L,
            120000L,
            Collections.emptySet(),
            UUID.randomUUID(),
            baseNow);
    // status is INACTIVE
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 1000L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-prec-1", req);

    assertThat(result.response().responseCode()).isEqualTo("62"); // step 1 wins over step 2
  }

  @Test
  @DisplayName("Decline precedence: expired card + wrong currency -> step 2 CARD_EXPIRED (54)")
  void declinePrecedence_expiredBeforeCurrencyMismatch() {
    Card card =
        new Card(
            UUID.randomUUID(),
            "cipher",
            "k1",
            "hash-exp-cur",
            "990000",
            "1234",
            "Sita Sharma",
            5,
            2026, // expired
            "NPR",
            50000L,
            120000L,
            Collections.emptySet(),
            UUID.randomUUID(),
            baseNow);
    card.activate(baseNow);
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 1000L, "USD", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-prec-2", req);

    assertThat(result.response().responseCode()).isEqualTo("54"); // step 2 wins over step 3
  }

  @Test
  @DisplayName(
      "Decline precedence: currency mismatch + blocked MCC -> step 3 CURRENCY_MISMATCH (12)")
  void declinePrecedence_currencyMismatchBeforeMcc() {
    Card card = createCard(100000L, 50000L, 120000L, Set.of("6011"), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 1000L, "USD", "Store", "6011", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-prec-3", req);

    assertThat(result.response().responseCode()).isEqualTo("12"); // step 3 wins over step 4
  }

  @Test
  @DisplayName("Decline precedence: blocked MCC + per-txn limit -> step 4 MCC_RESTRICTED (57)")
  void declinePrecedence_mccBeforeLimit() {
    Card card = createCard(100000L, 50000L, 120000L, Set.of("6011"), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 60000L, "NPR", "Store", "6011", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-prec-4", req);

    assertThat(result.response().responseCode()).isEqualTo("57"); // step 4 wins over step 5
  }

  @Test
  @DisplayName(
      "Decline precedence: per-txn limit + insufficient funds -> step 5 LIMIT_PER_TRANSACTION (61)")
  void declinePrecedence_perTxnLimitBeforeInsufficientFunds() {
    Card card = createCard(100L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 60000L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-prec-5", req);

    assertThat(result.response().responseCode()).isEqualTo("61"); // step 5 wins over step 7
  }

  @Test
  @DisplayName(
      "Decline precedence: insufficient funds + fraud condition -> step 7 INSUFFICIENT_FUNDS (51)")
  void declinePrecedence_insufficientFundsBeforeFraud() {
    Card card = createCard(1000L, 50000L, 120000L, Collections.emptySet(), "NPR");
    when(cardRepository.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    when(ledgerEntryRepository.sumSpentToday(eq(card.getId()), any(), any())).thenReturn(0L);

    AuthorizeRequest req = new AuthorizeRequest(card.getId(), 2000L, "NPR", "Store", "5411", null);
    AuthorizeExecutionResult result = service.authorize(caller, "idemp-prec-6", req);

    assertThat(result.response().responseCode()).isEqualTo("51"); // step 7 wins
    verify(riskEngine, never()).assess(any(), any(), any()); // Fraud assessment is skipped!
  }
}
