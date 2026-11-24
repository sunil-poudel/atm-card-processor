package dev.minicard.processor.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import dev.minicard.processor.security.ApiKeyAuthenticationFilter;
import dev.minicard.processor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
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
class ApiClientManagementIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApiClientRepository apiClientRepository;

  private String adminToken;

  @BeforeEach
  void setUpAdmin() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"admin@minicard.local\",\"password\":\"TestAdminPassword123!\"}"))
            .andExpect(status().isOk())
            .andReturn();

    adminToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
  }

  @Test
  void fullApiClientLifecycleAndApiKeyAuthentication() throws Exception {
    String clientName = "Acquirer POS Simulator " + java.util.UUID.randomUUID();
    // 1. Admin creates an API client
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + clientName + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(clientName))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.apiKey").isNotEmpty())
            .andReturn();

    String responseBody = createResult.getResponse().getContentAsString();
    String clientId = JsonPath.read(responseBody, "$.id");
    String apiKey = JsonPath.read(responseBody, "$.apiKey");

    assertThat(apiKey).startsWith("mck_");

    // 2. Machine caller uses X-API-Key to authenticate against POST /api/v1/authorize (passes auth
    // to controller)
    mockMvc
        .perform(
            post("/api/v1/authorize")
                .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cardId\":\""
                        + java.util.UUID.randomUUID()
                        + "\",\"amountMinor\":1000,\"currency\":\"NPR\",\"merchantName\":\"Test Merchant\",\"mcc\":\"5411\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

    // 3. API key presented to another route (e.g. /api/v1/users) is ignored and returns 401
    mockMvc
        .perform(get("/api/v1/users").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey))
        .andExpect(status().isUnauthorized());

    // 4. Admin revokes the API client
    mockMvc
        .perform(
            post("/api/v1/api-clients/" + clientId + "/revoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVOKED"));

    // 5. Revoked API key is rejected on /api/v1/authorize
    mockMvc
        .perform(
            post("/api/v1/authorize").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey))
        .andExpect(status().isUnauthorized());
  }
}
