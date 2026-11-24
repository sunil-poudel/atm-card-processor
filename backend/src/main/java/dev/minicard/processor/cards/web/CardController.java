package dev.minicard.processor.cards.web;

import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.service.CardQueryService;
import dev.minicard.processor.cards.service.CardService;
import dev.minicard.processor.cards.web.dto.BlockCardRequest;
import dev.minicard.processor.cards.web.dto.CardResponse;
import dev.minicard.processor.cards.web.dto.IssueCardRequest;
import dev.minicard.processor.cards.web.dto.UpdateBlockedMccsRequest;
import dev.minicard.processor.cards.web.dto.UpdateLimitsRequest;
import dev.minicard.processor.common.web.PageRequestFactory;
import dev.minicard.processor.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "Card issuance and life cycle management")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

  private static final Set<String> ALLOWED_CARD_SORTS =
      Set.of("createdAt", "holderName", "balanceMinor");
  private static final Sort DEFAULT_CARD_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final CardService cardService;
  private final CardQueryService cardQueryService;

  public CardController(CardService cardService, CardQueryService cardQueryService) {
    this.cardService = cardService;
    this.cardQueryService = cardQueryService;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Issue a new card")
  public ResponseEntity<CardResponse> issueCard(
      @Valid @RequestBody IssueCardRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID creatorId = UUID.fromString(jwt.getSubject());
    String creatorLabel = jwt.getClaimAsString("email");
    String ipAddress = httpRequest.getRemoteAddr();

    CardResponse response = cardService.issueCard(request, creatorId, creatorLabel, ipAddress);
    URI location = URI.create("/api/v1/cards/" + response.id());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "List cards with filters and pagination")
  public PageResponse<CardResponse> listCards(
      @RequestParam(required = false) CardStatus status,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean expired,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PageRequestFactory.create(page, size, sort, DEFAULT_CARD_SORT, ALLOWED_CARD_SORTS);
    return PageResponse.from(cardQueryService.listCards(status, currency, q, expired, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')")
  @Operation(summary = "Get card details by id")
  public CardResponse getCard(@PathVariable UUID id) {
    return cardQueryService.getCard(id);
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Activate an inactive card")
  public CardResponse activateCard(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    return cardService.activateCard(id, actorId, actorLabel, httpRequest.getRemoteAddr());
  }

  @PostMapping("/{id}/block")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Block an active or inactive card")
  public CardResponse blockCard(
      @PathVariable UUID id,
      @Valid @RequestBody BlockCardRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    return cardService.blockCard(id, request, actorId, actorLabel, httpRequest.getRemoteAddr());
  }

  @PostMapping("/{id}/unblock")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Unblock a blocked card")
  public CardResponse unblockCard(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    return cardService.unblockCard(id, actorId, actorLabel, httpRequest.getRemoteAddr());
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Close a card (requires zero balance)")
  public CardResponse closeCard(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    return cardService.closeCard(id, actorId, actorLabel, httpRequest.getRemoteAddr());
  }

  @PutMapping("/{id}/limits")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update transaction and daily limits")
  public CardResponse updateLimits(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateLimitsRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    return cardService.updateLimits(id, request, actorId, actorLabel, httpRequest.getRemoteAddr());
  }

  @PutMapping("/{id}/blocked-mccs")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update blocked merchant category codes")
  public CardResponse updateBlockedMccs(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBlockedMccsRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorLabel = jwt.getClaimAsString("email");
    return cardService.updateBlockedMccs(
        id, request, actorId, actorLabel, httpRequest.getRemoteAddr());
  }
}
