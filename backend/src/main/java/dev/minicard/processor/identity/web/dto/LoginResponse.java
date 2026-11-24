package dev.minicard.processor.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing JWT bearer access token")
public record LoginResponse(
    String accessToken, String tokenType, int expiresInSeconds, UserSummaryResponse user) {

  public LoginResponse(String accessToken, int expiresInSeconds, UserSummaryResponse user) {
    this(accessToken, "Bearer", expiresInSeconds, user);
  }
}
