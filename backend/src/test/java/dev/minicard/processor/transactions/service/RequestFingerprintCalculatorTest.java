package dev.minicard.processor.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.minicard.processor.transactions.web.dto.AuthorizeRequest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFingerprintCalculatorTest {

  @Test
  @DisplayName("Fingerprint is 64 hex characters")
  void fingerprintLengthAndFormat() {
    UUID cardId = UUID.randomUUID();
    AuthorizeRequest req =
        new AuthorizeRequest(cardId, 50000L, "NPR", "Demo Mart", "5411", "POS-1042");

    String fp = RequestFingerprintCalculator.calculate(req);
    assertThat(fp).hasSize(64).matches("^[0-9a-f]{64}$");
  }

  @Test
  @DisplayName("Length-prefix prevents boundary ambiguity: ('ab','c') vs ('a','bc')")
  void lengthPrefixPreventsAmbiguity() {
    UUID cardId = UUID.randomUUID();

    // merchant = "ab", mcc = "54" vs merchant = "a", mcc = "b54"
    AuthorizeRequest req1 = new AuthorizeRequest(cardId, 1000L, "NPR", "ab", "5411", "ref");
    AuthorizeRequest req2 = new AuthorizeRequest(cardId, 1000L, "NPR", "a", "b5411", "ref");

    String fp1 = RequestFingerprintCalculator.calculate(req1);
    String fp2 = RequestFingerprintCalculator.calculate(req2);

    assertThat(fp1).isNotEqualTo(fp2);
  }

  @Test
  @DisplayName("Null reference and empty reference produce identical fingerprint")
  void nullAndEmptyReferenceProduceSameFingerprint() {
    UUID cardId = UUID.randomUUID();
    AuthorizeRequest reqNull =
        new AuthorizeRequest(cardId, 1000L, "NPR", "Demo Mart", "5411", null);
    AuthorizeRequest reqEmpty = new AuthorizeRequest(cardId, 1000L, "NPR", "Demo Mart", "5411", "");

    String fpNull = RequestFingerprintCalculator.calculate(reqNull);
    String fpEmpty = RequestFingerprintCalculator.calculate(reqEmpty);

    assertThat(fpNull).isEqualTo(fpEmpty);
  }

  @Test
  @DisplayName("Merchant name whitespace trimming produces same fingerprint")
  void merchantNameTrimmedProducesSameFingerprint() {
    UUID cardId = UUID.randomUUID();
    AuthorizeRequest req1 =
        new AuthorizeRequest(cardId, 1000L, "NPR", "  Demo Mart  ", "5411", "ref1");
    AuthorizeRequest req2 = new AuthorizeRequest(cardId, 1000L, "NPR", "Demo Mart", "5411", "ref1");

    assertThat(RequestFingerprintCalculator.calculate(req1))
        .isEqualTo(RequestFingerprintCalculator.calculate(req2));
  }
}
