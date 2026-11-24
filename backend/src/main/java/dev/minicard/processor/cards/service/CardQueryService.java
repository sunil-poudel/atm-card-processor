package dev.minicard.processor.cards.service;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.cards.web.dto.CardResponse;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
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
public class CardQueryService {

  private final CardRepository cardRepository;
  private final AppProperties appProperties;
  private final Clock clock;

  public CardQueryService(CardRepository cardRepository, AppProperties appProperties, Clock clock) {
    this.cardRepository = cardRepository;
    this.appProperties = appProperties;
    this.clock = clock;
  }

  public CardResponse getCard(UUID cardId) {
    Card card =
        cardRepository
            .findById(cardId)
            .orElseThrow(
                () -> new NotFoundException(ErrorCode.CARD_NOT_FOUND, "Card not found: " + cardId));
    Instant now = clock.instant();
    return CardResponse.fromEntity(card, now, appProperties.businessZone(), null);
  }

  public Page<CardResponse> listCards(
      CardStatus status, String currency, String q, Boolean expired, Pageable pageable) {
    Instant now = clock.instant();
    ZonedDateTime nowKathmandu = now.atZone(appProperties.businessZone());
    int curYear = nowKathmandu.getYear();
    int curMonth = nowKathmandu.getMonthValue();

    Specification<Card> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (currency != null && !currency.isBlank()) {
            predicates.add(cb.equal(root.get("currency"), currency.trim().toUpperCase()));
          }
          if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase();
            if (term.matches("^\\d{4}$")) {
              predicates.add(
                  cb.or(
                      cb.like(cb.lower(root.get("holderName")), "%" + term + "%"),
                      cb.equal(root.get("last4"), term)));
            } else {
              predicates.add(cb.like(cb.lower(root.get("holderName")), "%" + term + "%"));
            }
          }
          if (expired != null) {
            if (expired) {
              predicates.add(
                  cb.or(
                      cb.lessThan(root.get("expiryYear"), curYear),
                      cb.and(
                          cb.equal(root.get("expiryYear"), curYear),
                          cb.lessThan(root.get("expiryMonth"), curMonth))));
            } else {
              predicates.add(
                  cb.or(
                      cb.greaterThan(root.get("expiryYear"), curYear),
                      cb.and(
                          cb.equal(root.get("expiryYear"), curYear),
                          cb.greaterThanOrEqualTo(root.get("expiryMonth"), curMonth))));
            }
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return cardRepository
        .findAll(spec, pageable)
        .map(card -> CardResponse.fromEntity(card, now, appProperties.businessZone(), null));
  }
}
