package dev.minicard.processor.transactions.web;

import dev.minicard.processor.common.web.PageRequestFactory;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.service.TransactionQueryService;
import dev.minicard.processor.transactions.web.dto.TransactionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards/{id}/transactions")
@Tag(name = "Cards", description = "Card transactions")
@SecurityRequirement(name = "bearerAuth")
public class CardTransactionsController {

  private static final Set<String> ALLOWED_TRANSACTION_SORTS = Set.of("createdAt", "amountMinor");
  private static final Sort DEFAULT_TRANSACTION_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final TransactionQueryService transactionQueryService;

  public CardTransactionsController(TransactionQueryService transactionQueryService) {
    this.transactionQueryService = transactionQueryService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "Get transactions for a card")
  public PageResponse<TransactionSummary> getCardTransactions(
      @PathVariable UUID id,
      @RequestParam(required = false) TransactionStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Boolean flagged,
      @RequestParam(required = false) String mcc,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PageRequestFactory.create(
            page, size, sort, DEFAULT_TRANSACTION_SORT, ALLOWED_TRANSACTION_SORTS);
    return PageResponse.from(
        transactionQueryService.listTransactions(id, status, from, to, flagged, mcc, pageable));
  }
}
