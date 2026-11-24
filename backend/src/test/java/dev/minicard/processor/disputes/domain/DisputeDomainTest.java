package dev.minicard.processor.disputes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.transactions.domain.Transaction;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisputeDomainTest {

  private Transaction transaction;
  private Card card;
  private Instant now;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-10-20T10:00:00Z");
    transaction = mock(Transaction.class);
    card = mock(Card.class);
    when(transaction.getId()).thenReturn(UUID.randomUUID());
    when(transaction.getAmountMinor()).thenReturn(250000L);
    when(transaction.getCurrency()).thenReturn("NPR");
    when(card.getId()).thenReturn(UUID.randomUUID());
  }

  @Test
  @DisplayName("Dispute initialization sets expected initial values")
  void disputeInitialization() {
    Dispute dispute =
        new Dispute(
            null,
            transaction,
            card,
            DisputeReason.UNAUTHORIZED_TRANSACTION,
            "Card stolen",
            "ops@minicard.dev",
            now);

    assertThat(dispute.getId()).isNotNull();
    assertThat(dispute.getTransaction()).isEqualTo(transaction);
    assertThat(dispute.getCard()).isEqualTo(card);
    assertThat(dispute.getReason()).isEqualTo(DisputeReason.UNAUTHORIZED_TRANSACTION);
    assertThat(dispute.getDescription()).isEqualTo("Card stolen");
    assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.OPENED);
    assertThat(dispute.getAmountMinor()).isEqualTo(250000L);
    assertThat(dispute.getCurrency()).isEqualTo("NPR");
    assertThat(dispute.getOpenedBy()).isEqualTo("ops@minicard.dev");
    assertThat(dispute.getOpenedAt()).isEqualTo(now);
    assertThat(dispute.getEvidenceDueAt()).isNull();
    assertThat(dispute.getResolvedAt()).isNull();
    assertThat(dispute.getResolvedBy()).isNull();
    assertThat(dispute.getResolutionNote()).isNull();
    assertThat(dispute.getVersion()).isNull();
  }

  @Test
  @DisplayName("Allowed actions correctly computed across states and deadlines")
  void allowedActionsComputation() {
    Dispute dispute =
        new Dispute(
            null,
            transaction,
            card,
            DisputeReason.UNAUTHORIZED_TRANSACTION,
            "Card stolen",
            "ops@minicard.dev",
            now);

    // OPENED
    assertThat(dispute.getAllowedActions(now)).containsExactly(DisputeAction.REQUEST_EVIDENCE);

    // Transition to EVIDENCE_REQUESTED with deadline in future
    dispute.transitionTo(
        DisputeStatus.EVIDENCE_REQUESTED, "officer@minicard.dev", "Provide docs", now);
    dispute.setEvidenceDueAt(now.plus(7, ChronoUnit.DAYS));

    assertThat(dispute.getAllowedActions(now)).containsExactly(DisputeAction.SUBMIT_EVIDENCE);
    assertThat(dispute.getAllowedActions(now.plus(7, ChronoUnit.DAYS)))
        .containsExactly(DisputeAction.SUBMIT_EVIDENCE);
    // After deadline -> empty
    assertThat(dispute.getAllowedActions(now.plus(7, ChronoUnit.DAYS).plusSeconds(1))).isEmpty();

    // Transition to UNDER_REVIEW
    dispute.transitionTo(DisputeStatus.UNDER_REVIEW, "officer@minicard.dev", "Docs submitted", now);
    assertThat(dispute.getAllowedActions(now))
        .containsExactlyInAnyOrder(DisputeAction.RESOLVE_WON, DisputeAction.RESOLVE_LOST);

    // Transition to RESOLVED_WON
    dispute.transitionTo(
        DisputeStatus.RESOLVED_WON, "officer@minicard.dev", "Merchant conceded", now);
    assertThat(dispute.getAllowedActions(now)).isEmpty();
    assertThat(dispute.getResolvedAt()).isEqualTo(now);
    assertThat(dispute.getResolvedBy()).isEqualTo("officer@minicard.dev");
    assertThat(dispute.getResolutionNote()).isEqualTo("Merchant conceded");
  }

  @Test
  @DisplayName("Illegal transition throws ConflictException")
  void illegalTransitionThrows() {
    Dispute dispute =
        new Dispute(
            null,
            transaction,
            card,
            DisputeReason.UNAUTHORIZED_TRANSACTION,
            "Card stolen",
            "ops@minicard.dev",
            now);

    // From OPENED directly to RESOLVED_WON is illegal
    assertThatThrownBy(
            () ->
                dispute.transitionTo(
                    DisputeStatus.RESOLVED_WON, "officer@minicard.dev", "Invalid jump", now))
        .isInstanceOf(ConflictException.class)
        .satisfies(
            ex -> {
              ConflictException ce = (ConflictException) ex;
              assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
            });
  }
}
