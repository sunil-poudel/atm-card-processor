package dev.minicard.processor.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.minicard.processor.support.PostgresContainerSupport;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMatrixIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private static final Set<String> PUBLIC_PATTERNS =
      Set.of(
          "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/authorize");

  private static final Set<String> ADMIN_ONLY_PATTERNS =
      Set.of(
          "/api/v1/cards",
          "/api/v1/cards/{id}/activate",
          "/api/v1/cards/{id}/block",
          "/api/v1/cards/{id}/unblock",
          "/api/v1/cards/{id}/close",
          "/api/v1/cards/{id}/limits",
          "/api/v1/cards/{id}/blocked-mccs",
          "/api/v1/cards/{id}/topups",
          "/api/v1/users",
          "/api/v1/users/{id}",
          "/api/v1/api-clients",
          "/api/v1/api-clients/{id}/revoke",
          "/api/v1/audit-events",
          "/api/v1/fraud-rules",
          "/api/v1/fraud-rules/{id}",
          "/api/v1/fraud-rules/{id}/enable",
          "/api/v1/fraud-rules/{id}/disable");

  private static final Set<String> STAFF_PATTERNS =
      Set.of(
          "/api/v1/auth/me",
          "/api/v1/meta/reference-data",
          "/api/v1/cards/{id}",
          "/api/v1/cards/{id}/transactions",
          "/api/v1/cards/{id}/ledger",
          "/api/v1/transactions",
          "/api/v1/transactions/{id}",
          "/api/v1/transactions/export.csv",
          "/api/v1/disputes",
          "/api/v1/disputes/{id}",
          "/api/v1/disputes/{id}/request-evidence",
          "/api/v1/disputes/{id}/submit-evidence",
          "/api/v1/disputes/{id}/resolve",
          "/api/v1/reports/daily",
          "/api/v1/reports/summary");

  @Test
  @DisplayName(
      "Completeness check: Every registered application route is covered in security matrix")
  void testAllMappedRoutesAreInSecurityMatrix() {
    Set<String> allKnown = new HashSet<>();
    allKnown.addAll(PUBLIC_PATTERNS);
    allKnown.addAll(ADMIN_ONLY_PATTERNS);
    allKnown.addAll(STAFF_PATTERNS);

    Set<String> unmapped = new HashSet<>();
    Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

    for (RequestMappingInfo info : handlerMethods.keySet()) {
      Set<String> patterns = info.getDirectPaths();
      if (patterns.isEmpty() && info.getPathPatternsCondition() != null) {
        patterns = info.getPathPatternsCondition().getPatternValues();
      }

      for (String pattern : patterns) {
        if (pattern.startsWith("/error")
            || pattern.startsWith("/v3/api-docs")
            || pattern.startsWith("/swagger-ui")
            || pattern.startsWith("/actuator")) {
          continue;
        }
        if (!allKnown.contains(pattern)) {
          unmapped.add(pattern);
        }
      }
    }

    assertThat(unmapped)
        .as("All application endpoints must be explicitly defined in the security matrix")
        .isEmpty();
  }

  @Test
  @DisplayName("Anonymous users cannot access admin-only endpoints (returns 401)")
  void testAnonymousRejectedOnAdminRoutes() throws Exception {
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/api-clients")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/fraud-rules")).andExpect(status().isUnauthorized());
  }

  private static final String VALID_CARD_PAYLOAD =
      """
      {
        "holderName": "Test Holder",
        "currency": "NPR",
        "validityYears": 3,
        "perTransactionLimitMinor": 10000,
        "dailyLimitMinor": 50000,
        "initialBalanceMinor": 10000
      }
      """;

  @Test
  @WithMockUser(roles = "ANALYST")
  @DisplayName("Analyst role cannot access admin-only endpoints (returns 403)")
  void testAnalystForbiddenOnAdminRoutes() throws Exception {
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/api-clients")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CARD_PAYLOAD))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "DISPUTE_OFFICER")
  @DisplayName("Dispute officer role cannot access admin-only endpoints (returns 403)")
  void testDisputeOfficerForbiddenOnAdminRoutes() throws Exception {
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/api-clients")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CARD_PAYLOAD))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ANALYST")
  @DisplayName("Analyst can access staff read endpoints")
  void testAnalystCanAccessStaffEndpoints() throws Exception {
    mockMvc.perform(get("/api/v1/cards")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/transactions")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/disputes")).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/reports/summary?from=2026-10-01&to=2026-10-20"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Public endpoints are accessible without authentication")
  void testPublicEndpointsAccessible() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
