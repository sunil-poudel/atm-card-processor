package dev.minicard.processor.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotNull ZoneId businessZone,
    @NotNull @Valid BootstrapAdminProperties bootstrapAdmin,
    @NotNull @Valid SecurityProperties security,
    @NotNull @Valid CryptoProperties crypto,
    @NotNull @Valid CardsProperties cards,
    @NotNull @Valid FraudProperties fraud,
    @NotNull @Valid DisputesProperties disputes) {

  public AppProperties {
    if (businessZone == null) {
      businessZone = ZoneId.of("Asia/Kathmandu");
    }
  }

  public record BootstrapAdminProperties(
      @NotBlank @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String email,
      String password) {}

  public record SecurityProperties(
      @NotNull @Valid JwtProperties jwt, boolean cookieSecure, List<String> corsAllowedOrigins) {
    public SecurityProperties {
      if (corsAllowedOrigins == null) {
        corsAllowedOrigins = List.of();
      }
    }
  }

  public record JwtProperties(
      String secret,
      @Min(1) @Max(1440) int accessTokenExpirationMinutes,
      @Min(1) @Max(365) int refreshTokenExpirationDays) {}

  public record CryptoProperties(@NotNull @Valid PanCryptoProperties pan) {}

  public record PanCryptoProperties(
      @NotBlank String currentKeyId, @NotNull Map<String, String> keys, String hashKey) {}

  public record CardsProperties(
      @NotBlank @Pattern(regexp = "^\\d{6}$") String bin,
      boolean exposePanOnIssue,
      @Min(1) long defaultPerTransactionLimitMinor,
      @Min(1) long defaultDailyLimitMinor) {

    @ConstructorBinding
    public CardsProperties(
        String bin,
        boolean exposePanOnIssue,
        long defaultPerTransactionLimitMinor,
        long defaultDailyLimitMinor) {
      this.bin = (bin == null || bin.isBlank()) ? "990000" : bin;
      this.exposePanOnIssue = exposePanOnIssue;
      this.defaultPerTransactionLimitMinor =
          defaultPerTransactionLimitMinor <= 0 ? 5_000_000L : defaultPerTransactionLimitMinor;
      this.defaultDailyLimitMinor =
          defaultDailyLimitMinor <= 0 ? 20_000_000L : defaultDailyLimitMinor;
    }

    public CardsProperties(String bin, boolean exposePanOnIssue) {
      this(bin, exposePanOnIssue, 5_000_000L, 20_000_000L);
    }
  }

  public record FraudProperties(
      @Min(0) @Max(100) int flagThreshold, @Min(0) @Max(100) int declineThreshold) {}

  public record DisputesProperties(
      @Min(1) @Max(90) int evidenceDays,
      @Min(1) @Max(365) int maxAgeDays,
      @NotBlank String deadlineJobCron) {}
}
