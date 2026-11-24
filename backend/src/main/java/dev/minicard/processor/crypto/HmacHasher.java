package dev.minicard.processor.crypto;

import dev.minicard.processor.common.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class HmacHasher {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private final AppProperties appProperties;
  private byte[] keyBytes;

  public HmacHasher(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @PostConstruct
  public void init() {
    String rawHashKey = appProperties.crypto().pan().hashKey();
    if (rawHashKey == null || rawHashKey.isBlank()) {
      throw new IllegalStateException("Crypto pan hashKey must be configured");
    }
    try {
      this.keyBytes = Base64.getDecoder().decode(rawHashKey);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Crypto pan hashKey is not valid Base64", e);
    }
    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "Crypto pan hashKey must be at least 32 bytes (256 bits), got " + keyBytes.length);
    }
  }

  public String hash(String value) {
    if (value == null) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
      byte[] hashBytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC SHA-256 calculation failed", e);
    }
  }
}
