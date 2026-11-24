package dev.minicard.processor.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.audit.domain.AuditEvent;
import dev.minicard.processor.audit.repository.AuditEventRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class TransactionsExportIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuditEventRepository auditEventRepository;

  private String adminToken;
  private String analystToken;
  private String disputeOfficerToken;
  private UUID testCardId;
  private UUID testApiClientId;

  @BeforeEach
  void setUp() throws Exception {
    auditEventRepository.deleteAll();

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
    String analystEmail = "analyst-export-" + UUID.randomUUID() + "@minicard.local";
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
                      "displayName": "Export Analyst",
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
    String officerEmail = "officer-export-" + UUID.randomUUID() + "@minicard.local";
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
                      "displayName": "Export Officer",
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
                          "holderName": "Export Tester",
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

    // 5. Create API client
    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Export POS " + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    testApiClientId =
        UUID.fromString(JsonPath.read(clientResult.getResponse().getContentAsString(), "$.id"));

    jdbcTemplate.update("DELETE FROM dispute_events");
    jdbcTemplate.update("DELETE FROM ledger_entries WHERE entry_type = 'DISPUTE_CREDIT'");
    jdbcTemplate.update("DELETE FROM disputes");
    jdbcTemplate.update("DELETE FROM transaction_rule_hits");
    jdbcTemplate.update("DELETE FROM ledger_entries WHERE entry_type = 'PURCHASE'");
    jdbcTemplate.update("DELETE FROM transactions");
  }

  @Test
  @DisplayName("Security: ADMIN and ANALYST allowed; DISPUTE_OFFICER forbidden")
  void testSecurityRoles() throws Exception {
    mockMvc.perform(get("/api/v1/transactions/export.csv")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/v1/transactions/export.csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/transactions/export.csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CSV export produces correct headers, rows, formula protection and audit record")
  void testCsvExportContentAndAudit() throws Exception {
    UUID txId = UUID.randomUUID();
    String sql =
        """
        INSERT INTO transactions (
          id, card_id, api_client_id, initiated_by_user_id, idempotency_scope, idempotency_key,
          request_fingerprint, amount_minor, currency, merchant_name, mcc, reference,
          status, response_code, decline_reason, flagged, risk_score, balance_after_minor,
          processing_millis, created_at
        ) VALUES (
          ?, ?, ?, NULL, 'client', 'idemp-csv-1',
          '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
          50000, 'NPR', '=FormulaMart, Store', '5411', NULL,
          'APPROVED', '00', NULL, FALSE, 20, 450000,
          12, ?
        )
        """;
    jdbcTemplate.update(
        sql,
        txId,
        testCardId,
        testApiClientId,
        Timestamp.from(Instant.parse("2026-10-20T10:15:30Z")));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/transactions/export.csv")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.matchesPattern(
                            "attachment; filename=\"transactions-[0-9]{8}-[0-9]{6}\\.csv\"")))
            .andReturn();

    String content = result.getResponse().getContentAsString();
    String[] lines = content.split("\r\n");
    assertThat(lines).hasSize(2);
    assertThat(lines[0])
        .isEqualTo(
            "transactionId,createdAt,cardLast4,amount,currency,merchantName,mcc,status,responseCode,declineReason,flagged,riskScore");
    assertThat(lines[1])
        .contains(txId.toString())
        .contains("500.00")
        .contains("\"'=FormulaMart, Store\""); // formula guarded with ' AND quoted for comma

    // Audit event written
    List<AuditEvent> auditEvents = auditEventRepository.findAll();
    assertThat(auditEvents)
        .anyMatch(
            e ->
                "EXPORT_GENERATED".equals(e.getAction())
                    && "Transaction".equals(e.getEntityType()));
  }
}
