package dev.minicard.processor.fraud.web;

import dev.minicard.processor.fraud.service.FraudRuleService;
import dev.minicard.processor.fraud.web.dto.CreateFraudRuleRequest;
import dev.minicard.processor.fraud.web.dto.FraudRuleResponse;
import dev.minicard.processor.fraud.web.dto.UpdateFraudRuleRequest;
import dev.minicard.processor.security.CallerIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fraud-rules")
@Tag(name = "Fraud Rules", description = "Fraud rule evaluation and administration")
@SecurityRequirement(name = "bearerAuth")
public class FraudRuleController {

  private final FraudRuleService fraudRuleService;

  public FraudRuleController(FraudRuleService fraudRuleService) {
    this.fraudRuleService = fraudRuleService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
  @Operation(summary = "List all fraud rules sorted by name")
  public ResponseEntity<List<FraudRuleResponse>> listRules() {
    return ResponseEntity.ok(fraudRuleService.listRules());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
  @Operation(summary = "Get a fraud rule by id")
  public ResponseEntity<FraudRuleResponse> getRule(@PathVariable UUID id) {
    return ResponseEntity.ok(fraudRuleService.getRule(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Create a fraud rule")
  public ResponseEntity<FraudRuleResponse> createRule(
      @Valid @RequestBody CreateFraudRuleRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    CallerIdentity caller = extractCaller(jwt, httpRequest);
    FraudRuleResponse response = fraudRuleService.createRule(request, caller);
    URI location = URI.create("/api/v1/fraud-rules/" + response.id());
    return ResponseEntity.created(location).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update an existing fraud rule")
  public ResponseEntity<FraudRuleResponse> updateRule(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateFraudRuleRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {
    CallerIdentity caller = extractCaller(jwt, httpRequest);
    FraudRuleResponse response = fraudRuleService.updateRule(id, request, caller);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/enable")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Enable a fraud rule")
  public ResponseEntity<FraudRuleResponse> enableRule(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
    CallerIdentity caller = extractCaller(jwt, httpRequest);
    return ResponseEntity.ok(fraudRuleService.enableRule(id, caller));
  }

  @PostMapping("/{id}/disable")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Disable a fraud rule")
  public ResponseEntity<FraudRuleResponse> disableRule(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
    CallerIdentity caller = extractCaller(jwt, httpRequest);
    return ResponseEntity.ok(fraudRuleService.disableRule(id, caller));
  }

  private CallerIdentity extractCaller(Jwt jwt, HttpServletRequest httpRequest) {
    if (jwt == null) {
      return null;
    }
    UUID userId = UUID.fromString(jwt.getSubject());
    String email = jwt.getClaimAsString("email");
    if (email == null) {
      email = jwt.getSubject();
    }
    return CallerIdentity.forUser(userId, email);
  }
}
