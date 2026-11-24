package dev.minicard.processor.demo;

import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.cards.service.CardService;
import dev.minicard.processor.cards.web.dto.BlockCardRequest;
import dev.minicard.processor.cards.web.dto.CardResponse;
import dev.minicard.processor.cards.web.dto.IssueCardRequest;
import dev.minicard.processor.disputes.domain.DisputeReason;
import dev.minicard.processor.disputes.service.DisputeService;
import dev.minicard.processor.disputes.web.dto.OpenDisputeRequest;
import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.identity.service.ApiClientService;
import dev.minicard.processor.identity.service.UserService;
import dev.minicard.processor.security.CallerIdentity;
import dev.minicard.processor.transactions.service.AuthorizationService;
import dev.minicard.processor.transactions.service.AuthorizeExecutionResult;
import dev.minicard.processor.transactions.web.dto.AuthorizeRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final CardRepository cardRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final ApiClientRepository apiClientRepository;
  private final ApiClientService apiClientService;
  private final CardService cardService;
  private final AuthorizationService authorizationService;
  private final DisputeService disputeService;

  public DemoDataSeeder(
      CardRepository cardRepository,
      UserRepository userRepository,
      UserService userService,
      ApiClientRepository apiClientRepository,
      ApiClientService apiClientService,
      CardService cardService,
      AuthorizationService authorizationService,
      DisputeService disputeService) {
    this.cardRepository = cardRepository;
    this.userRepository = userRepository;
    this.userService = userService;
    this.apiClientRepository = apiClientRepository;
    this.apiClientService = apiClientService;
    this.cardService = cardService;
    this.authorizationService = authorizationService;
    this.disputeService = disputeService;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (cardRepository.count() > 0) {
      log.info("DemoDataSeeder: Cards already exist. Skipping demo data seeding.");
      return;
    }

    log.info("DemoDataSeeder: Seeding demo data...");

    Optional<AppUser> adminOpt = userRepository.findByEmailIgnoreCase("admin@minicard.local");
    UUID adminId = adminOpt.map(AppUser::getId).orElseGet(UUID::randomUUID);
    String adminEmail = "admin@minicard.local";

    // 1. Staff users
    seedUserIfMissing(
        "analyst@minicard.local",
        "Demo Analyst",
        UserRole.ANALYST,
        "AnalystPassword123!",
        adminId,
        adminEmail);
    seedUserIfMissing(
        "dispute@minicard.local",
        "Demo Dispute Officer",
        UserRole.DISPUTE_OFFICER,
        "DisputePassword123!",
        adminId,
        adminEmail);

    // 2. POS API client
    UUID posClientId =
        apiClientRepository
            .findByNameIgnoreCase("Demo POS Terminal")
            .map(ApiClient::getId)
            .orElseGet(
                () ->
                    apiClientService
                        .createClient("Demo POS Terminal", adminId, adminEmail, "127.0.0.1")
                        .client()
                        .getId());
    CallerIdentity posCaller = CallerIdentity.forApiClient(posClientId, "Demo POS Terminal");

    // 3. Issue and activate demo cards
    CardResponse activeCard =
        cardService.issueCard(
            new IssueCardRequest(
                "Pramod Sharma", "NPR", 3, 5000000L, 20000000L, 10000000L, Set.of()),
            adminId,
            adminEmail,
            "127.0.0.1");
    cardService.activateCard(activeCard.id(), adminId, adminEmail, "127.0.0.1");

    cardService.issueCard(
        new IssueCardRequest("Sita Thapa", "NPR", 3, 2000000L, 10000000L, 2000000L, Set.of()),
        adminId,
        adminEmail,
        "127.0.0.1");

    CardResponse blockedCard =
        cardService.issueCard(
            new IssueCardRequest("Hari Prasad", "USD", 2, 50000L, 200000L, 100000L, Set.of()),
            adminId,
            adminEmail,
            "127.0.0.1");
    cardService.activateCard(blockedCard.id(), adminId, adminEmail, "127.0.0.1");
    cardService.blockCard(
        blockedCard.id(),
        new BlockCardRequest("Lost card report"),
        adminId,
        adminEmail,
        "127.0.0.1");

    // 4. Sample transactions on active card
    AuthorizeExecutionResult tx1 =
        authorizationService.authorize(
            posCaller,
            "demo-tx-1-" + UUID.randomUUID(),
            new AuthorizeRequest(
                activeCard.id(), 150000L, "NPR", "Bhat-Bhateni Supermarket", "5411", "REF-001"));

    authorizationService.authorize(
        posCaller,
        "demo-tx-2-" + UUID.randomUUID(),
        new AuthorizeRequest(
            activeCard.id(), 350000L, "NPR", "Himalayan Java Coffee", "5812", "REF-002"));

    // 5. Open dispute on tx1
    disputeService.openDispute(
        new OpenDisputeRequest(
            tx1.response().transactionId(),
            DisputeReason.UNAUTHORIZED_TRANSACTION,
            "Customer does not recognize purchase"),
        adminId,
        adminEmail,
        dev.minicard.processor.audit.domain.ActorType.USER,
        "127.0.0.1");

    log.info("DemoDataSeeder: Completed demo data seeding successfully.");
  }

  private void seedUserIfMissing(
      String email,
      String displayName,
      UserRole role,
      String password,
      UUID adminId,
      String adminEmail) {
    if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
      userService.createUser(email, displayName, role, password, adminId, adminEmail, "127.0.0.1");
    }
  }
}
