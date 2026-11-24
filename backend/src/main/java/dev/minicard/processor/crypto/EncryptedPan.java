package dev.minicard.processor.crypto;

import java.util.Objects;

public record EncryptedPan(String keyId, String ciphertext) {
  public EncryptedPan {
    Objects.requireNonNull(keyId, "keyId cannot be null");
    Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
  }
}
