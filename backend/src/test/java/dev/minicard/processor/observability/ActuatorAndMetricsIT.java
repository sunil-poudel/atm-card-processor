package dev.minicard.processor.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.security.ApiKeyAuthenticationFilter;
import dev.minicard.processor.support.PostgresContainerSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"management.prometheus.metrics.export.enabled=true"})
class ActuatorAndMetricsIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private MeterRegistry meterRegistry;

  private String adminToken;
  private String operatorToken;
  private String posApiKey;
  private String cardId;

  @BeforeEach
  void setUp() throws Exception {
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

    String analystEmail = "analyst-metric-" + UUID.randomUUID() + "@minicard.local";
    mockMvc
        .perform(
            post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "TestOperatorPassword123!",
                      "displayName": "Metric Analyst",
                      "role": "ANALYST"
                    }
                    """
                        .formatted(analystEmail)))
        .andExpect(status().isCreated());

    MvcResult opLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "TestOperatorPassword123!"
                        }
                        """
                            .formatted(analystEmail)))
            .andExpect(status().isOk())
            .andReturn();
    operatorToken = JsonPath.read(opLogin.getResponse().getContentAsString(), "$.accessToken");

    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Metrics POS " + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    posApiKey = JsonPath.read(clientResult.getResponse().getContentAsString(), "$.apiKey");

    MvcResult cardResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Metrics Cardholder",
                          "currency": "NPR",
                          "validityYears": 3,
                          "perTransactionLimitMinor": 500000,
                          "dailyLimitMinor": 1000000,
                          "initialBalanceMinor": 500000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    cardId = JsonPath.read(cardResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Health probes are anonymous; other actuator endpoints are ADMIN-only")
  void testActuatorAccessControl() throws Exception {
    // Health is anonymous
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

    // Metrics endpoint requires ADMIN
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());

    // Prometheus endpoint requires ADMIN
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Metrics recorded on authorize; no PAN or cardId in tags")
  void testAuthorizationMetrics() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                .header("Idempotency-Key", "idemp-metric-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cardId": "%s",
                      "amountMinor": 15000,
                      "currency": "NPR",
                      "merchantName": "Metric Merchant",
                      "mcc": "5411"
                    }
                    """
                        .formatted(cardId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Verify counter minicard.authorizations exists
    var counter =
        meterRegistry
            .find("minicard.authorizations")
            .tag("status", "APPROVED")
            .tag("responseCode", "00")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);

    // Verify duration timer exists
    var timer = meterRegistry.find("minicard.authorization.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isGreaterThanOrEqualTo(1L);

    // Verify cardinality guard: no tag contains PAN or cardId
    meterRegistry
        .getMeters()
        .forEach(
            m -> {
              if (m.getId().getName().startsWith("minicard.")) {
                for (Tag tag : m.getId().getTags()) {
                  assertThat(tag.getKey()).isNotEqualTo("cardId");
                  assertThat(tag.getKey()).isNotEqualTo("pan");
                  assertThat(tag.getValue()).doesNotContain(cardId);
                }
              }
            });
  }
}
