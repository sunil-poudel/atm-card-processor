package dev.minicard.processor.crypto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PanMasker {

  private static final Pattern PAN_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");

  private PanMasker() {}

  public static String mask(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }
    Matcher matcher = PAN_PATTERN.matcher(message);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String candidate = matcher.group();
      if (Luhn.isValid(candidate)) {
        String masked = maskPan(candidate);
        matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
      } else {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(candidate));
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  public static String maskPan(String pan) {
    if (pan == null || pan.length() < 10) {
      return "******";
    }
    int len = pan.length();
    return pan.substring(0, 6) + "*".repeat(len - 10) + pan.substring(len - 4);
  }
}
