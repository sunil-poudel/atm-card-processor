package dev.minicard.processor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import dev.minicard.processor.security.SecurityConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class TokenServiceTest {

  private TokenService tokenService;
  private JwtDecoder jwtDecoder;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2026-10-21T06:00:00Z"), ZoneOffset.UTC);
    AppProperties properties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("admin@minicard.local", "AdminPassword123!"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties(
                    "dGVzdEp3dFNlY3JldEtleUZvck1pbmlDYXJkUHJvY2Vzc29yVGVzdGluZ1B1cnBvc2VzT25seTIwMjY=",
                    15,
                    7),
                false,
                List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties("k1", Map.of("k1", "k1"), "hashKey")),
            new AppProperties.CardsProperties("990000", false),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    SecurityConfig securityConfig = new SecurityConfig(properties, null, null, null, fixedClock);
    SecretKey secretKey = securityConfig.jwtSecretKey();
    JwtEncoder jwtEncoder = securityConfig.jwtEncoder(secretKey);
    jwtDecoder = securityConfig.jwtDecoder(secretKey);

    tokenService = new TokenService(jwtEncoder, properties, fixedClock);
  }

  @Test
  void generatesValidJwtWithExpectedClaims() {
    UUID userId = UUID.randomUUID();
    AppUser user =
        new AppUser(
            userId,
            "admin@minicard.local",
            "Admin User",
            "hash",
            UserRole.ADMIN,
            true,
            fixedClock.instant());

    String tokenValue = tokenService.generateAccessToken(user);
    assertThat(tokenValue).isNotBlank();

    Jwt decoded = jwtDecoder.decode(tokenValue);
    assertThat(decoded.getClaimAsString("iss")).isEqualTo("minicard-processor");
    assertThat(decoded.getAudience()).contains("minicard-dashboard");
    assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
    assertThat(decoded.getClaimAsString("email")).isEqualTo("admin@minicard.local");
    assertThat(decoded.getClaimAsString("displayName")).isEqualTo("Admin User");
    assertThat(decoded.getIssuedAt()).isEqualTo(Instant.parse("2026-10-21T06:00:00Z"));
    assertThat(decoded.getExpiresAt()).isEqualTo(Instant.parse("2026-10-21T06:15:00Z"));
  }

  @Test
  void generatesCryptographicRawRefreshTokenAndHash() {
    String raw = tokenService.generateRawRefreshToken();
    assertThat(raw).isNotBlank();

    String hash = tokenService.hashToken(raw);
    assertThat(hash).hasSize(64); // SHA-256 hex length
    assertThat(tokenService.hashToken(raw)).isEqualTo(hash); // Deterministic
  }
}
