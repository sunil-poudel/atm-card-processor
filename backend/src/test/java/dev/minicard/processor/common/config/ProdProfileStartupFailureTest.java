package dev.minicard.processor.common.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.minicard.processor.CardProcessorApplication;
import dev.minicard.processor.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class ProdProfileStartupFailureTest extends PostgresContainerSupport {

  @Test
  @DisplayName("Production profile fails startup when secrets are missing")
  void testProdFailsWithoutSecrets() {
    String dbUrl =
        getPostgresJdbcUrl() != null
            ? getPostgresJdbcUrl()
            : (System.getProperty("spring.datasource.url") != null
                ? System.getProperty("spring.datasource.url")
                : "jdbc:postgresql://localhost:5433/minicard");
    String dbUser =
        getPostgresUsername() != null
            ? getPostgresUsername()
            : (System.getProperty("spring.datasource.username") != null
                ? System.getProperty("spring.datasource.username")
                : "minicard");
    String dbPass =
        getPostgresPassword() != null
            ? getPostgresPassword()
            : (System.getProperty("spring.datasource.password") != null
                ? System.getProperty("spring.datasource.password")
                : "minicard");

    assertThatThrownBy(
            () ->
                SpringApplication.run(
                    CardProcessorApplication.class,
                    "--spring.profiles.active=prod",
                    "--spring.datasource.url=" + dbUrl,
                    "--spring.datasource.username=" + dbUser,
                    "--spring.datasource.password=" + dbPass,
                    "--spring.flyway.enabled=false",
                    "--spring.jpa.hibernate.ddl-auto=none"))
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }
}
