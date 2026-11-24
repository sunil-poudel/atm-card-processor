package dev.minicard.processor.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void validPropertiesPassValidation() {
    AppProperties properties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("admin@minicard.local", "secret"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties("jwt-secret", 15, 7), false, List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties("k1", Map.of("k1", "key1"), "hashKey")),
            new AppProperties.CardsProperties("990000", false, 5000000L, 20000000L),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
    assertThat(violations).isEmpty();
  }

  @Test
  void invalidBinFailsValidation() {
    AppProperties properties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("admin@minicard.local", "secret"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties("jwt-secret", 15, 7), false, List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties("k1", Map.of("k1", "key1"), "hashKey")),
            new AppProperties.CardsProperties(
                "123", false, 5000000L, 20000000L), // invalid BIN length
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("cards.bin"));
  }

  @Test
  void invalidAdminEmailFailsValidation() {
    AppProperties properties =
        new AppProperties(
            ZoneId.of("Asia/Kathmandu"),
            new AppProperties.BootstrapAdminProperties("not-an-email", "secret"),
            new AppProperties.SecurityProperties(
                new AppProperties.JwtProperties("jwt-secret", 15, 7), false, List.of()),
            new AppProperties.CryptoProperties(
                new AppProperties.PanCryptoProperties("k1", Map.of("k1", "key1"), "hashKey")),
            new AppProperties.CardsProperties("990000", false, 5000000L, 20000000L),
            new AppProperties.FraudProperties(50, 80),
            new AppProperties.DisputesProperties(7, 120, "0 0 * * * *"));

    Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().contains("bootstrapAdmin.email"));
  }
}
