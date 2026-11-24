package dev.minicard.processor.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LuhnTest {

  @Test
  @DisplayName("Standard known valid Luhn vectors evaluate to true")
  void standardValidVectors() {
    assertThat(Luhn.isValid("79927398713")).isTrue();
    assertThat(Luhn.isValid("49927398716")).isTrue();
    assertThat(Luhn.isValid("1234567812345670")).isTrue();
    assertThat(Luhn.isValid("9900001234567899")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "79927398710",
        "79927398711",
        "79927398712",
        "79927398714",
        "79927398715",
        "79927398716",
        "79927398717",
        "79927398718",
        "79927398719"
      })
  @DisplayName("Altered check digits on known payload evaluate to false")
  void invalidCheckDigits(String pan) {
    assertThat(Luhn.isValid(pan)).isFalse();
  }

  @Test
  @DisplayName("Malformed and edge cases evaluate to false")
  void malformedInputs() {
    assertThat(Luhn.isValid(null)).isFalse();
    assertThat(Luhn.isValid("")).isFalse();
    assertThat(Luhn.isValid("abc")).isFalse();
    assertThat(Luhn.isValid("7992739871A")).isFalse();
    assertThat(Luhn.isValid("799 273 98713")).isFalse();
  }

  @Test
  @DisplayName("checkDigit generates the correct Luhn check digit")
  void checkDigitCalculation() {
    assertThat(Luhn.checkDigit("7992739871")).isEqualTo(3);
    assertThat(Luhn.checkDigit("4992739871")).isEqualTo(6);
    assertThat(Luhn.checkDigit("123456781234567")).isEqualTo(0);
    assertThat(Luhn.checkDigit("990000123456789")).isEqualTo(9);

    String payload = "990000555123456";
    int cd = Luhn.checkDigit(payload);
    assertThat(Luhn.isValid(payload + cd)).isTrue();
  }

  @Test
  @DisplayName("checkDigit rejects empty or non-digit payload")
  void checkDigitRejection() {
    assertThatThrownBy(() -> Luhn.checkDigit("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Luhn.checkDigit("123X")).isInstanceOf(IllegalArgumentException.class);
  }
}
