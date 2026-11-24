package dev.minicard.processor.cards.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardStateMachineTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");
  private static final Instant NOW = Instant.parse("2026-10-21T07:00:00Z");

  private Card createCard(CardStatus status, long balanceMinor, boolean previouslyActivated) {
    Card card =
        new Card(
            UUID.randomUUID(),
            "encPan",
            "k1",
            "panHash",
            "990000",
            "1234",
            "Sita Sharma",
            10,
            2029,
            "NPR",
            5000000L,
            20000000L,
            Set.of("7995"),
            UUID.randomUUID(),
            NOW);

    if (previouslyActivated) {
      card.activate(NOW.minusSeconds(3600));
    }

    if (status == CardStatus.BLOCKED) {
      card.block("Stolen", NOW);
    } else if (status == CardStatus.CLOSED) {
      card.close(NOW);
    } else if (status == CardStatus.ACTIVE && !previouslyActivated) {
      card.activate(NOW);
    }

    card.updateBalance(balanceMinor);
    return card;
  }

  @Test
  @DisplayName("From INACTIVE: activate, block, close work; unblock fails")
  void inactiveTransitions() {
    Card card1 = createCard(CardStatus.INACTIVE, 0L, false);
    card1.activate(NOW);
    assertThat(card1.getStatus()).isEqualTo(CardStatus.ACTIVE);
    assertThat(card1.getActivatedAt()).isEqualTo(NOW);

    Card card2 = createCard(CardStatus.INACTIVE, 0L, false);
    card2.block("Suspected fraud", NOW);
    assertThat(card2.getStatus()).isEqualTo(CardStatus.BLOCKED);
    assertThat(card2.getBlockReason()).isEqualTo("Suspected fraud");

    Card card3 = createCard(CardStatus.INACTIVE, 0L, false);
    card3.close(NOW);
    assertThat(card3.getStatus()).isEqualTo(CardStatus.CLOSED);

    Card card4 = createCard(CardStatus.INACTIVE, 0L, false);
    assertThatThrownBy(() -> card4.unblock(NOW))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION);
  }

  @Test
  @DisplayName("From ACTIVE: block, close work; activate, unblock fail")
  void activeTransitions() {
    Card card1 = createCard(CardStatus.ACTIVE, 0L, true);
    card1.block("Lost card", NOW);
    assertThat(card1.getStatus()).isEqualTo(CardStatus.BLOCKED);

    Card card2 = createCard(CardStatus.ACTIVE, 0L, true);
    card2.close(NOW);
    assertThat(card2.getStatus()).isEqualTo(CardStatus.CLOSED);

    Card card3 = createCard(CardStatus.ACTIVE, 0L, true);
    assertThatThrownBy(() -> card3.activate(NOW))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION);

    Card card4 = createCard(CardStatus.ACTIVE, 0L, true);
    assertThatThrownBy(() -> card4.unblock(NOW))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION);
  }

  @Test
  @DisplayName("From BLOCKED: unblock restores ACTIVE if previously activated, INACTIVE otherwise")
  void blockedTransitions() {
    // Previously activated returns to ACTIVE
    Card cardActiveThenBlocked = createCard(CardStatus.BLOCKED, 0L, true);
    cardActiveThenBlocked.unblock(NOW);
    assertThat(cardActiveThenBlocked.getStatus()).isEqualTo(CardStatus.ACTIVE);
    assertThat(cardActiveThenBlocked.getBlockReason()).isNull();

    // Never activated returns to INACTIVE
    Card cardInactiveThenBlocked = createCard(CardStatus.BLOCKED, 0L, false);
    cardInactiveThenBlocked.unblock(NOW);
    assertThat(cardInactiveThenBlocked.getStatus()).isEqualTo(CardStatus.INACTIVE);
    assertThat(cardInactiveThenBlocked.getBlockReason()).isNull();

    // Close from BLOCKED
    Card cardClose = createCard(CardStatus.BLOCKED, 0L, false);
    cardClose.close(NOW);
    assertThat(cardClose.getStatus()).isEqualTo(CardStatus.CLOSED);

    // Block again fails
    Card cardBlock = createCard(CardStatus.BLOCKED, 0L, false);
    assertThatThrownBy(() -> cardBlock.block("reason", NOW)).isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("From CLOSED: all state transitions are rejected")
  void closedTransitions() {
    Card closedCard = createCard(CardStatus.CLOSED, 0L, true);
    assertThatThrownBy(() -> closedCard.activate(NOW)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> closedCard.block("reason", NOW)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> closedCard.unblock(NOW)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> closedCard.close(NOW)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> closedCard.updateLimits(100, 200, NOW))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> closedCard.updateBlockedMccs(Set.of("5411"), NOW))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("Closing a card with positive balance throws INVALID_STATE_TRANSITION")
  void closeWithBalanceFails() {
    Card card = createCard(CardStatus.ACTIVE, 1000L, true);
    assertThatThrownBy(() -> card.close(NOW))
        .isInstanceOf(ConflictException.class)
        .matches(e -> ((ConflictException) e).getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION);
  }

  @Test
  @DisplayName("Expiry validation: valid through the end of expiry month in Kathmandu zone")
  void cardExpiryValidation() {
    // Expiry: 10 / 2026 -> valid through 2026-10-31 23:59:59.999 in Asia/Kathmandu
    Card card =
        new Card(
            UUID.randomUUID(),
            "enc",
            "k1",
            "hash",
            "990000",
            "1234",
            "Holder",
            10,
            2026,
            "NPR",
            1000,
            2000,
            Set.of(),
            UUID.randomUUID(),
            NOW);

    // Last second of October in Kathmandu: 2026-10-31 23:59:59 (+05:45) is 2026-10-31 18:14:59 UTC
    Instant lastSecondOfOctober = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, ZONE).toInstant();
    assertThat(card.isExpired(lastSecondOfOctober, ZONE)).isFalse();

    // First second of November in Kathmandu: 2026-11-01 00:00:00 (+05:45) is 2026-10-31 18:15:00
    // UTC
    Instant firstSecondOfNovember = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZONE).toInstant();
    assertThat(card.isExpired(firstSecondOfNovember, ZONE)).isTrue();
  }
}
