package dev.minicard.processor.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CardRepository cardRepository;

  private String adminToken;
  private String analystToken;
  private String disputeOfficerToken;
  private UUID testCardId;
  private UUID testApiClientId;

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

    // 2. Analyst user
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
                      "displayName": "Report Analyst",
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

    // 3. Dispute Officer user
    String officerEmail = "officer-" + UUID.randomUUID() + "@minicard.local";
    mockMvc
        .perform(
            post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "TestOfficerPassword123!",
                      "displayName": "Report Officer",
                      "role": "DISPUTE_OFFICER"
                    }
                    """
                        .formatted(officerEmail)))
        .andExpect(status().isCreated());

    MvcResult officerLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "TestOfficerPassword123!"
                        }
                        """
                            .formatted(officerEmail)))
            .andExpect(status().isOk())
            .andReturn();
    disputeOfficerToken =
        JsonPath.read(officerLogin.getResponse().getContentAsString(), "$.accessToken");

    // 4. Create an active card
    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Report Tester",
                          "currency": "NPR",
                          "validityYears": 3,
                          "perTransactionLimitMinor": 500000,
                          "dailyLimitMinor": 1000000,
                          "initialBalanceMinor": 500000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    testCardId =
        UUID.fromString(JsonPath.read(issueResult.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            post("/api/v1/cards/" + testCardId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());

    // 5. Create an API client for transaction FK
    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Report POS " + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    testApiClientId =
        UUID.fromString(JsonPath.read(clientResult.getResponse().getContentAsString(), "$.id"));

    // Clean up transactions and disputes for clean report testing
    jdbcTemplate.update("DELETE FROM dispute_events");
    jdbcTemplate.update("DELETE FROM ledger_entries WHERE entry_type = 'DISPUTE_CREDIT'");
    jdbcTemplate.update("DELETE FROM disputes");
    jdbcTemplate.update("DELETE FROM transaction_rule_hits");
    jdbcTemplate.update("DELETE FROM ledger_entries WHERE entry_type = 'PURCHASE'");
    jdbcTemplate.update("DELETE FROM transactions");
  }

  private void insertTransaction(
      UUID txId,
      long amountMinor,
      String currency,
      String merchantName,
      String mcc,
      String status,
      String responseCode,
      String declineReason,
      boolean flagged,
      Instant createdAt) {
    String sql =
        """
        INSERT INTO transactions (
          id, card_id, api_client_id, initiated_by_user_id, idempotency_scope, idempotency_key,
          request_fingerprint, amount_minor, currency, merchant_name, mcc, reference,
          status, response_code, decline_reason, flagged, risk_score, balance_after_minor,
          processing_millis, created_at
        ) VALUES (
          ?, ?, ?, NULL, 'client', ?,
          '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
          ?, ?, ?, ?, NULL,
          ?, ?, ?, ?, 10, ?,
          15, ?
        )
        """;

    Long balanceAfter = "APPROVED".equals(status) ? 400000L : null;
    jdbcTemplate.update(
        sql,
        txId,
        testCardId,
        testApiClientId,
        "idemp-" + txId,
        amountMinor,
        currency,
        merchantName,
        mcc,
        status,
        responseCode,
        declineReason,
        flagged,
        balanceAfter,
        Timestamp.from(createdAt));
  }

  @Test
  @DisplayName(
      "Security: ADMIN and ANALYST allowed; DISPUTE_OFFICER forbidden; Anonymous unauthorized")
  void testReportsSecurityRoles() throws Exception {
    mockMvc.perform(get("/api/v1/reports/daily")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/v1/reports/daily")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/reports/daily")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/reports/daily").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Daily report business-zone day boundary: 18:14:59Z falls into Oct 20, 18:15:00Z falls into Oct 21")
  void testDailyReportBusinessZoneBoundary() throws Exception {
    // 2026-10-20 local midnight in Kathmandu is 2026-10-19T18:15:00Z
    // 2026-10-20 23:59:59 local is 2026-10-20T18:14:59Z
    // 2026-10-21 00:00:00 local is 2026-10-20T18:15:00Z

    UUID tx1 = UUID.randomUUID();
    UUID tx2 = UUID.randomUUID();

    insertTransaction(
        tx1,
        10000L,
        "NPR",
        "Late Store",
        "5411",
        "APPROVED",
        "00",
        null,
        false,
        Instant.parse("2026-10-20T18:14:59Z"));

    insertTransaction(
        tx2,
        20000L,
        "NPR",
        "Next Day Store",
        "5411",
        "APPROVED",
        "00",
        null,
        false,
        Instant.parse("2026-10-20T18:15:00Z"));

    // Check Oct 20 report
    mockMvc
        .perform(
            get("/api/v1/reports/daily")
                .param("date", "2026-10-20")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-20"))
        .andExpect(jsonPath("$.zone").value("Asia/Kathmandu"))
        .andExpect(jsonPath("$.attempts").value(1))
        .andExpect(jsonPath("$.approved").value(1))
        .andExpect(jsonPath("$.declined").value(0))
        .andExpect(jsonPath("$.approvalRatePercent").value(100.00))
        .andExpect(jsonPath("$.hourly[23].hour").value(23))
        .andExpect(jsonPath("$.hourly[23].attempts").value(1))
        .andExpect(jsonPath("$.hourly[23].approved").value(1))
        .andExpect(jsonPath("$.hourly[0].attempts").value(0));

    // Check Oct 21 report
    mockMvc
        .perform(
            get("/api/v1/reports/daily")
                .param("date", "2026-10-21")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-21"))
        .andExpect(jsonPath("$.attempts").value(1))
        .andExpect(jsonPath("$.approved").value(1))
        .andExpect(jsonPath("$.hourly[0].hour").value(0))
        .andExpect(jsonPath("$.hourly[0].attempts").value(1))
        .andExpect(jsonPath("$.hourly[0].approved").value(1));
  }

  @Test
  @DisplayName(
      "Daily report aggregates: multi-currency volumes, top MCCs, declines by code ordering")
  void testDailyReportAggregates() throws Exception {
    Instant time = Instant.parse("2026-10-20T08:00:00Z");

    // 1 approved NPR
    insertTransaction(
        UUID.randomUUID(),
        30000L,
        "NPR",
        "Supermarket",
        "5411",
        "APPROVED",
        "00",
        null,
        false,
        time);
    // 1 approved USD
    insertTransaction(
        UUID.randomUUID(), 2500L, "USD", "Hotel", "7011", "APPROVED", "00", null, true, time);
    // 2 declined with code 51
    insertTransaction(
        UUID.randomUUID(),
        50000L,
        "NPR",
        "Restaurant",
        "5812",
        "DECLINED",
        "51",
        "INSUFFICIENT_FUNDS",
        false,
        time);
    insertTransaction(
        UUID.randomUUID(),
        40000L,
        "NPR",
        "Restaurant",
        "5812",
        "DECLINED",
        "51",
        "INSUFFICIENT_FUNDS",
        false,
        time);
    // 1 declined with code 54
    insertTransaction(
        UUID.randomUUID(),
        10000L,
        "NPR",
        "Taxi",
        "4121",
        "DECLINED",
        "54",
        "CARD_EXPIRED",
        false,
        time);

    // Total attempts: 5, approved: 2, declined: 3, flagged: 1.
    // approvalRate: 2/5 * 100 = 40.00%
    mockMvc
        .perform(
            get("/api/v1/reports/daily")
                .param("date", "2026-10-20")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attempts").value(5))
        .andExpect(jsonPath("$.approved").value(2))
        .andExpect(jsonPath("$.declined").value(3))
        .andExpect(jsonPath("$.flagged").value(1))
        .andExpect(jsonPath("$.approvalRatePercent").value(40.00))
        .andExpect(jsonPath("$.approvedVolume.length()").value(2))
        .andExpect(jsonPath("$.approvedVolume[0].currency").value("NPR"))
        .andExpect(jsonPath("$.approvedVolume[0].amountMinor").value(30000))
        .andExpect(jsonPath("$.approvedVolume[1].currency").value("USD"))
        .andExpect(jsonPath("$.approvedVolume[1].amountMinor").value(2500))
        // Declines by code: 51 has count 2, 54 has count 1
        .andExpect(jsonPath("$.declinesByCode.length()").value(2))
        .andExpect(jsonPath("$.declinesByCode[0].responseCode").value("51"))
        .andExpect(jsonPath("$.declinesByCode[0].count").value(2))
        .andExpect(jsonPath("$.declinesByCode[0].message").value("Insufficient funds"))
        .andExpect(jsonPath("$.declinesByCode[1].responseCode").value("54"))
        .andExpect(jsonPath("$.declinesByCode[1].count").value(1))
        // Top MCCs: 5812 (2 attempts), 4121, 5411, 7011 (1 each, sorted by MCC asc)
        .andExpect(jsonPath("$.topMccs[0].mcc").value("5812"))
        .andExpect(jsonPath("$.topMccs[0].attempts").value(2))
        .andExpect(jsonPath("$.topMccs[0].label").value("Restaurants"));
  }

  @Test
  @DisplayName("Summary report: contiguous zero-filled series across month boundary, live counts")
  void testSummaryReportSeriesAndSnapshots() throws Exception {
    // Oct 30 to Nov 2 is 4 days: 2026-10-30, 2026-10-31, 2026-11-01, 2026-11-02
    Instant oct31 = Instant.parse("2026-10-31T06:00:00Z");
    insertTransaction(
        UUID.randomUUID(),
        50000L,
        "NPR",
        "Supermarket",
        "5411",
        "APPROVED",
        "00",
        null,
        false,
        oct31);

    mockMvc
        .perform(
            get("/api/v1/reports/summary")
                .param("from", "2026-10-30")
                .param("to", "2026-11-02")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-10-30"))
        .andExpect(jsonPath("$.to").value("2026-11-02"))
        .andExpect(jsonPath("$.zone").value("Asia/Kathmandu"))
        .andExpect(jsonPath("$.totals.attempts").value(1))
        .andExpect(jsonPath("$.totals.approved").value(1))
        .andExpect(jsonPath("$.series.length()").value(4))
        .andExpect(jsonPath("$.series[0].date").value("2026-10-30"))
        .andExpect(jsonPath("$.series[0].approved").value(0))
        .andExpect(jsonPath("$.series[1].date").value("2026-10-31"))
        .andExpect(jsonPath("$.series[1].approved").value(1))
        .andExpect(jsonPath("$.series[1].approvedVolume[0].currency").value("NPR"))
        .andExpect(jsonPath("$.series[1].approvedVolume[0].amountMinor").value(50000))
        .andExpect(jsonPath("$.series[2].date").value("2026-11-01"))
        .andExpect(jsonPath("$.series[2].approved").value(0))
        .andExpect(jsonPath("$.series[3].date").value("2026-11-02"))
        .andExpect(jsonPath("$.series[3].approved").value(0))
        .andExpect(jsonPath("$.activeCards").isNumber())
        .andExpect(jsonPath("$.openDisputes").value(0));
  }
}
