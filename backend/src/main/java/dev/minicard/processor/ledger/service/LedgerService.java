package dev.minicard.processor.ledger.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.cards.service.CardLedgerService;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.ledger.domain.LedgerEntry;
import dev.minicard.processor.ledger.domain.LedgerEntryType;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import dev.minicard.processor.ledger.web.dto.LedgerEntryResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService implements CardLedgerService {

  private final LedgerEntryRepository ledgerEntryRepository;
  private final CardRepository cardRepository;
  private final AuditService auditService;
  private final Clock clock;
  private final MeterRegistry meterRegistry;

  public LedgerService(
      LedgerEntryRepository ledgerEntryRepository,
      CardRepository cardRepository,
      AuditService auditService,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.cardRepository = cardRepository;
    this.auditService = auditService;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @Transactional
  public void recordInitialLoad(
      Card card, long amountMinor, UUID actorId, String actorLabel, String clientIp) {
    recordTopUp(card, amountMinor, "Initial load", actorLabel);
  }

  @Transactional
  public LedgerEntryResponse topUpCard(
      UUID cardId,
      long amountMinor,
      String description,
      UUID actorId,
      String actorLabel,
      String clientIp) {
    Card card =
        cardRepository
            .findByIdForUpdate(cardId)
            .orElseThrow(
                () -> new NotFoundException(ErrorCode.CARD_NOT_FOUND, "Card not found: " + cardId));

    String desc = description != null ? description : "Top-up";
    LedgerEntry entry = recordTopUp(card, amountMinor, desc, actorLabel);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "CARD_TOPUP",
        "Card",
        card.getId(),
        clientIp,
        "amount=" + amountMinor + " currency=" + card.getCurrency());

    return LedgerEntryResponse.fromEntity(entry);
  }

  @Transactional(readOnly = true)
  public Page<LedgerEntryResponse> getCardLedger(UUID cardId, Pageable pageable) {
    if (!cardRepository.existsById(cardId)) {
      throw new NotFoundException(ErrorCode.CARD_NOT_FOUND, "Card not found: " + cardId);
    }
    return ledgerEntryRepository
        .findByCardIdOrderByCreatedAtDesc(cardId, pageable)
        .map(LedgerEntryResponse::fromEntity);
  }

  @Transactional
  public LedgerEntry recordTopUp(Card card, long amountMinor, String description, String actor) {
    Objects.requireNonNull(card, "card cannot be null");
    Objects.requireNonNull(actor, "actor cannot be null");

    if (card.getStatus() != CardStatus.ACTIVE && card.getStatus() != CardStatus.INACTIVE) {
      throw new ConflictException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Top-up is only permitted for ACTIVE or INACTIVE cards; current status: "
              + card.getStatus());
    }

    if (amountMinor <= 0) {
      throw new UnprocessableException(
          ErrorCode.VALIDATION_FAILED,
          "Top-up amount must be greater than zero, got " + amountMinor);
    }

    long newBalance;
    try {
      newBalance = Math.addExact(card.getBalanceMinor(), amountMinor);
    } catch (ArithmeticException e) {
      throw new UnprocessableException(
          ErrorCode.AMOUNT_OVERFLOW, "Top-up amount causes card balance overflow");
    }

    Instant now = clock.instant();
    card.updateBalance(newBalance);
    cardRepository.saveAndFlush(card);

    LedgerEntry entry =
        new LedgerEntry(
            null,
            card,
            LedgerEntryType.TOPUP,
            amountMinor,
            newBalance,
            null,
            null,
            description,
            actor,
            now);

    LedgerEntry saved = ledgerEntryRepository.save(entry);
    Counter.builder("minicard.ledger.entries")
        .tag("type", LedgerEntryType.TOPUP.name())
        .register(meterRegistry)
        .increment();
    return saved;
  }

  @Transactional
  public LedgerEntry recordPurchase(
      Card card, UUID transactionId, long amountMinor, String description, String actor) {
    Objects.requireNonNull(card, "card cannot be null");
    Objects.requireNonNull(transactionId, "transactionId cannot be null");
    Objects.requireNonNull(actor, "actor cannot be null");

    if (amountMinor <= 0) {
      throw new UnprocessableException(
          ErrorCode.VALIDATION_FAILED,
          "Purchase amount must be greater than zero, got " + amountMinor);
    }

    if (amountMinor > card.getBalanceMinor()) {
      throw new UnprocessableException(
          ErrorCode.VALIDATION_FAILED,
          "Insufficient card balance for purchase; available: "
              + card.getBalanceMinor()
              + ", requested: "
              + amountMinor);
    }

    long newBalance = card.getBalanceMinor() - amountMinor;
    Instant now = clock.instant();
    card.updateBalance(newBalance);
    cardRepository.saveAndFlush(card);

    LedgerEntry entry =
        new LedgerEntry(
            null,
            card,
            LedgerEntryType.PURCHASE,
            -amountMinor,
            newBalance,
            transactionId,
            null,
            description,
            actor,
            now);

    LedgerEntry saved = ledgerEntryRepository.save(entry);
    Counter.builder("minicard.ledger.entries")
        .tag("type", LedgerEntryType.PURCHASE.name())
        .register(meterRegistry)
        .increment();
    return saved;
  }

  @Transactional
  public LedgerEntry recordDisputeCredit(
      Card card, UUID disputeId, long amountMinor, String description, String actor) {
    Objects.requireNonNull(card, "card cannot be null");
    Objects.requireNonNull(disputeId, "disputeId cannot be null");
    Objects.requireNonNull(actor, "actor cannot be null");

    if (card.getStatus() == CardStatus.CLOSED) {
      throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "card is closed");
    }

    if (amountMinor <= 0) {
      throw new UnprocessableException(
          ErrorCode.VALIDATION_FAILED,
          "Dispute credit amount must be greater than zero, got " + amountMinor);
    }

    if (ledgerEntryRepository.existsByDisputeId(disputeId)) {
      throw new ConflictException(
          ErrorCode.DISPUTE_ALREADY_EXISTS,
          "Ledger credit for disputeId " + disputeId + " already exists");
    }

    long newBalance;
    try {
      newBalance = Math.addExact(card.getBalanceMinor(), amountMinor);
    } catch (ArithmeticException e) {
      throw new UnprocessableException(
          ErrorCode.AMOUNT_OVERFLOW, "Dispute credit causes card balance overflow");
    }

    Instant now = clock.instant();
    card.updateBalance(newBalance);
    cardRepository.saveAndFlush(card);

    LedgerEntry entry =
        new LedgerEntry(
            null,
            card,
            LedgerEntryType.DISPUTE_CREDIT,
            amountMinor,
            newBalance,
            null,
            disputeId,
            description,
            actor,
            now);

    LedgerEntry saved = ledgerEntryRepository.save(entry);
    Counter.builder("minicard.ledger.entries")
        .tag("type", LedgerEntryType.DISPUTE_CREDIT.name())
        .register(meterRegistry)
        .increment();
    return saved;
  }
}
