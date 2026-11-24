package dev.minicard.processor.cards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.crypto.GeneratedPan;
import dev.minicard.processor.crypto.HmacHasher;
import dev.minicard.processor.crypto.Luhn;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PanGeneratorTest {

  private static final String HASH_KEY_B64 =
      Base64.getEncoder().encodeToString("hashSecretKeyMustBe32BytesMinLen!".getBytes());

  private CardRepository cardRepository;
  private HmacHasher hmacHasher;
  private PanGenerator panGenerator;

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
                    "k1",
                    Map.of(
                        "k1",
                        Base64.getEncoder()
                            .encodeToString("01234567890123456789012345678901".getBytes())),
                    HASH_KEY_B64)),
            new AppProperties.CardsProperties("990000", false),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    cardRepository = mock(CardRepository.class);
    hmacHasher = new HmacHasher(properties);
    hmacHasher.init();
    panGenerator = new PanGenerator(properties, hmacHasher, cardRepository);
  }

  @Test
  @DisplayName("generateUniquePan creates 16-digit Luhn-valid PAN starting with BIN")
  void generatesValidPan() {
    when(cardRepository.existsByPanHash(anyString())).thenReturn(false);

    GeneratedPan generated = panGenerator.generateUniquePan();

    assertThat(generated.pan()).hasSize(16);
    assertThat(generated.bin()).isEqualTo("990000");
    assertThat(generated.pan()).startsWith("990000");
    assertThat(generated.last4()).isEqualTo(generated.pan().substring(12, 16));
    assertThat(Luhn.isValid(generated.pan())).isTrue();
    assertThat(generated.maskedPan()).isEqualTo("990000******" + generated.last4());
    assertThat(generated.panHash()).isEqualTo(hmacHasher.hash(generated.pan()));
  }

  @Test
  @DisplayName(
      "100,000 generated PANs property test: all length 16, start with 990000, all Luhn-valid")
  void propertyTest100kGeneratedPans() {
    when(cardRepository.existsByPanHash(anyString())).thenReturn(false);

    for (int i = 0; i < 100_000; i++) {
      GeneratedPan generated = panGenerator.generateUniquePan();
      assertThat(generated.pan()).hasSize(16);
      assertThat(generated.pan()).startsWith("990000");
      assertThat(Luhn.isValid(generated.pan())).isTrue();
    }
  }

  @Test
  @DisplayName("Retries PAN generation on unique constraint collision on panHash")
  void retriesOnCollision() {
    when(cardRepository.existsByPanHash(anyString()))
        .thenReturn(true) // first attempt collision
        .thenReturn(false); // second attempt unique

    GeneratedPan generated = panGenerator.generateUniquePan();

    assertThat(generated).isNotNull();
    assertThat(Luhn.isValid(generated.pan())).isTrue();
    verify(cardRepository, times(2)).existsByPanHash(anyString());
  }
}
