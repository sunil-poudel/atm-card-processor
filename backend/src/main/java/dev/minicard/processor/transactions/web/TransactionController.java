package dev.minicard.processor.transactions.web;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.common.web.PageRequestFactory;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.transactions.domain.DeclineReason;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.service.CsvExportService;
import dev.minicard.processor.transactions.service.TransactionQueryService;
import dev.minicard.processor.transactions.web.dto.TransactionDetail;
import dev.minicard.processor.transactions.web.dto.TransactionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Transaction search, detail, and export views")
public class TransactionController {

  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
  private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "amountMinor");

  private final TransactionQueryService transactionQueryService;
  private final CsvExportService csvExportService;

  public TransactionController(
      TransactionQueryService transactionQueryService, CsvExportService csvExportService) {
    this.transactionQueryService = transactionQueryService;
    this.csvExportService = csvExportService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "Search transactions with filters and pagination")
  public PageResponse<TransactionSummary> listTransactions(
      @RequestParam(required = false) UUID cardId,
      @RequestParam(required = false) TransactionStatus status,
      @RequestParam(required = false) String responseCode,
      @RequestParam(required = false) DeclineReason declineReason,
      @RequestParam(required = false) String mcc,
      @RequestParam(required = false) Boolean flagged,
      @RequestParam(required = false) Long minAmountMinor,
      @RequestParam(required = false) Long maxAmountMinor,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {

    Pageable pageable = PageRequestFactory.create(page, size, sort, DEFAULT_SORT, ALLOWED_SORTS);
    return PageResponse.from(
        transactionQueryService.listTransactions(
            cardId,
            status,
            responseCode,
            declineReason,
            mcc,
            flagged,
            minAmountMinor,
            maxAmountMinor,
            from,
            to,
            q,
            pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "Get transaction details")
  public TransactionDetail getTransaction(@PathVariable UUID id) {
    return transactionQueryService.getTransaction(id);
  }

  @GetMapping("/export.csv")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
  @Operation(summary = "Export transactions as RFC 4180 CSV with formula injection protection")
  public ResponseEntity<byte[]> exportTransactions(
      @RequestParam(required = false) UUID cardId,
      @RequestParam(required = false) TransactionStatus status,
      @RequestParam(required = false) String responseCode,
      @RequestParam(required = false) DeclineReason declineReason,
      @RequestParam(required = false) String mcc,
      @RequestParam(required = false) Boolean flagged,
      @RequestParam(required = false) Long minAmountMinor,
      @RequestParam(required = false) Long maxAmountMinor,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String q,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {

    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel =
        jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject();
    ActorType actorType = ActorType.USER;
    String ipAddress = httpRequest.getRemoteAddr();

    CsvExportService.CsvExportResult result =
        csvExportService.exportTransactions(
            cardId,
            status,
            responseCode,
            declineReason,
            mcc,
            flagged,
            minAmountMinor,
            maxAmountMinor,
            from,
            to,
            q,
            actorId,
            actorLabel,
            actorType,
            ipAddress);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(result.filename()).build());

    return new ResponseEntity<>(result.csvBytes(), headers, HttpStatus.OK);
  }
}
