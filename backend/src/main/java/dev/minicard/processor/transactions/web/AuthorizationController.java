package dev.minicard.processor.transactions.web;

import dev.minicard.processor.common.error.UnauthenticatedException;
import dev.minicard.processor.security.ApiClientPrincipal;
import dev.minicard.processor.security.ApiKeyAuthenticationToken;
import dev.minicard.processor.security.CallerIdentity;
import dev.minicard.processor.transactions.service.AuthorizationService;
import dev.minicard.processor.transactions.service.AuthorizeExecutionResult;
import dev.minicard.processor.transactions.web.dto.AuthorizeRequest;
import dev.minicard.processor.transactions.web.dto.AuthorizeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authorize")
@Tag(name = "Authorization", description = "POS transaction authorization operations")
public class AuthorizationController {

  private final AuthorizationService authorizationService;

  public AuthorizationController(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('API_CLIENT', 'ADMIN')")
  @Operation(summary = "Authorize a card purchase transaction")
  public ResponseEntity<AuthorizeResponse> authorize(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AuthorizeRequest request,
      Authentication authentication) {

    CallerIdentity caller = resolveCaller(authentication);
    AuthorizeExecutionResult result =
        authorizationService.authorize(caller, idempotencyKey, request);

    HttpHeaders headers = new HttpHeaders();
    if (result.replayed()) {
      headers.add("Idempotent-Replayed", "true");
    }

    return ResponseEntity.ok().headers(headers).body(result.response());
  }

  private CallerIdentity resolveCaller(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof ApiClientPrincipal principal) {
      return CallerIdentity.forApiClient(principal.id(), principal.name());
    }
    if (authentication instanceof ApiKeyAuthenticationToken apiKeyToken) {
      ApiClientPrincipal principal = apiKeyToken.getPrincipal();
      return CallerIdentity.forApiClient(principal.id(), principal.name());
    }
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      UUID userId = UUID.fromString(jwt.getSubject());
      String email = jwt.getClaimAsString("email");
      return CallerIdentity.forUser(userId, email != null ? email : userId.toString());
    }
    org.slf4j.LoggerFactory.getLogger(AuthorizationController.class)
        .warn(
            "resolveCaller failed: authentication={}, principal={}",
            authentication != null ? authentication.getClass().getName() : "null",
            authentication != null ? authentication.getPrincipal() : "null");
    throw new UnauthenticatedException("Valid authentication required for authorization");
  }
}
