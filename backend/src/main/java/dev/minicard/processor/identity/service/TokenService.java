package dev.minicard.processor.identity.service;

import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.id.UuidV7;
import dev.minicard.processor.identity.domain.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  public static final String ISSUER = "minicard-processor";
  public static final String AUDIENCE = "minicard-dashboard";

  private final JwtEncoder jwtEncoder;
  private final AppProperties appProperties;
  private final Clock clock;
  private final SecureRandom secureRandom;

  public TokenService(JwtEncoder jwtEncoder, AppProperties appProperties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.appProperties = appProperties;
    this.clock = clock;
    this.secureRandom = new SecureRandom();
  }

  public String generateAccessToken(AppUser user) {
    Instant now = clock.instant();
    int expiryMinutes = appProperties.security().jwt().accessTokenExpirationMinutes();
    Instant expiresAt = now.plus(Duration.ofMinutes(expiryMinutes));

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.getId().toString())
            .audience(List.of(AUDIENCE))
            .claim("role", user.getRole().name())
            .claim("email", user.getEmail())
            .claim("displayName", user.getDisplayName())
            .id(UuidV7.newId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public String generateRawRefreshToken() {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
