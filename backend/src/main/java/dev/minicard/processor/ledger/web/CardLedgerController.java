package dev.minicard.processor.ledger.web;

import dev.minicard.processor.cards.web.dto.TopUpRequest;
import dev.minicard.processor.common.web.PageRequestFactory;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.ledger.service.LedgerService;
import dev.minicard.processor.ledger.web.dto.LedgerEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards/{id}")
@Tag(name = "Cards", description = "Card ledger and top-up operations")
@SecurityRequirement(name = "bearerAuth")
public class CardLedgerController {

  private static final Set<String> ALLOWED_LEDGER_SORTS = Set.of("createdAt");
  private static final Sort DEFAULT_LEDGER_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final LedgerService ledgerService;

  public CardLedgerController(LedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @PostMapping("/topups")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Top up a card balance")
  public ResponseEntity<LedgerEntryResponse> topUpCard(
      @PathVariable UUID id,
      @Valid @RequestBody TopUpRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    if (actorLabel == null) {
      actorLabel = actorId.toString();
    }
    String ipAddress = httpRequest.getRemoteAddr();
    LedgerEntryResponse response =
        ledgerService.topUpCard(
            id, request.amountMinor(), request.description(), actorId, actorLabel, ipAddress);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/ledger")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "Get ledger entries for a card")
  public PageResponse<LedgerEntryResponse> getCardLedger(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PageRequestFactory.create(page, size, sort, DEFAULT_LEDGER_SORT, ALLOWED_LEDGER_SORTS);
    return PageResponse.from(ledgerService.getCardLedger(id, pageable));
  }
}
