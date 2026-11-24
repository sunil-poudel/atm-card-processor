package dev.minicard.processor.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.domain.AuditEvent;
import dev.minicard.processor.audit.repository.AuditEventRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditReadIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditEventRepository auditEventRepository;

  @BeforeEach
  void setUp() {
    auditEventRepository.deleteAll();
  }

  @Test
  @DisplayName("Anonymous access to audit-events returns 401")
  void testAnonymousUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ANALYST")
  @DisplayName("Non-admin roles get 403 Forbidden")
  void testNonAdminForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("Admin can filter audit events by action, entityType, actorLabel and date range")
  void testAdminFilterAuditEvents() throws Exception {
    UUID entity1 = UUID.randomUUID();
    UUID entity2 = UUID.randomUUID();

    AuditEvent ev1 =
        new AuditEvent(
            null,
            Instant.parse("2026-10-20T08:00:00Z"),
            ActorType.USER,
            UUID.randomUUID(),
            "operator@minicard.dev",
            "CARD_ISSUED",
            "Card",
            entity1,
            "req-1",
            "127.0.0.1",
            "Issued card");
    AuditEvent ev2 =
        new AuditEvent(
            null,
            Instant.parse("2026-10-20T09:00:00Z"),
            ActorType.USER,
            UUID.randomUUID(),
            "admin@minicard.dev",
            "USER_CREATED",
            "AppUser",
            entity2,
            "req-2",
            "127.0.0.1",
            "Created user");
    AuditEvent ev3 =
        new AuditEvent(
            null,
            Instant.parse("2026-10-20T10:00:00Z"),
            ActorType.USER,
            UUID.randomUUID(),
            "operator@minicard.dev",
            "CARD_ACTIVATED",
            "Card",
            entity1,
            "req-3",
            "127.0.0.1",
            "Activated card");

    auditEventRepository.saveAll(java.util.List.of(ev1, ev2, ev3));

    // Test filter by action
    mockMvc
        .perform(get("/api/v1/audit-events").param("action", "USER_CREATED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].action").value("USER_CREATED"))
        .andExpect(jsonPath("$.content[0].actorLabel").value("admin@minicard.dev"));

    // Test filter by entityType and entityId
    mockMvc
        .perform(
            get("/api/v1/audit-events")
                .param("entityType", "Card")
                .param("entityId", entity1.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].action").value("CARD_ACTIVATED")) // default sort desc
        .andExpect(jsonPath("$.content[1].action").value("CARD_ISSUED"));

    // Test filter by actorLabel
    mockMvc
        .perform(get("/api/v1/audit-events").param("actorLabel", "operator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    // Test date range filter
    mockMvc
        .perform(
            get("/api/v1/audit-events")
                .param("from", "2026-10-20T08:30:00Z")
                .param("to", "2026-10-20T09:30:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].action").value("USER_CREATED"));
  }
}
