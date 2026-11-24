package dev.minicard.processor.disputes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DisputeStatusTest {

  @Test
  @DisplayName("Verify exact allowed transitions from every status")
  void verifyAllowedTransitionsMap() {
    assertThat(DisputeStatus.OPENED.getAllowedTransitions())
        .containsExactly(DisputeStatus.EVIDENCE_REQUESTED);

    assertThat(DisputeStatus.EVIDENCE_REQUESTED.getAllowedTransitions())
        .containsExactlyInAnyOrder(DisputeStatus.UNDER_REVIEW, DisputeStatus.RESOLVED_LOST);

    assertThat(DisputeStatus.UNDER_REVIEW.getAllowedTransitions())
        .containsExactlyInAnyOrder(DisputeStatus.RESOLVED_WON, DisputeStatus.RESOLVED_LOST);

    assertThat(DisputeStatus.RESOLVED_WON.getAllowedTransitions()).isEmpty();
    assertThat(DisputeStatus.RESOLVED_LOST.getAllowedTransitions()).isEmpty();
  }

  @ParameterizedTest(name = "{0} -> {1} allowed={2}")
  @CsvSource({
    "OPENED, OPENED, false",
    "OPENED, EVIDENCE_REQUESTED, true",
    "OPENED, UNDER_REVIEW, false",
    "OPENED, RESOLVED_WON, false",
    "OPENED, RESOLVED_LOST, false",
    "EVIDENCE_REQUESTED, OPENED, false",
    "EVIDENCE_REQUESTED, EVIDENCE_REQUESTED, false",
    "EVIDENCE_REQUESTED, UNDER_REVIEW, true",
    "EVIDENCE_REQUESTED, RESOLVED_WON, false",
    "EVIDENCE_REQUESTED, RESOLVED_LOST, true",
    "UNDER_REVIEW, OPENED, false",
    "UNDER_REVIEW, EVIDENCE_REQUESTED, false",
    "UNDER_REVIEW, UNDER_REVIEW, false",
    "UNDER_REVIEW, RESOLVED_WON, true",
    "UNDER_REVIEW, RESOLVED_LOST, true",
    "RESOLVED_WON, OPENED, false",
    "RESOLVED_WON, EVIDENCE_REQUESTED, false",
    "RESOLVED_WON, UNDER_REVIEW, false",
    "RESOLVED_WON, RESOLVED_WON, false",
    "RESOLVED_WON, RESOLVED_LOST, false",
    "RESOLVED_LOST, OPENED, false",
    "RESOLVED_LOST, EVIDENCE_REQUESTED, false",
    "RESOLVED_LOST, UNDER_REVIEW, false",
    "RESOLVED_LOST, RESOLVED_WON, false",
    "RESOLVED_LOST, RESOLVED_LOST, false"
  })
  @DisplayName("Full state transition matrix test")
  void fullStateTransitionMatrix(DisputeStatus from, DisputeStatus to, boolean expectedAllowed) {
    assertThat(from.canTransitionTo(to)).isEqualTo(expectedAllowed);
  }

  @Test
  @DisplayName("isTerminal is true only for resolved statuses")
  void verifyTerminalStatuses() {
    for (DisputeStatus status : DisputeStatus.values()) {
      if (status == DisputeStatus.RESOLVED_WON || status == DisputeStatus.RESOLVED_LOST) {
        assertThat(status.isTerminal()).isTrue();
      } else {
        assertThat(status.isTerminal()).isFalse();
      }
    }
  }

  @Test
  @DisplayName("canTransitionTo with null returns false")
  void canTransitionToNull() {
    for (DisputeStatus status : DisputeStatus.values()) {
      assertThat(status.canTransitionTo(null)).isFalse();
    }
  }
}
