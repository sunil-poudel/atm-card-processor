package dev.minicard.processor.crypto;

import java.util.Objects;

public record GeneratedPan(String pan, String panHash, String bin, String last4) {
  public GeneratedPan {
    Objects.requireNonNull(pan, "pan cannot be null");
    Objects.requireNonNull(panHash, "panHash cannot be null");
    Objects.requireNonNull(bin, "bin cannot be null");
    Objects.requireNonNull(last4, "last4 cannot be null");
    if (pan.length() != 16) {
      throw new IllegalArgumentException("PAN must be exactly 16 digits, got " + pan.length());
    }
  }

  public String maskedPan() {
    return bin + "******" + last4;
  }
}
