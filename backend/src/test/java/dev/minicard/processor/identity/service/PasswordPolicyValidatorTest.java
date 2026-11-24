package dev.minicard.processor.identity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

  private PasswordPolicyValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PasswordPolicyValidator();
  }

  @Test
  void validPasswordPasses() {
    assertThatCode(() -> validator.validate("CorrectHorseBatteryStaple123!", "user@example.com"))
        .doesNotThrowAnyException();
  }

  @Test
  void passwordShorterThan12CharsIsRejected() {
    assertThatThrownBy(() -> validator.validate("Short1!", "user@example.com"))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void passwordIdenticalToEmailIsRejected() {
    assertThatThrownBy(() -> validator.validate("admin@minicard.local", "admin@minicard.local"))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void commonPasswordIsRejected() {
    assertThatThrownBy(() -> validator.validate("password123456", "user@example.com"))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void passwordExceeding72Utf8BytesIsRejected() {
    // 73 multibyte unicode characters exceeding 72 bytes
    String longPassword = "A".repeat(73);
    assertThatThrownBy(() -> validator.validate(longPassword, "user@example.com"))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }
}
