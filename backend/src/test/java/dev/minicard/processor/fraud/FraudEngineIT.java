package dev.minicard.processor.fraud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.security.ApiKeyAuthenticationFilter;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.util.UUID;
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
class FraudEngineIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;

  private String adminToken;
  private String analystToken;
  private String posApiKey;

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

    // 2. Create analyst user via admin API and login
    String analystEmail = "analyst-" + UUID.randomUUID() + "@minicard.local";
    mockMvc
        .perform(
            post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "TestAnalystPassword123!",
                      "displayName": "Analyst User",
                      "role": "ANALYST"
                    }
                    """
                        .formatted(analystEmail)))
        .andExpect(status().isCreated());

    MvcResult analystLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "TestAnalystPassword123!"
                        }
                        """
                            .formatted(analystEmail)))
            .andExpect(status().isOk())
            .andReturn();
    analystToken = JsonPath.read(analystLogin.getResponse().getContentAsString(), "$.accessToken");

    // 3. Create POS API Client via REST API
    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Fraud Test POS " + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    posApiKey = JsonPath.read(clientResult.getResponse().getContentAsString(), "$.apiKey");
  }

  @Test
  @DisplayName("Fraud rules CRUD, permissions, and optimistic locking")
  void testFraudRulesCrud() throws Exception {
    // 1. Analyst can list rules
    mockMvc
        .perform(
            get("/api/v1/fraud-rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

    // 2. Analyst cannot create rules (403 Forbidden)
    mockMvc
        .perform(
            post("/api/v1/fraud-rules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Unauthorized Rule",
                      "type": "MAX_AMOUNT",
                      "thresholdValue": 1000,
                      "scorePoints": 50,
                      "action": "FLAG"
                    }
                    """))
        .andExpect(status().isForbidden());

    // 3. Admin creates custom rule
    String ruleName = "Custom High Amount " + UUID.randomUUID();
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/fraud-rules")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s",
                          "description": "Custom flag rule",
                          "type": "MAX_AMOUNT",
                          "thresholdValue": 8000000,
                          "scorePoints": 30,
                          "action": "FLAG"
                        }
                        """
                            .formatted(ruleName)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(ruleName))
            .andExpect(jsonPath("$.type").value("MAX_AMOUNT"))
            .andExpect(jsonPath("$.thresholdValue").value(8000000))
            .andExpect(jsonPath("$.scorePoints").value(30))
            .andExpect(jsonPath("$.action").value("FLAG"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andReturn();

    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
    int version = JsonPath.read(createResult.getResponse().getContentAsString(), "$.version");

    // 4. Update with wrong version triggers 409 Conflict
    mockMvc
        .perform(
            put("/api/v1/fraud-rules/" + ruleId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "%s",
                      "description": "Updated description",
                      "enabled": true,
                      "thresholdValue": 9000000,
                      "scorePoints": 35,
                      "action": "FLAG",
                      "version": 999
                    }
                    """
                        .formatted(ruleName)))
        .andExpect(status().isConflict());

    // 5. Update with correct version succeeds
    mockMvc
        .perform(
            put("/api/v1/fraud-rules/" + ruleId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "%s",
                      "description": "Updated description",
                      "enabled": true,
                      "thresholdValue": 9000000,
                      "scorePoints": 35,
                      "action": "FLAG",
                      "version": %d
                    }
                    """
                        .formatted(ruleName, version)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.thresholdValue").value(9000000))
        .andExpect(jsonPath("$.scorePoints").value(35));

    // 6. Disable rule
    mockMvc
        .perform(
            post("/api/v1/fraud-rules/" + ruleId + "/disable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    // 7. Enable rule
    mockMvc
        .perform(
            post("/api/v1/fraud-rules/" + ruleId + "/enable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  @DisplayName("Worked Example 12: High value payment flagged (score 40, APPROVED 00)")
  void testWorkedExample12_highValuePaymentFlagged() throws Exception {
    String cardId = createActiveCard(10_000_000L, 10_000_000L, 20_000_000L);

    // 6,000,000 NPR exceeds 5,000,000 threshold of 'High value payment' rule
    String authPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 6000000,
          "currency": "NPR",
          "merchantName": "Kathmandu Gold & Diamonds",
          "mcc": "5411"
        }
        """
            .formatted(cardId);

    MvcResult authResult =
        mockMvc
            .perform(
                post("/api/v1/authorize")
                    .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                    .header("Idempotency-Key", "idemp-ex12-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.responseCode").value("00"))
            .andReturn();

    String txnId = JsonPath.read(authResult.getResponse().getContentAsString(), "$.transactionId");

    // Verify transaction details via GET /api/v1/transactions/{id}
    mockMvc
        .perform(
            get("/api/v1/transactions/" + txnId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.flagged").value(true))
        .andExpect(jsonPath("$.riskScore").value(40))
        .andExpect(jsonPath("$.ruleHits.length()").value(1))
        .andExpect(jsonPath("$.ruleHits[0].ruleName").value("High value payment"))
        .andExpect(jsonPath("$.ruleHits[0].ruleType").value("MAX_AMOUNT"))
        .andExpect(jsonPath("$.ruleHits[0].points").value(40))
        .andExpect(jsonPath("$.ruleHits[0].action").value("FLAG"))
        .andExpect(jsonPath("$.ruleHits[0].detail").value("amount 6000000 exceeds 5000000"));
  }

  @Test
  @DisplayName("Worked Example 14: Blocked MCC declined with 59 SUSPECTED_FRAUD and hit details")
  void testWorkedExample14_blockedMccDeclined() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);

    String authPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 1000,
          "currency": "NPR",
          "merchantName": "Online Casino",
          "mcc": "7995"
        }
        """
            .formatted(cardId);

    MvcResult authResult =
        mockMvc
            .perform(
                post("/api/v1/authorize")
                    .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                    .header("Idempotency-Key", "idemp-ex14-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DECLINED"))
            .andExpect(jsonPath("$.responseCode").value("59"))
            .andReturn();

    String txnId = JsonPath.read(authResult.getResponse().getContentAsString(), "$.transactionId");

    mockMvc
        .perform(
            get("/api/v1/transactions/" + txnId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.responseCode").value("59"))
        .andExpect(jsonPath("$.declineReason").value("SUSPECTED_FRAUD"))
        .andExpect(jsonPath("$.riskScore").value(100))
        .andExpect(jsonPath("$.ruleHits.length()").value(1))
        .andExpect(jsonPath("$.ruleHits[0].ruleName").value("Restricted merchant categories"))
        .andExpect(jsonPath("$.ruleHits[0].ruleType").value("BLOCKED_MCC"))
        .andExpect(jsonPath("$.ruleHits[0].points").value(100))
        .andExpect(jsonPath("$.ruleHits[0].action").value("DECLINE"))
        .andExpect(jsonPath("$.ruleHits[0].detail").value("mcc 7995 is restricted"));
  }

  @Test
  @DisplayName("Worked Example 13: 6th attempt in 60s triggers Rapid-fire attempts decline")
  void testWorkedExample13_velocityRapidFireDeclined() throws Exception {
    String cardId = createActiveCard(1_000_000L, 100_000L, 500_000L);

    // Make 5 prior attempts
    for (int i = 1; i <= 5; i++) {
      String payload =
          """
          {
            "cardId": "%s",
            "amountMinor": 100,
            "currency": "NPR",
            "merchantName": "Cafe Store",
            "mcc": "5812"
          }
          """
              .formatted(cardId);

      mockMvc
          .perform(
              post("/api/v1/authorize")
                  .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                  .header("Idempotency-Key", "idemp-velocity-" + i + "-" + UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // 6th attempt within 60s has 5 prior attempts on card -> triggers 'Rapid-fire attempts'
    // (threshold 5)
    String sixthPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": 100,
          "currency": "NPR",
          "merchantName": "Cafe Store",
          "mcc": "5812"
        }
        """
            .formatted(cardId);

    MvcResult sixthResult =
        mockMvc
            .perform(
                post("/api/v1/authorize")
                    .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                    .header("Idempotency-Key", "idemp-velocity-6-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(sixthPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DECLINED"))
            .andExpect(jsonPath("$.responseCode").value("59"))
            .andReturn();

    String txnId = JsonPath.read(sixthResult.getResponse().getContentAsString(), "$.transactionId");

    mockMvc
        .perform(
            get("/api/v1/transactions/" + txnId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.responseCode").value("59"))
        .andExpect(jsonPath("$.declineReason").value("SUSPECTED_FRAUD"))
        .andExpect(jsonPath("$.riskScore").value(60))
        .andExpect(jsonPath("$.ruleHits.length()").value(1))
        .andExpect(jsonPath("$.ruleHits[0].ruleName").value("Rapid-fire attempts"))
        .andExpect(jsonPath("$.ruleHits[0].ruleType").value("VELOCITY"))
        .andExpect(jsonPath("$.ruleHits[0].points").value(60))
        .andExpect(jsonPath("$.ruleHits[0].action").value("DECLINE"))
        .andExpect(jsonPath("$.ruleHits[0].detail").value("5 prior attempts in 60s (threshold 5)"));
  }

  @Test
  @DisplayName(
      "Disabling a fraud rule immediately allows transactions that would have been declined")
  void testDisableRuleTakesEffectImmediately() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);

    // Get ID of 'Restricted merchant categories' rule (seeded with fixed ID
    // 00000000-0000-7000-8000-000000000003)
    UUID ruleId = UUID.fromString("00000000-0000-7000-8000-000000000003");

    // Disable the rule
    mockMvc
        .perform(
            post("/api/v1/fraud-rules/" + ruleId + "/disable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    try {
      // With rule disabled, MCC 7995 is now approved
      String authPayload =
          """
          {
            "cardId": "%s",
            "amountMinor": 1000,
            "currency": "NPR",
            "merchantName": "Online Casino",
            "mcc": "7995"
          }
          """
              .formatted(cardId);

      mockMvc
          .perform(
              post("/api/v1/authorize")
                  .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                  .header("Idempotency-Key", "idemp-disabled-rule-" + UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(authPayload))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("APPROVED"))
          .andExpect(jsonPath("$.responseCode").value("00"));
    } finally {
      // Re-enable rule to preserve test state
      mockMvc
          .perform(
              post("/api/v1/fraud-rules/" + ruleId + "/enable")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.enabled").value(true));
    }
  }

  private String createActiveCard(long balance, long perTxnLimit, long dailyLimit)
      throws Exception {
    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Test Cardholder",
                          "currency": "NPR",
                          "validityYears": 3,
                          "perTransactionLimitMinor": %d,
                          "dailyLimitMinor": %d,
                          "initialBalanceMinor": %d
                        }
                        """
                            .formatted(perTxnLimit, dailyLimit, balance)))
            .andExpect(status().isCreated())
            .andReturn();
    String cardId = JsonPath.read(issueResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());

    return cardId;
  }
}
