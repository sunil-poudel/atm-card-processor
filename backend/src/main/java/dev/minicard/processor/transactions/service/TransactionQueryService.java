package dev.minicard.processor.transactions.service;

import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.fraud.repository.TransactionRuleHitRepository;
import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.transactions.domain.DeclineReason;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import dev.minicard.processor.transactions.web.dto.DisputeSummaryResponse;
import dev.minicard.processor.transactions.web.dto.RuleHitResponse;
import dev.minicard.processor.transactions.web.dto.TransactionDetail;
import dev.minicard.processor.transactions.web.dto.TransactionSummary;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

  private final TransactionRepository transactionRepository;
  private final TransactionRuleHitRepository transactionRuleHitRepository;
  private final ApiClientRepository apiClientRepository;
  private final UserRepository userRepository;
  private final TransactionDisputeLookup transactionDisputeLookup;

  public TransactionQueryService(
      TransactionRepository transactionRepository,
      TransactionRuleHitRepository transactionRuleHitRepository,
      ApiClientRepository apiClientRepository,
      UserRepository userRepository,
      TransactionDisputeLookup transactionDisputeLookup) {
    this.transactionRepository = transactionRepository;
    this.transactionRuleHitRepository = transactionRuleHitRepository;
    this.apiClientRepository = apiClientRepository;
    this.userRepository = userRepository;
    this.transactionDisputeLookup = transactionDisputeLookup;
  }

  public Page<TransactionSummary> listTransactions(
      UUID cardId,
      TransactionStatus status,
      Instant from,
      Instant to,
      Boolean flagged,
      String mcc,
      Pageable pageable) {
    return listTransactions(
        cardId, status, null, null, mcc, flagged, null, null, from, to, null, pageable);
  }

  public Page<TransactionSummary> listTransactions(
      UUID cardId,
      TransactionStatus status,
      String responseCode,
      DeclineReason declineReason,
      String mcc,
      Boolean flagged,
      Long minAmountMinor,
      Long maxAmountMinor,
      Instant from,
      Instant to,
      String q,
      Pageable pageable) {

    Specification<Transaction> spec =
        (root, query, cb) -> {
          if (query != null
              && Long.class != query.getResultType()
              && long.class != query.getResultType()) {
            root.fetch("card", JoinType.LEFT);
          }

          List<Predicate> predicates = new ArrayList<>();
          if (cardId != null) {
            predicates.add(cb.equal(root.get("card").get("id"), cardId));
          }
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (responseCode != null && !responseCode.isBlank()) {
            predicates.add(cb.equal(root.get("responseCode"), responseCode.trim()));
          }
          if (declineReason != null) {
            predicates.add(cb.equal(root.get("declineReason"), declineReason));
          }
          if (minAmountMinor != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amountMinor"), minAmountMinor));
          }
          if (maxAmountMinor != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amountMinor"), maxAmountMinor));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThan(root.get("createdAt"), to));
          }
          if (flagged != null) {
            predicates.add(cb.equal(root.get("flagged"), flagged));
          }
          if (mcc != null && !mcc.isBlank()) {
            predicates.add(cb.equal(root.get("mcc"), mcc.trim()));
          }
          if (q != null && !q.isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("merchantName")), "%" + q.trim().toLowerCase() + "%"));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<Transaction> page = transactionRepository.findAll(spec, pageable);
    if (page.isEmpty()) {
      return page.map(t -> null);
    }

    Set<UUID> clientIds =
        page.getContent().stream()
            .map(Transaction::getApiClientId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Set<UUID> userIds =
        page.getContent().stream()
            .map(Transaction::getInitiatedByUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, String> clientNames =
        clientIds.isEmpty()
            ? Collections.emptyMap()
            : apiClientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(ApiClient::getId, ApiClient::getName));

    Map<UUID, String> userNames =
        userIds.isEmpty()
            ? Collections.emptyMap()
            : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));

    List<TransactionSummary> summaries =
        page.getContent().stream()
            .map(
                t -> {
                  String sourceName =
                      t.getApiClientId() != null
                          ? clientNames.getOrDefault(t.getApiClientId(), "Unknown Client")
                          : userNames.getOrDefault(t.getInitiatedByUserId(), "Unknown User");
                  return TransactionSummary.fromEntity(
                      t, t.getCard().getLast4(), t.getCard().getHolderName(), sourceName);
                })
            .toList();

    return new PageImpl<>(summaries, pageable, page.getTotalElements());
  }

  public TransactionDetail getTransaction(UUID id) {
    Transaction t =
        transactionRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + id));

    String sourceName;
    if (t.getApiClientId() != null) {
      sourceName =
          apiClientRepository
              .findById(t.getApiClientId())
              .map(ApiClient::getName)
              .orElse("Unknown Client");
    } else if (t.getInitiatedByUserId() != null) {
      sourceName =
          userRepository
              .findById(t.getInitiatedByUserId())
              .map(AppUser::getEmail)
              .orElse("Unknown User");
    } else {
      sourceName = "SYSTEM";
    }

    List<RuleHitResponse> ruleHits =
        transactionRuleHitRepository.findByTransactionIdOrderByRuleNameAsc(id).stream()
            .map(
                h ->
                    new RuleHitResponse(
                        h.getRuleId(),
                        h.getRuleName(),
                        h.getRuleType(),
                        h.getPoints(),
                        h.getAction(),
                        h.getDetail()))
            .toList();

    DisputeSummaryResponse disputeSummary =
        transactionDisputeLookup.findDisputeSummary(id).orElse(null);

    return TransactionDetail.fromEntity(
        t,
        t.getCard().getLast4(),
        t.getCard().getHolderName(),
        sourceName,
        ruleHits,
        disputeSummary);
  }
}
