package dev.minicard.processor.disputes.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum DisputeStatus {
  OPENED,
  EVIDENCE_REQUESTED,
  UNDER_REVIEW,
  RESOLVED_WON,
  RESOLVED_LOST;

  private Set<DisputeStatus> allowedTransitions;

  static {
    OPENED.allowedTransitions = EnumSet.of(EVIDENCE_REQUESTED);
    EVIDENCE_REQUESTED.allowedTransitions = EnumSet.of(UNDER_REVIEW, RESOLVED_LOST);
    UNDER_REVIEW.allowedTransitions = EnumSet.of(RESOLVED_WON, RESOLVED_LOST);
    RESOLVED_WON.allowedTransitions = Collections.emptySet();
    RESOLVED_LOST.allowedTransitions = Collections.emptySet();
  }

  public boolean canTransitionTo(DisputeStatus target) {
    if (target == null) {
      return false;
    }
    return allowedTransitions != null && allowedTransitions.contains(target);
  }

  public Set<DisputeStatus> getAllowedTransitions() {
    return allowedTransitions != null
        ? Collections.unmodifiableSet(allowedTransitions)
        : Collections.emptySet();
  }

  public boolean isTerminal() {
    return this == RESOLVED_WON || this == RESOLVED_LOST;
  }
}
