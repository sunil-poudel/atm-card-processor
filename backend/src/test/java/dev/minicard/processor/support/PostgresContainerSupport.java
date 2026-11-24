package dev.minicard.processor.support;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgresContainerSupport {
  private static final Logger log = LoggerFactory.getLogger(PostgresContainerSupport.class);
  private static PostgreSQLContainer container;
  private static String jdbcUrl;
  private static String username;
  private static String password;

  static {
    initPostgres();
  }

  private static synchronized void initPostgres() {
    if (jdbcUrl != null) {
      return;
    }
    boolean dockerAvailable = false;
    try {
      dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      log.info(
          "Docker not available ({}), utilizing local PostgreSQL test cluster.", t.getMessage());
    }

    if (dockerAvailable) {
      log.info("Starting Testcontainers PostgreSQL 17 container...");
      container =
          new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
              .withDatabaseName("minicard")
              .withUsername("minicard")
              .withPassword("testpass");
      container.start();
      jdbcUrl = container.getJdbcUrl();
      username = container.getUsername();
      password = container.getPassword();
    } else {
      log.info(
          "Docker is not available. Falling back to local PostgreSQL test cluster (ADR 0016)...");
      ensureLocalPostgresRunning();
    }
  }

  private static void ensureLocalPostgresRunning() {
    int port = 5433;
    username = "minicard";
    password = "";
    jdbcUrl = "jdbc:postgresql://localhost:" + port + "/minicard";

    if (!isPortOpen("localhost", port)) {
      try {
        File dataDir = new File("/tmp/minicard-test-pg");
        if (!dataDir.exists()) {
          new ProcessBuilder(
                  "/usr/lib/postgresql/16/bin/initdb",
                  "-D",
                  dataDir.getAbsolutePath(),
                  "-U",
                  "minicard",
                  "-A",
                  "trust",
                  "--locale=en_US.UTF-8")
              .inheritIO()
              .start()
              .waitFor();
        }
        new ProcessBuilder(
                "/usr/lib/postgresql/16/bin/pg_ctl",
                "-D",
                dataDir.getAbsolutePath(),
                "-o",
                "-p " + port + " -k /tmp",
                "-l",
                "/tmp/minicard-pg.log",
                "start")
            .inheritIO()
            .start()
            .waitFor();
        new ProcessBuilder(
                "/usr/lib/postgresql/16/bin/createdb",
                "-h",
                "localhost",
                "-p",
                String.valueOf(port),
                "-U",
                "minicard",
                "minicard")
            .start()
            .waitFor();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to start local fallback PostgreSQL instance", e);
      }
    }
  }

  private static boolean isPortOpen(String host, int port) {
    try {
      new Socket(host, port).close();
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> password);
  }

  public static String getPostgresJdbcUrl() {
    return jdbcUrl;
  }

  public static String getPostgresUsername() {
    return username;
  }

  public static String getPostgresPassword() {
    return password;
  }
}
