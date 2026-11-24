package dev.minicard.processor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.UnauthenticatedException;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import dev.minicard.processor.identity.repository.RefreshTokenRepository;
import dev.minicard.processor.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private UserRepository userRepository;
  private RefreshTokenRepository refreshTokenRepository;
  private TokenService tokenService;
  private PasswordEncoder passwordEncoder;
  private AuditService auditService;
  private Clock fixedClock;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    refreshTokenRepository = mock(RefreshTokenRepository.class);
    tokenService = mock(TokenService.class);
    passwordEncoder = mock(PasswordEncoder.class);
    auditService = mock(AuditService.class);
    fixedClock = Clock.fixed(Instant.parse("2026-10-21T06:00:00Z"), ZoneOffset.UTC);

    AppProperties properties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("admin@minicard.local", "secret"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties("secret", 15, 7), false, List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties("k1", Map.of("k1", "key1"), "hashKey")),
            new AppProperties.CardsProperties("990000", false),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    authService =
        new AuthService(
            userRepository,
            refreshTokenRepository,
            tokenService,
            passwordEncoder,
            auditService,
            properties,
            fixedClock);
  }

  @Test
  void unknownUserTriggersDummyPasswordComparisonAndThrowsInvalidCredentials() {
    when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.login("unknown@example.com", "Password123!", "Agent", "127.0.0.1"))
        .isInstanceOf(UnauthenticatedException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

    // Verifies timing equalization ran dummy match
    verify(passwordEncoder).matches(eq("Password123!"), any());
  }

  @Test
  void wrongPasswordIncrementsFailedAttemptsAndLocksAccountAtThreshold() {
    AppUser user =
        new AppUser(
            UUID.randomUUID(),
            "analyst@minicard.local",
            "Analyst",
            "$2a$12$somehash",
            UserRole.ANALYST,
            true,
            fixedClock.instant());

    when(userRepository.findByEmailIgnoreCase("analyst@minicard.local"))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("WrongPass123!", "$2a$12$somehash")).thenReturn(false);

    // Fail 4 times
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(
              () ->
                  authService.login(
                      "analyst@minicard.local", "WrongPass123!", "Agent", "127.0.0.1"))
          .isInstanceOf(UnauthenticatedException.class);
      assertThat(user.isLocked(fixedClock.instant())).isFalse();
    }

    // 5th failure triggers lockout
    assertThatThrownBy(
            () ->
                authService.login("analyst@minicard.local", "WrongPass123!", "Agent", "127.0.0.1"))
        .isInstanceOf(UnauthenticatedException.class);

    assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
    assertThat(user.isLocked(fixedClock.instant())).isTrue();
    assertThat(user.getLockedUntil()).isEqualTo(Instant.parse("2026-10-21T06:15:00Z"));
  }

  @Test
  void loginSuccessResetsFailedAttemptsAndIssuesTokens() {
    AppUser user =
        new AppUser(
            UUID.randomUUID(),
            "analyst@minicard.local",
            "Analyst",
            "$2a$12$somehash",
            UserRole.ANALYST,
            true,
            fixedClock.instant());

    user.recordLoginFailure(fixedClock.instant(), 5, 15);
    assertThat(user.getFailedLoginAttempts()).isEqualTo(1);

    when(userRepository.findByEmailIgnoreCase("analyst@minicard.local"))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("CorrectPass123!", "$2a$12$somehash")).thenReturn(true);
    when(tokenService.generateAccessToken(user)).thenReturn("jwt.token.value");
    when(tokenService.generateRawRefreshToken()).thenReturn("raw-refresh-token");
    when(tokenService.hashToken("raw-refresh-token")).thenReturn("hash-val");

    AuthService.LoginResult result =
        authService.login("analyst@minicard.local", "CorrectPass123!", "Agent", "127.0.0.1");

    assertThat(result.accessToken()).isEqualTo("jwt.token.value");
    assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh-token");
    assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
    assertThat(user.getLockedUntil()).isNull();
  }
}
