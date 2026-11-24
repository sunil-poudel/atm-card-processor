package dev.minicard.processor.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class ProdSecretsValidator {

  private final AppProperties appProperties;

  public ProdSecretsValidator(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @PostConstruct
  public void validateProdSecrets() {
    if (appProperties.bootstrapAdmin().password() == null
        || appProperties.bootstrapAdmin().password().isBlank()) {
      throw new IllegalStateException("Production profile requires app.bootstrap-admin.password");
    }
    if (appProperties.security().jwt().secret() == null
        || appProperties.security().jwt().secret().isBlank()) {
      throw new IllegalStateException("Production profile requires app.security.jwt.secret");
    }
    String currentKeyId = appProperties.crypto().pan().currentKeyId();
    String panKey = appProperties.crypto().pan().keys().get(currentKeyId);
    if (panKey == null || panKey.isBlank()) {
      throw new IllegalStateException(
          "Production profile requires app.crypto.pan.keys." + currentKeyId);
    }
    if (appProperties.crypto().pan().hashKey() == null
        || appProperties.crypto().pan().hashKey().isBlank()) {
      throw new IllegalStateException("Production profile requires app.crypto.pan.hash-key");
    }
  }
}
