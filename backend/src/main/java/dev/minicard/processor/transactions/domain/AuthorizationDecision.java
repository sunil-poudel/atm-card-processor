package dev.minicard.processor.transactions.domain;

public sealed interface AuthorizationDecision
    permits AuthorizationDecision.Approved, AuthorizationDecision.Declined {

  record Approved(int riskScore, boolean flagged) implements AuthorizationDecision {}

  record Declined(DeclineReason reason, int riskScore) implements AuthorizationDecision {}
}
