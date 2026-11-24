package dev.minicard.processor.identity.service;

import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ErrorCode;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128;
  public static final int MAX_BYTES = 72;

  private final Set<String> commonPasswords;

  public PasswordPolicyValidator() {
    this.commonPasswords = loadCommonPasswords();
  }

  public void validate(String password, String email) {
    if (password == null || password.length() < MIN_LENGTH) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED,
          "Password must be at least " + MIN_LENGTH + " characters long");
    }
    if (password.length() > MAX_LENGTH) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "Password must not exceed " + MAX_LENGTH + " characters");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED,
          "Password exceeds the maximum limit of " + MAX_BYTES + " UTF-8 bytes");
    }
    if (email != null && password.equalsIgnoreCase(email.trim())) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "Password must not be identical to email address");
    }
    if (commonPasswords.contains(password.toLowerCase())) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "Password is too common; please choose a stronger password");
    }
  }

  private Set<String> loadCommonPasswords() {
    Set<String> set = new HashSet<>();
    try (InputStream in = getClass().getResourceAsStream("/security/common-passwords.txt")) {
      if (in != null) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
              set.add(trimmed.toLowerCase());
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Keep empty if unable to load
    }
    return set;
  }
}
