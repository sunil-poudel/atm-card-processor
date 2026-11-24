package dev.minicard.processor.identity.service;

import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import dev.minicard.processor.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final AppProperties appProperties;
  private final Clock clock;

  public BootstrapAdminRunner(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator passwordPolicyValidator,
      AppProperties appProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.appProperties = appProperties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.count() > 0) {
      return;
    }

    AppProperties.BootstrapAdminProperties adminConfig = appProperties.bootstrapAdmin();
    if (adminConfig == null
        || adminConfig.email() == null
        || adminConfig.email().isBlank()
        || adminConfig.password() == null
        || adminConfig.password().isBlank()) {
      log.info("No bootstrap administrator configured; skipping bootstrap.");
      return;
    }

    String email = adminConfig.email().trim();
    String password = adminConfig.password();

    passwordPolicyValidator.validate(password, email);

    Instant now = clock.instant();
    String passwordHash = passwordEncoder.encode(password);
    AppUser admin =
        new AppUser(null, email, "System Administrator", passwordHash, UserRole.ADMIN, true, now);

    userRepository.save(admin);
    log.info("Bootstrap administrator account created successfully: {}", admin.getEmail());
  }
}
