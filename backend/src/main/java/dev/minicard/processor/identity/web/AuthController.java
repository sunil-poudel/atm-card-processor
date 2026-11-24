package dev.minicard.processor.identity.web;

import dev.minicard.processor.common.error.ForbiddenException;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.service.AuthService;
import dev.minicard.processor.identity.service.UserService;
import dev.minicard.processor.identity.web.dto.LoginRequest;
import dev.minicard.processor.identity.web.dto.LoginResponse;
import dev.minicard.processor.identity.web.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for back-office staff session lifecycle")
public class AuthController {

  private final AuthService authService;
  private final UserService userService;

  public AuthController(AuthService authService, UserService userService) {
    this.authService = authService;
    this.userService = userService;
  }

  @PostMapping("/login")
  @Operation(summary = "Authenticate with email and password")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {

    String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
    String ipAddress = httpRequest.getRemoteAddr();

    AuthService.LoginResult result =
        authService.login(request.email(), request.password(), userAgent, ipAddress);

    ResponseCookie cookie = authService.createRefreshCookie(result.rawRefreshToken());
    LoginResponse response =
        new LoginResponse(
            result.accessToken(),
            result.expiresInSeconds(),
            UserSummaryResponse.from(result.user()));

    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate refresh token and issue new access token")
  public ResponseEntity<LoginResponse> refresh(
      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
      @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String refreshCookie,
      HttpServletRequest httpRequest) {

    validateRequestedWithHeader(requestedWith);

    String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
    String ipAddress = httpRequest.getRemoteAddr();

    try {
      AuthService.LoginResult result = authService.refresh(refreshCookie, userAgent, ipAddress);
      ResponseCookie cookie = authService.createRefreshCookie(result.rawRefreshToken());
      LoginResponse response =
          new LoginResponse(
              result.accessToken(),
              result.expiresInSeconds(),
              UserSummaryResponse.from(result.user()));

      return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
    } catch (Exception ex) {
      ResponseCookie clearCookie = authService.createClearRefreshCookie();
      throw ex;
    }
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Log out user, revoking refresh token family")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
      @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String refreshCookie,
      HttpServletRequest httpRequest) {

    validateRequestedWithHeader(requestedWith);

    String ipAddress = httpRequest.getRemoteAddr();
    authService.logout(refreshCookie, ipAddress);

    ResponseCookie clearCookie = authService.createClearRefreshCookie();
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
        .build();
  }

  @GetMapping("/me")
  @Operation(summary = "Get current authenticated staff user profile")
  public UserSummaryResponse me(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    AppUser user = userService.getUser(userId);
    return UserSummaryResponse.from(user);
  }

  private void validateRequestedWithHeader(String header) {
    if (header == null || !"XMLHttpRequest".equalsIgnoreCase(header.trim())) {
      throw new ForbiddenException("Missing or invalid X-Requested-With header");
    }
  }
}
