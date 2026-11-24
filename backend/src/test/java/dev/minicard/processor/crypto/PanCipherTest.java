package dev.minicard.processor.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.minicard.processor.common.config.AppProperties;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PanCipherTest {

  private static final String KEY_K1_B64 =
      Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
  private static final String KEY_K2_B64 =
      Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes());
  private static final String HASH_KEY_B64 =
      Base64.getEncoder().encodeToString("hashSecretKeyMustBe32BytesMinLen!".getBytes());

  private PanCipher panCipher;

  @BeforeEach
  void setUp() {
    AppProperties properties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("admin@minicard.local", "secret"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties("secret", 15, 7), false, List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties(
                    "k1", Map.of("k1", KEY_K1_B64, "k2", KEY_K2_B64), HASH_KEY_B64)),
            new AppProperties.CardsProperties("990000", false),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    panCipher = new PanCipher(properties);
    panCipher.init();
  }

  @Test
  @DisplayName("Round-trip encryption and decryption succeeds")
  void roundTripEncryption() {
    UUID cardId = UUID.randomUUID();
    String plainPan = "9900001234567890";

    EncryptedPan encrypted = panCipher.encrypt(plainPan, cardId);
    assertThat(encrypted.keyId()).isEqualTo("k1");
    assertThat(encrypted.ciphertext()).isNotBlank();
    assertThat(encrypted.ciphertext()).doesNotContain(plainPan);

    String decrypted = panCipher.decrypt(encrypted.ciphertext(), encrypted.keyId(), cardId);
    assertThat(decrypted).isEqualTo(plainPan);
  }

  @Test
  @DisplayName("Decryption fails when AAD (cardId) does not match encryption cardId")
  void aadMismatchFailsDecryption() {
    UUID cardId1 = UUID.randomUUID();
    UUID cardId2 = UUID.randomUUID();
    String plainPan = "9900001234567890";

    EncryptedPan encrypted = panCipher.encrypt(plainPan, cardId1);

    assertThatThrownBy(() -> panCipher.decrypt(encrypted.ciphertext(), encrypted.keyId(), cardId2))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("corrupted data or AAD mismatch");
  }

  @Test
  @DisplayName("Decryption fails when ciphertext is tampered")
  void tamperedCiphertextFails() {
    UUID cardId = UUID.randomUUID();
    String plainPan = "9900001234567890";

    EncryptedPan encrypted = panCipher.encrypt(plainPan, cardId);
    byte[] decoded = Base64.getUrlDecoder().decode(encrypted.ciphertext());
    // Tamper the last byte
    decoded[decoded.length - 1] ^= 0x01;
    String tamperedBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);

    assertThatThrownBy(() -> panCipher.decrypt(tamperedBase64, encrypted.keyId(), cardId))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  @DisplayName("Key rotation supports decrypting with past key and encrypting with new key")
  void keyRotation() {
    UUID cardId1 = UUID.randomUUID();
    String plainPan1 = "9900001234567890";
    EncryptedPan enc1 = panCipher.encrypt(plainPan1, cardId1);
    assertThat(enc1.keyId()).isEqualTo("k1");

    // Reconfigure currentKeyId to k2
    AppProperties rotatedProperties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("admin@minicard.local", "secret"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties("secret", 15, 7), false, List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties(
                    "k2", Map.of("k1", KEY_K1_B64, "k2", KEY_K2_B64), HASH_KEY_B64)),
            new AppProperties.CardsProperties("990000", false),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    PanCipher rotatedCipher = new PanCipher(rotatedProperties);
    rotatedCipher.init();

    // New encryption uses k2
    UUID cardId2 = UUID.randomUUID();
    String plainPan2 = "9900009876543210";
    EncryptedPan enc2 = rotatedCipher.encrypt(plainPan2, cardId2);
    assertThat(enc2.keyId()).isEqualTo("k2");

    // Can decrypt old ciphertext enc1 (using k1) and new ciphertext enc2 (using k2)
    assertThat(rotatedCipher.decrypt(enc1.ciphertext(), enc1.keyId(), cardId1))
        .isEqualTo(plainPan1);
    assertThat(rotatedCipher.decrypt(enc2.ciphertext(), enc2.keyId(), cardId2))
        .isEqualTo(plainPan2);
  }
}
