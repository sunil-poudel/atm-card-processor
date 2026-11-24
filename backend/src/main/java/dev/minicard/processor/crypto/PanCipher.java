package dev.minicard.processor.crypto;

import dev.minicard.processor.common.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PanCipher {

  private static final String ALGORITHM = "AES";
  private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int GCM_TAG_LENGTH_BYTES = 16;

  private final AppProperties appProperties;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Map<String, SecretKey> keyMap = new HashMap<>();
  private String currentKeyId;

  public PanCipher(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @PostConstruct
  public void init() {
    AppProperties.PanCryptoProperties panCrypto = appProperties.crypto().pan();
    this.currentKeyId = panCrypto.currentKeyId();
    Map<String, String> keys = panCrypto.keys();

    if (keys == null || keys.isEmpty()) {
      throw new IllegalStateException("Crypto pan keys map cannot be null or empty");
    }
    if (currentKeyId == null || !keys.containsKey(currentKeyId)) {
      throw new IllegalStateException(
          "Crypto pan currentKeyId '" + currentKeyId + "' is not defined in keys map");
    }

    for (Map.Entry<String, String> entry : keys.entrySet()) {
      String keyId = entry.getKey();
      String rawBase64 = entry.getValue();
      if (rawBase64 == null || rawBase64.isBlank()) {
        throw new IllegalStateException("Key for keyId '" + keyId + "' is blank");
      }
      byte[] decoded;
      try {
        decoded = Base64.getDecoder().decode(rawBase64);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("Key for keyId '" + keyId + "' is not valid Base64", e);
      }
      if (decoded.length != 32) {
        throw new IllegalStateException(
            "Key for keyId '" + keyId + "' must be 32 bytes (256 bits), got " + decoded.length);
      }
      keyMap.put(keyId, new SecretKeySpec(decoded, ALGORITHM));
    }
  }

  public EncryptedPan encrypt(String pan, UUID cardId) {
    Objects.requireNonNull(pan, "pan cannot be null");
    Objects.requireNonNull(cardId, "cardId cannot be null");

    SecretKey secretKey = keyMap.get(currentKeyId);
    if (secretKey == null) {
      throw new IllegalStateException("Current key not found: " + currentKeyId);
    }

    byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
    secureRandom.nextBytes(iv);

    try {
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
      cipher.updateAAD(cardId.toString().getBytes(StandardCharsets.UTF_8));

      byte[] cipherWithTag = cipher.doFinal(pan.getBytes(StandardCharsets.UTF_8));

      ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherWithTag.length);
      buffer.put(iv);
      buffer.put(cipherWithTag);

      String base64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
      return new EncryptedPan(currentKeyId, base64Url);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  public String decrypt(String base64Url, String keyId, UUID cardId) {
    Objects.requireNonNull(base64Url, "ciphertext cannot be null");
    Objects.requireNonNull(keyId, "keyId cannot be null");
    Objects.requireNonNull(cardId, "cardId cannot be null");

    SecretKey secretKey = keyMap.get(keyId);
    if (secretKey == null) {
      throw new IllegalStateException("Unknown keyId for decryption: " + keyId);
    }

    byte[] combined;
    try {
      combined = Base64.getUrlDecoder().decode(base64Url);
    } catch (IllegalArgumentException e) {
      throw new SecurityException("Ciphertext is not valid Base64-URL", e);
    }

    if (combined.length < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
      throw new SecurityException("Ciphertext too short for IV and GCM tag");
    }

    byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
    System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

    int cipherLength = combined.length - GCM_IV_LENGTH_BYTES;
    byte[] cipherWithTag = new byte[cipherLength];
    System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherWithTag, 0, cipherLength);

    try {
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
      cipher.updateAAD(cardId.toString().getBytes(StandardCharsets.UTF_8));

      byte[] plainBytes = cipher.doFinal(cipherWithTag);
      return new String(plainBytes, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new SecurityException("Decryption failed: corrupted data or AAD mismatch", e);
    }
  }
}
