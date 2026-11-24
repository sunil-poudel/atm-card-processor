package dev.minicard.processor.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PanMaskerTest {

  @Test
  @DisplayName("Masks valid Luhn PAN preserving first 6 and last 4 digits")
  void testMaskValidPan() {
    // 79927398713 is a 11-digit Luhn test vector
    // Let's generate a 16-digit Luhn number: 990000123456789
    int check = Luhn.checkDigit("990000123456789");
    String validPan = "990000123456789" + check;

    String text = "Transaction on card " + validPan + " approved";
    String masked = PanMasker.mask(text);

    assertThat(masked).isEqualTo("Transaction on card 990000******789" + check + " approved");
  }

  @Test
  @DisplayName("Leaves non-Luhn sequences and short numbers unchanged")
  void testLeavesNonLuhnUnchanged() {
    String text = "User id 1234567890123456 has phone 9841234567";
    // If not Luhn valid, it should not be masked
    if (!Luhn.isValid("1234567890123456")) {
      assertThat(PanMasker.mask(text)).isEqualTo(text);
    }
  }

  @Test
  @DisplayName("MaskPan handles short strings gracefully")
  void testMaskPanShort() {
    assertThat(PanMasker.maskPan("12345")).isEqualTo("******");
    assertThat(PanMasker.maskPan(null)).isEqualTo("******");
  }
}
