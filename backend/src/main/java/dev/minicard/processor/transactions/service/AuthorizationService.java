package dev.minicard.processor.transactions.service;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.common.id.UuidV7;
import dev.minicard.processor.common.time.BusinessCalendar;
import dev.minicard.processor.fraud.domain.TransactionRuleHit;
import dev.minicard.processor.fraud.repository.TransactionRuleHitRepository;
import dev.minicard.processor.fraud.service.AuthContext;
import dev.minicard.processor.fraud.service.RiskAssessment;
import dev.minicard.processor.fraud.service.RiskDecision;
import dev.minicard.processor.fraud.service.RiskEngine;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import dev.minicard.processor.ledger.service.LedgerService;
import dev.minicard.processor.security.CallerIdentity;
import dev.minicard.processor.transactions.domain.AuthorizationDecision;
import dev.minicard.processor.transactions.domain.DeclineReason;
import dev.minicard.processor.transactions.domain.ResponseCode;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import dev.minicard.processor.transactions.web.dto.AuthorizeRequest;
import dev.minicard.processor.transactions.web.dto.AuthorizeResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
  private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{8,64}$");

  private final CardRepository cardRepository;
  private final TransactionRepository transactionRepository;
  private final TransactionRuleHitRepository transactionRuleHitRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final LedgerService ledgerService;
  private final RiskEngine riskEngine;
  private final AppProperties appProperties;
  private final BusinessCalendar businessCalendar;
  private final Clock clock;
  private final MeterRegistry meterRegistry;

  public AuthorizationService(
      CardRepository cardRepository,
      TransactionRepository transactionRepository,
      TransactionRuleHitRepository transactionRuleHitRepository,
      LedgerEntryRepository ledgerEntryRepository,
      LedgerService ledgerService,
      RiskEngine riskEngine,
      AppProperties appProperties,
      BusinessCalendar businessCalendar,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.cardRepository = cardRepository;
    this.transactionRepository = transactionRepository;
    this.transactionRuleHitRepository = transactionRuleHitRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.ledgerService = ledgerService;
    this.riskEngine = riskEngine;
    this.appProperties = appProperties;
    this.businessCalendar = businessCalendar;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public AuthorizeExecutionResult authorize(
      CallerIdentity caller, String idempotencyKey, AuthorizeRequest request) {
    Objects.requireNonNull(caller, "caller cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
      throw new BadRequestException(
          ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
          "Header Idempotency-Key is required and must match ^[A-Za-z0-9_.:-]{8,64}$");
    }

    if (!dev.minicard.processor.common.reference.ReferenceData.SUPPORTED_CURRENCY_CODES.contains(
        request.currency())) {
      throw new UnprocessableException(
          ErrorCode.UNSUPPORTED_CURRENCY, "Unsupported currency: " + request.currency());
    }

    Instant now = clock.instant();
    String scope = caller.scope();
    String fingerprint = RequestFingerprintCalculator.calculate(request);

    // 1. Lock card row for update (5 s lock timeout via repository query hint)
    // Serializes concurrent requests for the same card
    Card card =
        cardRepository
            .findByIdForUpdate(request.cardId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.CARD_NOT_FOUND, "Card not found: " + request.cardId()));

    // 2. Check idempotency after acquiring card row lock
    Optional<Transaction> existing =
        transactionRepository.findByIdempotencyScopeAndIdempotencyKey(scope, idempotencyKey);
    if (existing.isPresent()) {
      Transaction prior = existing.get();
      if (prior.getRequestFingerprint().equals(fingerprint)) {
        log.info(
            "AUTHORIZATION_REPLAY: transactionId={}, scope={}, key={}",
            prior.getId(),
            scope,
            idempotencyKey);
        String msg = ResponseCode.messageFor(prior.getResponseCode());
        return new AuthorizeExecutionResult(AuthorizeResponse.fromEntity(prior, msg), true);
      } else {
        throw new UnprocessableException(
            ErrorCode.IDEMPOTENCY_KEY_REUSED,
            "Idempotency key has already been used with different request parameters");
      }
    }

    // 3. Evaluate rules in normative sequence (docs/05-BUSINESS-RULES.md section 2.1)
    AuthorizationDecision decision;
    RiskAssessment risk = null;
    if (card.getStatus() != CardStatus.ACTIVE) {
      decision = new AuthorizationDecision.Declined(DeclineReason.CARD_NOT_ACTIVE, 0);
    } else if (card.isExpired(now, appProperties.businessZone())) {
      decision = new AuthorizationDecision.Declined(DeclineReason.CARD_EXPIRED, 0);
    } else if (!request.currency().equals(card.getCurrency())) {
      decision = new AuthorizationDecision.Declined(DeclineReason.CURRENCY_MISMATCH, 0);
    } else if (card.getBlockedMccs().contains(request.mcc())) {
      decision = new AuthorizationDecision.Declined(DeclineReason.MCC_RESTRICTED, 0);
    } else if (request.amountMinor() > card.getPerTransactionLimitMinor()) {
      decision = new AuthorizationDecision.Declined(DeclineReason.LIMIT_PER_TRANSACTION, 0);
    } else {
      Instant startOfDay = businessCalendar.dayRange(now).startInclusive();
      long spentToday = ledgerEntryRepository.sumSpentToday(card.getId(), startOfDay, now);
      long totalToday;
      try {
        totalToday = Math.addExact(spentToday, request.amountMinor());
      } catch (ArithmeticException e) {
        totalToday = Long.MAX_VALUE;
      }

      if (totalToday > card.getDailyLimitMinor()) {
        decision = new AuthorizationDecision.Declined(DeclineReason.LIMIT_DAILY, 0);
      } else if (request.amountMinor() > card.getBalanceMinor()) {
        decision = new AuthorizationDecision.Declined(DeclineReason.INSUFFICIENT_FUNDS, 0);
      } else {
        risk =
            riskEngine.assess(
                card,
                new AuthContext(request.amountMinor(), request.currency(), request.mcc()),
                now);
        if (risk.decision() == RiskDecision.DECLINE) {
          decision =
              new AuthorizationDecision.Declined(DeclineReason.SUSPECTED_FRAUD, risk.score());
        } else {
          boolean flagged = risk.decision() == RiskDecision.FLAG;
          decision = new AuthorizationDecision.Approved(risk.score(), flagged);
        }
      }
    }

    // 4. Create and persist Transaction record
    int processingMillis = (int) Math.max(1, Duration.between(now, clock.instant()).toMillis());
    TransactionStatus status;
    String responseCode;
    DeclineReason declineReason = null;
    boolean flagged = false;
    int riskScore = 0;
    Long balanceAfterMinor = null;

    if (decision instanceof AuthorizationDecision.Approved approved) {
      status = TransactionStatus.APPROVED;
      responseCode = ResponseCode.APPROVED;
      flagged = approved.flagged();
      riskScore = approved.riskScore();
      balanceAfterMinor = card.getBalanceMinor() - request.amountMinor();
    } else if (decision instanceof AuthorizationDecision.Declined declined) {
      status = TransactionStatus.DECLINED;
      responseCode = declined.reason().responseCode();
      declineReason = declined.reason();
      riskScore = declined.riskScore();
      flagged = false;
      balanceAfterMinor = null;
    } else {
      throw new IllegalStateException("Unexpected authorization decision type");
    }

    Transaction transaction =
        new Transaction(
            null,
            card,
            caller.apiClientId(),
            caller.userId(),
            scope,
            idempotencyKey,
            fingerprint,
            request.amountMinor(),
            request.currency(),
            request.merchantName(),
            request.mcc(),
            request.reference(),
            status,
            responseCode,
            declineReason,
            flagged,
            riskScore,
            balanceAfterMinor,
            processingMillis,
            now);

    try {
      transaction = transactionRepository.saveAndFlush(transaction);
    } catch (DataIntegrityViolationException ex) {
      if (isIdempotencyConstraintViolation(ex)) {
        throw new ConflictException(
            ErrorCode.IDEMPOTENCY_CONFLICT,
            "Idempotency key collision under concurrent processing; please retry");
      }
      throw ex;
    }

    if (risk != null && !risk.ruleHits().isEmpty()) {
      UUID transactionId = transaction.getId();
      List<TransactionRuleHit> hits =
          risk.ruleHits().stream()
              .map(
                  h ->
                      new TransactionRuleHit(
                          UuidV7.newId(clock),
                          transactionId,
                          h.ruleId(),
                          h.ruleName(),
                          h.ruleType(),
                          h.points(),
                          h.action(),
                          h.detail()))
              .toList();
      transactionRuleHitRepository.saveAll(hits);
    }

    // 5. If approved, record purchase debit in ledger
    if (status == TransactionStatus.APPROVED) {
      ledgerService.recordPurchase(
          card,
          transaction.getId(),
          request.amountMinor(),
          request.merchantName().trim(),
          caller.actorLabel());
    }

    log.info(
        "AUTHORIZATION: transactionId={}, status={}, responseCode={}, amountMinor={} {}, cardLast4={}",
        transaction.getId(),
        status,
        responseCode,
        request.amountMinor(),
        request.currency(),
        card.getLast4());

    Counter.builder("minicard.authorizations")
        .tag("status", status.name())
        .tag("responseCode", responseCode)
        .tag("declineReason", declineReason != null ? declineReason.name() : "NONE")
        .register(meterRegistry)
        .increment();

    Timer.builder("minicard.authorization.duration")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)
        .record(Duration.between(now, clock.instant()));

    if (flagged) {
      Counter.builder("minicard.fraud.flagged").register(meterRegistry).increment();
    }

    String responseMessage = ResponseCode.messageFor(responseCode);
    return new AuthorizeExecutionResult(
        AuthorizeResponse.fromEntity(transaction, responseMessage), false);
  }

  private boolean isIdempotencyConstraintViolation(Throwable ex) {
    Throwable t = ex;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null
          && (msg.contains("ux_transactions_idempotency")
              || msg.contains("idempotency_scope")
              || msg.contains("duplicate key"))) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }
}
