package dev.minicard.processor.identity.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.UnauthenticatedException;
import dev.minicard.processor.common.id.UuidV7;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.RefreshToken;
import dev.minicard.processor.identity.repository.RefreshTokenRepository;
import dev.minicard.processor.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  public static final String REFRESH_COOKIE_NAME = "mc_refresh";
  public static final String AUTH_PATH = "/api/v1/auth";
  public static final int MAX_FAILED_ATTEMPTS = 5;
  public static final int LOCKOUT_MINUTES = 15;

  // Fixed dummy BCrypt hash for timing equalization when user doesn't exist
  private static final String DUMMY_HASH =
      "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final TokenService tokenService;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final AppProperties appProperties;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      TokenService tokenService,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      AppProperties appProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.tokenService = tokenService;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.appProperties = appProperties;
    this.clock = clock;
  }

  @Transactional(noRollbackFor = UnauthenticatedException.class)
  public LoginResult login(String rawEmail, String password, String userAgent, String ipAddress) {
    Instant now = clock.instant();
    String email = AppUser.normalizeEmail(rawEmail);
    Optional<AppUser> userOpt = userRepository.findByEmailIgnoreCase(email);

    if (userOpt.isEmpty()) {
      passwordEncoder.matches(password, DUMMY_HASH);
      auditService.record(
          ActorType.ANONYMOUS,
          null,
          email,
          "LOGIN_FAILED",
          "AppUser",
          null,
          ipAddress,
          "reason=user_not_found");
      throw new UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    AppUser user = userOpt.get();

    if (!user.isEnabled()) {
      auditService.record(
          ActorType.USER,
          user.getId(),
          user.getEmail(),
          "LOGIN_FAILED",
          "AppUser",
          user.getId(),
          ipAddress,
          "reason=disabled");
      throw new UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    if (user.isLocked(now)) {
      auditService.record(
          ActorType.USER,
          user.getId(),
          user.getEmail(),
          "LOGIN_FAILED",
          "AppUser",
          user.getId(),
          ipAddress,
          "reason=locked");
      throw new UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      boolean locked = user.recordLoginFailure(now, MAX_FAILED_ATTEMPTS, LOCKOUT_MINUTES);
      userRepository.save(user);
      if (locked) {
        auditService.record(
            ActorType.USER,
            user.getId(),
            user.getEmail(),
            "ACCOUNT_LOCKED",
            "AppUser",
            user.getId(),
            ipAddress,
            "attempts=5");
      }
      auditService.record(
          ActorType.USER,
          user.getId(),
          user.getEmail(),
          "LOGIN_FAILED",
          "AppUser",
          user.getId(),
          ipAddress,
          "reason=bad_password");
      throw new UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    user.recordLoginSuccess(now);
    userRepository.save(user);

    String accessToken = tokenService.generateAccessToken(user);
    String rawRefreshToken = tokenService.generateRawRefreshToken();
    String refreshHash = tokenService.hashToken(rawRefreshToken);

    int refreshDays = appProperties.security().jwt().refreshTokenExpirationDays();
    Instant refreshExpiresAt = now.plus(Duration.ofDays(refreshDays));
    UUID familyId = UuidV7.newId();

    RefreshToken refreshToken =
        new RefreshToken(null, user, familyId, refreshHash, now, refreshExpiresAt, userAgent);
    refreshTokenRepository.save(refreshToken);

    auditService.record(
        ActorType.USER,
        user.getId(),
        user.getEmail(),
        "LOGIN_SUCCESS",
        "AppUser",
        user.getId(),
        ipAddress,
        null);

    int accessExpirySeconds = appProperties.security().jwt().accessTokenExpirationMinutes() * 60;
    return new LoginResult(accessToken, accessExpirySeconds, user, rawRefreshToken);
  }

  @Transactional(noRollbackFor = UnauthenticatedException.class)
  public LoginResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
    Instant now = clock.instant();
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new UnauthenticatedException(
          ErrorCode.REFRESH_TOKEN_INVALID, "Refresh cookie is missing");
    }

    String hash = tokenService.hashToken(rawRefreshToken);
    Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHashForUpdate(hash);

    if (tokenOpt.isEmpty() || tokenOpt.get().isExpired(now)) {
      throw new UnauthenticatedException(
          ErrorCode.REFRESH_TOKEN_INVALID, "Invalid or expired refresh token");
    }

    RefreshToken currentToken = tokenOpt.get();
    AppUser user = currentToken.getUser();
    UUID userId = user.getId();
    String userEmail = user.getEmail();
    UUID familyId = currentToken.getFamilyId();
    UUID tokenId = currentToken.getId();

    // Check reuse detection
    if (currentToken.isRevoked()) {
      refreshTokenRepository.revokeFamily(familyId, now);
      auditService.record(
          ActorType.USER,
          userId,
          userEmail,
          "TOKEN_REUSE_DETECTED",
          "RefreshToken",
          tokenId,
          ipAddress,
          "familyId=" + familyId);
      throw new UnauthenticatedException(
          ErrorCode.REFRESH_TOKEN_INVALID, "Token reuse detected; session revoked");
    }

    if (!user.isEnabled()) {
      refreshTokenRepository.revokeFamily(familyId, now);
      throw new UnauthenticatedException(
          ErrorCode.REFRESH_TOKEN_INVALID, "User account is disabled");
    }

    String newRawToken = tokenService.generateRawRefreshToken();
    String newHash = tokenService.hashToken(newRawToken);

    RefreshToken successor =
        new RefreshToken(
            null,
            user,
            currentToken.getFamilyId(),
            newHash,
            now,
            currentToken.getExpiresAt(), // Do not extend family expiration
            userAgent);
    RefreshToken savedSuccessor = refreshTokenRepository.save(successor);
    currentToken.replaceWith(savedSuccessor.getId(), now);
    refreshTokenRepository.save(currentToken);

    String newAccessToken = tokenService.generateAccessToken(user);
    int accessExpirySeconds = appProperties.security().jwt().accessTokenExpirationMinutes() * 60;
    return new LoginResult(newAccessToken, accessExpirySeconds, user, newRawToken);
  }

  @Transactional
  public void logout(String rawRefreshToken, String ipAddress) {
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      Instant now = clock.instant();
      String hash = tokenService.hashToken(rawRefreshToken);
      refreshTokenRepository
          .findByTokenHash(hash)
          .ifPresent(
              token -> {
                UUID userId = token.getUser().getId();
                String email = token.getUser().getEmail();
                UUID familyId = token.getFamilyId();
                refreshTokenRepository.revokeFamily(familyId, now);
                auditService.record(
                    ActorType.USER, userId, email, "LOGOUT", "AppUser", userId, ipAddress, null);
              });
    }
  }

  public ResponseCookie createRefreshCookie(String rawRefreshToken) {
    boolean secure = appProperties.security().cookieSecure();
    int refreshDays = appProperties.security().jwt().refreshTokenExpirationDays();
    return ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
        .httpOnly(true)
        .secure(secure)
        .path(AUTH_PATH)
        .sameSite("Strict")
        .maxAge(Duration.ofDays(refreshDays))
        .build();
  }

  public ResponseCookie createClearRefreshCookie() {
    boolean secure = appProperties.security().cookieSecure();
    return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secure)
        .path(AUTH_PATH)
        .sameSite("Strict")
        .maxAge(0)
        .build();
  }

  public record LoginResult(
      String accessToken, int expiresInSeconds, AppUser user, String rawRefreshToken) {}
}
