package dev.minicard.processor.identity.web;

import dev.minicard.processor.common.web.PageRequestFactory;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.service.ApiClientService;
import dev.minicard.processor.identity.web.dto.ApiClientCreatedResponse;
import dev.minicard.processor.identity.web.dto.ApiClientResponse;
import dev.minicard.processor.identity.web.dto.CreateApiClientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/api-clients")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "API Clients", description = "Machine client credential management (ADMIN only)")
public class ApiClientController {

  private static final Set<String> ALLOWED_SORTS = Set.of("name", "createdAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final ApiClientService apiClientService;

  public ApiClientController(ApiClientService apiClientService) {
    this.apiClientService = apiClientService;
  }

  @GetMapping
  @Operation(summary = "List API clients with pagination")
  public PageResponse<ApiClientResponse> listClients(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {

    Pageable pageable = PageRequestFactory.create(page, size, sort, DEFAULT_SORT, ALLOWED_SORTS);
    Page<ApiClientResponse> clientPage =
        apiClientService.listClients(pageable).map(ApiClientResponse::from);
    return PageResponse.from(clientPage);
  }

  @PostMapping
  @Operation(summary = "Register a new API client caller (API key returned once)")
  public ResponseEntity<ApiClientCreatedResponse> createClient(
      @Valid @RequestBody CreateApiClientRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {

    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorEmail = jwt.getClaimAsString("email");
    String ipAddress = httpRequest.getRemoteAddr();

    ApiClientService.ApiClientCreatedResult result =
        apiClientService.createClient(request.name(), actorId, actorEmail, ipAddress);

    URI location = URI.create("/api/v1/api-clients/" + result.client().getId());
    return ResponseEntity.created(location)
        .body(ApiClientCreatedResponse.from(result.client(), result.fullApiKey()));
  }

  @PostMapping("/{id}/revoke")
  @Operation(summary = "Revoke API client credentials")
  public ApiClientResponse revokeClient(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {

    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorEmail = jwt.getClaimAsString("email");
    String ipAddress = httpRequest.getRemoteAddr();

    ApiClient revoked = apiClientService.revokeClient(id, actorId, actorEmail, ipAddress);
    return ApiClientResponse.from(revoked);
  }
}
