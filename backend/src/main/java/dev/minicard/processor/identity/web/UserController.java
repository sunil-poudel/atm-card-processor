package dev.minicard.processor.identity.web;

import dev.minicard.processor.common.web.PageRequestFactory;
import dev.minicard.processor.common.web.PageResponse;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.service.UserService;
import dev.minicard.processor.identity.web.dto.CreateUserRequest;
import dev.minicard.processor.identity.web.dto.UpdateUserRequest;
import dev.minicard.processor.identity.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "Back-office staff user management (ADMIN only)")
public class UserController {

  private static final Set<String> ALLOWED_SORTS = Set.of("email", "displayName", "createdAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "email");

  private final UserService userService;
  private final Clock clock;

  public UserController(UserService userService, Clock clock) {
    this.userService = userService;
    this.clock = clock;
  }

  @GetMapping
  @Operation(summary = "List staff users with pagination and sorting")
  public PageResponse<UserResponse> listUsers(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {

    Pageable pageable = PageRequestFactory.create(page, size, sort, DEFAULT_SORT, ALLOWED_SORTS);
    Instant now = clock.instant();

    Page<UserResponse> userPage =
        userService.listUsers(pageable).map(user -> UserResponse.from(user, now));
    return PageResponse.from(userPage);
  }

  @PostMapping
  @Operation(summary = "Create a new staff user")
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {

    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorEmail = jwt.getClaimAsString("email");
    String ipAddress = httpRequest.getRemoteAddr();

    AppUser created =
        userService.createUser(
            request.email(),
            request.displayName(),
            request.role(),
            request.password(),
            actorId,
            actorEmail,
            ipAddress);

    Instant now = clock.instant();
    URI location = URI.create("/api/v1/users/" + created.getId());
    return ResponseEntity.created(location).body(UserResponse.from(created, now));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get user details by ID")
  public UserResponse getUser(@PathVariable UUID id) {
    AppUser user = userService.getUser(id);
    return UserResponse.from(user, clock.instant());
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update user details, role, or enabled status")
  public UserResponse updateUser(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest httpRequest) {

    UUID actorId = UUID.fromString(jwt.getSubject());
    String actorEmail = jwt.getClaimAsString("email");
    String ipAddress = httpRequest.getRemoteAddr();

    AppUser updated =
        userService.updateUser(
            id,
            request.displayName(),
            request.role(),
            request.enabled(),
            request.newPassword(),
            actorId,
            actorEmail,
            ipAddress);

    return UserResponse.from(updated, clock.instant());
  }
}
