package dev.minicard.processor.cards.service;

import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.crypto.GeneratedPan;
import dev.minicard.processor.crypto.HmacHasher;
import dev.minicard.processor.crypto.Luhn;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class PanGenerator {

  private static final int MAX_ATTEMPTS = 5;
  private final AppProperties appProperties;
  private final HmacHasher hmacHasher;
  private final CardRepository cardRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  public PanGenerator(
      AppProperties appProperties, HmacHasher hmacHasher, CardRepository cardRepository) {
    this.appProperties = appProperties;
    this.hmacHasher = hmacHasher;
    this.cardRepository = cardRepository;
  }

  public GeneratedPan generateUniquePan() {
    String bin = appProperties.cards().bin();
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      StringBuilder sb = new StringBuilder(bin);
      for (int i = 0; i < 9; i++) {
        sb.append(secureRandom.nextInt(10));
      }
      String payload = sb.toString();
      int checkDigit = Luhn.checkDigit(payload);
      String pan = payload + checkDigit;

      if (!Luhn.isValid(pan)) {
        throw new IllegalStateException("Generated PAN failed Luhn check internally");
      }

      String panHash = hmacHasher.hash(pan);
      if (!cardRepository.existsByPanHash(panHash)) {
        String last4 = pan.substring(12, 16);
        return new GeneratedPan(pan, panHash, bin, last4);
      }
    }
    throw new IllegalStateException(
        "Failed to generate unique PAN after " + MAX_ATTEMPTS + " attempts");
  }
}
