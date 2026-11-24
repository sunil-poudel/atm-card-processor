package dev.minicard.processor.transactions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationFlowIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;

  private String adminToken;
  private String apiKey;
  private String cardId;

  @BeforeEach
  void setUp() throws Exception {
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

    // 2. Create API client for merchant
    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"test-pos-merchant-" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    apiKey = JsonPath.read(clientResult.getResponse().getContentAsString(), "$.apiKey");

    // 3. Issue and activate card with initial balance 100,000 NPR (1000.00 NPR)
    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Hari Bahadur Thapa",
                          "currency": "NPR",
                          "validityYears": 3,
                          "perTransactionLimitMinor": 50000,
                          "dailyLimitMinor": 120000,
                          "initialBalanceMinor": 100000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    cardId = JsonPath.read(issueResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Full authorization lifecycle: approve, replay, reuse conflict, decline replay, read API")
  void authorizationLifecycle() throws Exception {
    String idempKey = "test-auth-key-" + UUID.randomUUID();
    String authPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 20000,
          "currency": "NPR",
          "merchantName": "Bhatbhateni Supermarket",
          "mcc": "5411",
          "reference": "POS-REG-01"
        }
        """
            .formatted(cardId);

    // 1. Initial authorization via API Key -> APPROVED 00
    MvcResult authResult =
        mockMvc
            .perform(
                post("/api/v1/authorize")
                    .header("X-Api-Key", apiKey)
                    .header("Idempotency-Key", idempKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authPayload))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.responseCode").value("00"))
            .andExpect(jsonPath("$.responseMessage").value("Approved"))
            .andExpect(jsonPath("$.transactionId").isNotEmpty())
            .andExpect(jsonPath("$.authorizedAt").isNotEmpty())
            // Response must not leak internal fields:
            .andExpect(jsonPath("$.balanceMinor").doesNotExist())
            .andExpect(jsonPath("$.declineReason").doesNotExist())
            .andExpect(jsonPath("$.riskScore").doesNotExist())
            .andExpect(jsonPath("$.flagged").doesNotExist())
            .andReturn();

    String txId = JsonPath.read(authResult.getResponse().getContentAsString(), "$.transactionId");

    // Card balance should now be 80,000 NPR
    mockMvc
        .perform(
            get("/api/v1/cards/" + cardId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanceMinor").value(80000));

    // 2. Replay with identical payload -> 200 OK, Idempotent-Replayed: true, identical response
    // body
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", idempKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(authPayload))
        .andExpect(status().isOk())
        .andExpect(header().string("Idempotent-Replayed", "true"))
        .andExpect(jsonPath("$.transactionId").value(txId))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.responseCode").value("00"));

    // Balance still 80,000 (no double debit)
    mockMvc
        .perform(
            get("/api/v1/cards/" + cardId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanceMinor").value(80000));

    // 3. Reuse idempotency key with different amount -> 422 IDEMPOTENCY_KEY_REUSED
    String modifiedPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 30000,
          "currency": "NPR",
          "merchantName": "Bhatbhateni Supermarket",
          "mcc": "5411",
          "reference": "POS-REG-01"
        }
        """
            .formatted(cardId);

    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", idempKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(modifiedPayload))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

    // 4. Decline authorization (per-transaction limit 50,000, request 60,000) -> DECLINED 61
    String declineKey = "test-decline-key-" + UUID.randomUUID();
    String declinePayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 60000,
          "currency": "NPR",
          "merchantName": "Luxury Store",
          "mcc": "5311"
        }
        """
            .formatted(cardId);

    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", declineKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(declinePayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.responseCode").value("61"))
        .andExpect(jsonPath("$.responseMessage").value("Exceeds amount limit"));

    // Replay of decline returns stored decline
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", declineKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(declinePayload))
        .andExpect(status().isOk())
        .andExpect(header().string("Idempotent-Replayed", "true"))
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.responseCode").value("61"));

    // 5. Admin user JWT authorization works
    String adminAuthKey = "admin-auth-key-" + UUID.randomUUID();
    String adminPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 1000,
          "currency": "NPR",
          "merchantName": "Cafe",
          "mcc": "5812"
        }
        """
            .formatted(cardId);

    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .header("Idempotency-Key", adminAuthKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // 6. Read transactions API (GET /api/v1/transactions and GET /api/v1/transactions/{id})
    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("cardId", cardId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", Matchers.hasSize(Matchers.greaterThanOrEqualTo(3))))
        .andExpect(jsonPath("$.content[0].cardId").value(cardId))
        .andExpect(jsonPath("$.content[0].source").isNotEmpty());

    mockMvc
        .perform(
            get("/api/v1/transactions/" + txId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(txId))
        .andExpect(jsonPath("$.cardId").value(cardId))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.balanceAfterMinor").value(80000))
        .andExpect(jsonPath("$.processingMillis").isNumber())
        .andExpect(jsonPath("$.idempotencyKey").value(idempKey));

    // GET /api/v1/cards/{id}/transactions
    mockMvc
        .perform(
            get("/api/v1/cards/" + cardId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", Matchers.hasSize(Matchers.greaterThanOrEqualTo(3))));
  }

  @Test
  @DisplayName("Validation & Security checks for authorization endpoint")
  void validationAndSecurity() throws Exception {
    // Missing Idempotency-Key -> 400
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cardId\":\""
                        + cardId
                        + "\",\"amountMinor\":1000,\"currency\":\"NPR\",\"merchantName\":\"Shop\",\"mcc\":\"5411\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

    // Too short Idempotency-Key (< 8 chars) -> 400
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", "short")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cardId\":\""
                        + cardId
                        + "\",\"amountMinor\":1000,\"currency\":\"NPR\",\"merchantName\":\"Shop\",\"mcc\":\"5411\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

    // Unsupported currency EUR -> 422 UNSUPPORTED_CURRENCY
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", "valid-idemp-key-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cardId\":\""
                        + cardId
                        + "\",\"amountMinor\":1000,\"currency\":\"EUR\",\"merchantName\":\"Shop\",\"mcc\":\"5411\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));

    // Unknown cardId -> 404 CARD_NOT_FOUND
    UUID unknownCardId = UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("X-Api-Key", apiKey)
                .header("Idempotency-Key", "valid-idemp-key-99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cardId\":\""
                        + unknownCardId
                        + "\",\"amountMinor\":1000,\"currency\":\"NPR\",\"merchantName\":\"Shop\",\"mcc\":\"5411\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));

    // Unauthenticated -> 401
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header("Idempotency-Key", "valid-idemp-key-unauth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cardId\":\""
                        + cardId
                        + "\",\"amountMinor\":1000,\"currency\":\"NPR\",\"merchantName\":\"Shop\",\"mcc\":\"5411\"}"))
        .andExpect(status().isUnauthorized());
  }
}
