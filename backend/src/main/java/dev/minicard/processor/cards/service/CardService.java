package dev.minicard.processor.cards.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.cards.web.dto.BlockCardRequest;
import dev.minicard.processor.cards.web.dto.CardResponse;
import dev.minicard.processor.cards.web.dto.IssueCardRequest;
import dev.minicard.processor.cards.web.dto.UpdateBlockedMccsRequest;
import dev.minicard.processor.cards.web.dto.UpdateLimitsRequest;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.common.id.UuidV7;
import dev.minicard.processor.crypto.EncryptedPan;
import dev.minicard.processor.crypto.GeneratedPan;
import dev.minicard.processor.crypto.PanCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {

  private final CardRepository cardRepository;
  private final CardLedgerService cardLedgerService;
  private final PanGenerator panGenerator;
  private final PanCipher panCipher;
  private final AuditService auditService;
  private final AppProperties appProperties;
  private final Clock clock;

  public CardService(
      CardRepository cardRepository,
      CardLedgerService cardLedgerService,
      PanGenerator panGenerator,
      PanCipher panCipher,
      AuditService auditService,
      AppProperties appProperties,
      Clock clock) {
    this.cardRepository = cardRepository;
    this.cardLedgerService = cardLedgerService;
    this.panGenerator = panGenerator;
    this.panCipher = panCipher;
    this.auditService = auditService;
    this.appProperties = appProperties;
    this.clock = clock;
  }

  @Transactional
  public CardResponse issueCard(
      IssueCardRequest request, UUID creatorUserId, String creatorLabel, String ipAddress) {
    Objects.requireNonNull(request, "request cannot be null");

    GeneratedPan generatedPan = panGenerator.generateUniquePan();
    UUID cardId = UuidV7.newId();

    ZonedDateTime nowKathmandu = clock.instant().atZone(appProperties.businessZone());
    int validityYears = request.validityYears() != null ? request.validityYears() : 3;
    int expiryMonth = nowKathmandu.getMonthValue();
    int expiryYear = nowKathmandu.getYear() + validityYears;

    long perTxnLimit =
        request.perTransactionLimitMinor() != null
            ? request.perTransactionLimitMinor()
            : appProperties.cards().defaultPerTransactionLimitMinor();
    long dailyLimit =
        request.dailyLimitMinor() != null
            ? request.dailyLimitMinor()
            : appProperties.cards().defaultDailyLimitMinor();

    if (perTxnLimit < 1 || dailyLimit < perTxnLimit || dailyLimit > 10_000_000_000L) {
      throw new UnprocessableException(
          ErrorCode.LIMIT_CONFIGURATION_INVALID,
          "Limits must satisfy: 1 <= perTransaction <= daily <= 10,000,000,000");
    }

    EncryptedPan encryptedPan = panCipher.encrypt(generatedPan.pan(), cardId);
    Instant now = clock.instant();

    Card card =
        new Card(
            cardId,
            encryptedPan.ciphertext(),
            encryptedPan.keyId(),
            generatedPan.panHash(),
            generatedPan.bin(),
            generatedPan.last4(),
            request.holderName(),
            expiryMonth,
            expiryYear,
            request.currency(),
            perTxnLimit,
            dailyLimit,
            request.blockedMccs(),
            creatorUserId,
            now);

    Card savedCard = cardRepository.saveAndFlush(card);

    long initialBalance =
        request.initialBalanceMinor() != null ? request.initialBalanceMinor() : 0L;
    if (initialBalance > 0) {
      cardLedgerService.recordInitialLoad(
          savedCard, initialBalance, creatorUserId, creatorLabel, ipAddress);
    }

    auditService.record(
        ActorType.USER,
        creatorUserId,
        creatorLabel,
        "CARD_ISSUED",
        "Card",
        savedCard.getId(),
        ipAddress,
        "last4=" + savedCard.getLast4() + " currency=" + savedCard.getCurrency());

    String plainPan = appProperties.cards().exposePanOnIssue() ? generatedPan.pan() : null;
    return CardResponse.fromEntity(savedCard, now, appProperties.businessZone(), plainPan);
  }

  @Transactional
  public CardResponse activateCard(UUID cardId, UUID actorId, String actorLabel, String ipAddress) {
    Card card = loadCardForUpdate(cardId);
    Instant now = clock.instant();
    card.activate(now);
    Card saved = cardRepository.saveAndFlush(card);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "CARD_ACTIVATED",
        "Card",
        saved.getId(),
        ipAddress,
        null);

    return CardResponse.fromEntity(saved, now, appProperties.businessZone(), null);
  }

  @Transactional
  public CardResponse blockCard(
      UUID cardId, BlockCardRequest request, UUID actorId, String actorLabel, String ipAddress) {
    Card card = loadCardForUpdate(cardId);
    Instant now = clock.instant();
    card.block(request.reason(), now);
    Card saved = cardRepository.saveAndFlush(card);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "CARD_BLOCKED",
        "Card",
        saved.getId(),
        ipAddress,
        "reason=" + request.reason().trim());

    return CardResponse.fromEntity(saved, now, appProperties.businessZone(), null);
  }

  @Transactional
  public CardResponse unblockCard(UUID cardId, UUID actorId, String actorLabel, String ipAddress) {
    Card card = loadCardForUpdate(cardId);
    Instant now = clock.instant();
    card.unblock(now);
    Card saved = cardRepository.saveAndFlush(card);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "CARD_UNBLOCKED",
        "Card",
        saved.getId(),
        ipAddress,
        null);

    return CardResponse.fromEntity(saved, now, appProperties.businessZone(), null);
  }

  @Transactional
  public CardResponse closeCard(UUID cardId, UUID actorId, String actorLabel, String ipAddress) {
    Card card = loadCardForUpdate(cardId);
    Instant now = clock.instant();
    card.close(now);
    Card saved = cardRepository.saveAndFlush(card);

    auditService.record(
        ActorType.USER, actorId, actorLabel, "CARD_CLOSED", "Card", saved.getId(), ipAddress, null);

    return CardResponse.fromEntity(saved, now, appProperties.businessZone(), null);
  }

  @Transactional
  public CardResponse updateLimits(
      UUID cardId, UpdateLimitsRequest request, UUID actorId, String actorLabel, String ipAddress) {
    Card card = loadCardForUpdate(cardId);
    if (!Objects.equals(card.getVersion(), request.version())) {
      throw new ConflictException(
          ErrorCode.OPTIMISTIC_LOCK_FAILURE,
          "Card version conflict: expected " + card.getVersion() + " but was " + request.version());
    }

    Instant now = clock.instant();
    card.updateLimits(request.perTransactionLimitMinor(), request.dailyLimitMinor(), now);
    Card saved = cardRepository.saveAndFlush(card);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "CARD_LIMITS_CHANGED",
        "Card",
        saved.getId(),
        ipAddress,
        "perTxn=" + request.perTransactionLimitMinor() + " daily=" + request.dailyLimitMinor());

    return CardResponse.fromEntity(saved, now, appProperties.businessZone(), null);
  }

  @Transactional
  public CardResponse updateBlockedMccs(
      UUID cardId,
      UpdateBlockedMccsRequest request,
      UUID actorId,
      String actorLabel,
      String ipAddress) {
    Card card = loadCardForUpdate(cardId);
    if (!Objects.equals(card.getVersion(), request.version())) {
      throw new ConflictException(
          ErrorCode.OPTIMISTIC_LOCK_FAILURE,
          "Card version conflict: expected " + card.getVersion() + " but was " + request.version());
    }

    Instant now = clock.instant();
    card.updateBlockedMccs(request.mccs(), now);
    Card saved = cardRepository.saveAndFlush(card);

    int count = request.mccs() != null ? request.mccs().size() : 0;
    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "CARD_MCC_CHANGED",
        "Card",
        saved.getId(),
        ipAddress,
        "count=" + count);

    return CardResponse.fromEntity(saved, now, appProperties.businessZone(), null);
  }

  private Card loadCardForUpdate(UUID cardId) {
    return cardRepository
        .findByIdForUpdate(cardId)
        .orElseThrow(
            () -> new NotFoundException(ErrorCode.CARD_NOT_FOUND, "Card not found: " + cardId));
  }
}
