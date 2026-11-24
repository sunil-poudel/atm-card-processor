package dev.minicard.processor.crypto;

import java.util.Objects;

public final class Luhn {

  private Luhn() {}

  public static boolean isValid(String pan) {
    if (pan == null || pan.isEmpty()) {
      return false;
    }
    int sum = 0;
    int len = pan.length();
    for (int i = 0; i < len; i++) {
      char c = pan.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
      int digit = c - '0';
      // From rightmost (index len - 1), every second digit moving left is doubled
      int positionFromRight = len - 1 - i;
      if (positionFromRight % 2 == 1) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
    }
    return sum % 10 == 0;
  }

  public static int checkDigit(String payload) {
    Objects.requireNonNull(payload, "payload cannot be null");
    if (payload.isEmpty()) {
      throw new IllegalArgumentException("payload cannot be empty");
    }
    int sum = 0;
    int len = payload.length();
    for (int i = 0; i < len; i++) {
      char c = payload.charAt(i);
      if (c < '0' || c > '9') {
        throw new IllegalArgumentException("payload must contain only decimal digits");
      }
      int digit = c - '0';
      // Since check digit will be appended at positionFromRight = 0,
      // payload's last character will be at positionFromRight = 1 (doubled)
      int positionFromRight = len - i;
      if (positionFromRight % 2 == 1) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
    }
    return (10 - (sum % 10)) % 10;
  }
}
