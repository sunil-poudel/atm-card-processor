package dev.minicard.processor.cards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.crypto.Luhn;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.ledger.domain.LedgerEntry;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardManagementIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private CardRepository cardRepository;
  @Autowired private LedgerEntryRepository ledgerEntryRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String adminToken;
  private String analystToken;

  @BeforeEach
  void setUpAuthTokens() throws Exception {
    // 1. Admin login
    MvcResult adminLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"admin@minicard.local\",\"password\":\"TestAdminPassword123!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");

    // 2. Analyst user creation via API & login for read-role tests
    String analystEmail = "analyst.cards@minicard.local";
    if (userRepository.findByEmailIgnoreCase(analystEmail).isEmpty()) {
      mockMvc
          .perform(
              post("/api/v1/users")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "email": "analyst.cards@minicard.local",
                        "displayName": "Cards Analyst",
                        "role": "ANALYST",
                        "password": "Password12345!"
                      }
                      """))
          .andExpect(status().isCreated());
    }

    MvcResult analystLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"" + analystEmail + "\",\"password\":\"Password12345!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    analystToken = JsonPath.read(analystLogin.getResponse().getContentAsString(), "$.accessToken");
  }

  @Test
  @DisplayName(
      "Full card lifecycle: issue, activate, limits, blocked-mccs, top-up, block, unblock, close check")
  void fullCardLifecycleAndSecurityAssertions() throws Exception {
    // 1. Issue card with initial balance
    String issueRequest =
        """
        {
          "holderName": "Sita Kumari Sharma",
          "currency": "NPR",
          "validityYears": 3,
          "perTransactionLimitMinor": 5000000,
          "dailyLimitMinor": 20000000,
          "initialBalanceMinor": 50000,
          "blockedMccs": ["7995", "6011"]
        }
        """;

    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(issueRequest))
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.balanceMinor").value(50000))
            .andExpect(jsonPath("$.currency").value("NPR"))
            .andExpect(jsonPath("$.holderName").value("Sita Kumari Sharma"))
            .andExpect(jsonPath("$.bin").value("990000"))
            .andExpect(jsonPath("$.last4").isNotEmpty())
            .andExpect(
                jsonPath("$.maskedPan").value(org.hamcrest.Matchers.startsWith("990000******")))
            .andExpect(jsonPath("$.pan").isNotEmpty()) // exposed only on issue when configured
            .andReturn();

    String issueJson = issueResult.getResponse().getContentAsString();
    String cardId = JsonPath.read(issueJson, "$.id");
    String issuedPan = JsonPath.read(issueJson, "$.pan");
    int cardVersion = JsonPath.read(issueJson, "$.version");

    assertThat(Luhn.isValid(issuedPan)).isTrue();
    assertThat(issuedPan).hasSize(16);

    // 2. Read card detail as ANALYST: PAN is NOT returned
    MvcResult detailResult =
        mockMvc
            .perform(
                get("/api/v1/cards/" + cardId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(cardId))
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.balanceMinor").value(50000))
            .andExpect(jsonPath("$.pan").doesNotExist())
            .andReturn();

    assertThat(detailResult.getResponse().getContentAsString()).doesNotContain(issuedPan);

    // 3. List cards with search filter
    mockMvc
        .perform(
            get("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                .param("q", "Kumari")
                .param("status", "INACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].id").value(cardId))
        .andExpect(jsonPath("$.content[0].pan").doesNotExist());

    // 4. Activate card
    MvcResult activateResult =
        mockMvc
            .perform(
                post("/api/v1/cards/" + cardId + "/activate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.activatedAt").isNotEmpty())
            .andReturn();
    int activeVersion =
        JsonPath.read(activateResult.getResponse().getContentAsString(), "$.version");

    // 5. Update limits with active version
    mockMvc
        .perform(
            put("/api/v1/cards/" + cardId + "/limits")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"perTransactionLimitMinor\":6000000,\"dailyLimitMinor\":25000000,\"version\":"
                        + activeVersion
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.perTransactionLimitMinor").value(6000000))
        .andExpect(jsonPath("$.dailyLimitMinor").value(25000000));

    // Stale version fails with 409 OPTIMISTIC_LOCK_FAILURE
    mockMvc
        .perform(
            put("/api/v1/cards/" + cardId + "/limits")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"perTransactionLimitMinor\":7000000,\"dailyLimitMinor\":30000000,\"version\":"
                        + cardVersion // stale version
                        + "}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_FAILURE"));

    // 6. Top-up card
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/topups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":25000,\"description\":\"Counter reload\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("TOPUP"))
        .andExpect(jsonPath("$.amountMinor").value(25000))
        .andExpect(jsonPath("$.balanceAfterMinor").value(75000));

    // 7. Check ledger entries
    mockMvc
        .perform(
            get("/api/v1/cards/" + cardId + "/ledger")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].amountMinor").value(25000))
        .andExpect(jsonPath("$.content[1].amountMinor").value(50000));

    // 8. Block card
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Card reported lost by holder\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.blockReason").value("Card reported lost by holder"));

    // Top-up while blocked is rejected with 409
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/topups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":10000}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

    // 9. Unblock card -> restores ACTIVE
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/unblock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.blockReason").isEmpty());

    // 10. Close card with positive balance (75,000) is rejected with 409 INVALID_STATE_TRANSITION
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  @DisplayName(
      "Property test: card.balanceMinor always equals the sum of ledger entries over random top-ups")
  void propertyTestBalanceEqualsLedgerSum() throws Exception {
    // 1. Issue a new card with 0 balance
    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Property Test Card",
                          "currency": "NPR",
                          "initialBalanceMinor": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String cardId = JsonPath.read(issueResult.getResponse().getContentAsString(), "$.id");
    UUID cardUuid = UUID.fromString(cardId);

    // 2. Perform 10 random top-ups
    Random random = new Random(42);
    long expectedBalance = 0L;

    for (int i = 0; i < 10; i++) {
      long amount = 1000L + random.nextInt(50000);
      expectedBalance += amount;

      mockMvc
          .perform(
              post("/api/v1/cards/" + cardId + "/topups")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"amountMinor\":" + amount + ",\"description\":\"Batch " + i + "\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.balanceAfterMinor").value(expectedBalance));
    }

    // 3. Verify in database: card.balanceMinor matches expected and sum of ledger rows
    Card card = cardRepository.findById(cardUuid).orElseThrow();
    List<LedgerEntry> entries = ledgerEntryRepository.findByCardIdOrderByCreatedAtAsc(cardUuid);

    assertThat(card.getBalanceMinor()).isEqualTo(expectedBalance);
    long sumOfLedger = entries.stream().mapToLong(LedgerEntry::getAmountMinor).sum();
    assertThat(card.getBalanceMinor()).isEqualTo(sumOfLedger);

    // Verify running balance invariant on every row
    long running = 0L;
    for (LedgerEntry entry : entries) {
      running += entry.getAmountMinor();
      assertThat(entry.getBalanceAfterMinor()).isEqualTo(running);
    }
  }
}
