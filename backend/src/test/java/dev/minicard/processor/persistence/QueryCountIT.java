package dev.minicard.processor.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.minicard.processor.demo.DemoDataSeeder;
import dev.minicard.processor.support.PostgresContainerSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true"})
class QueryCountIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private DemoDataSeeder demoDataSeeder;

  private Statistics statistics;

  @BeforeEach
  void setUp() {
    demoDataSeeder.run(new DefaultApplicationArguments());
    statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName(
      "GET /api/v1/cards uses at most 3 queries (count + page content + batch blocked MCCs)")
  void testListCardsQueryCount() throws Exception {
    statistics.clear();
    mockMvc.perform(get("/api/v1/cards")).andExpect(status().isOk());
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(queryCount)
        .as("List cards should not suffer from N+1 queries")
        .isLessThanOrEqualTo(3L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName(
      "GET /api/v1/transactions uses at most 4 queries (count + transactions + batch clients/users)")
  void testListTransactionsQueryCount() throws Exception {
    statistics.clear();
    mockMvc.perform(get("/api/v1/transactions")).andExpect(status().isOk());
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(queryCount)
        .as("List transactions should not suffer from N+1 queries")
        .isLessThanOrEqualTo(4L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName(
      "GET /api/v1/disputes uses at most 3 queries (count + disputes with card + batch MCCs)")
  void testListDisputesQueryCount() throws Exception {
    statistics.clear();
    mockMvc.perform(get("/api/v1/disputes")).andExpect(status().isOk());
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(queryCount)
        .as("List disputes should not suffer from N+1 queries")
        .isLessThanOrEqualTo(3L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("GET /api/v1/audit-events uses at most 2 queries (count + page content)")
  void testListAuditEventsQueryCount() throws Exception {
    statistics.clear();
    mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isOk());
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(queryCount)
        .as("List audit events should not suffer from N+1 queries")
        .isLessThanOrEqualTo(2L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("GET /api/v1/users uses at most 2 queries (count + page content)")
  void testListUsersQueryCount() throws Exception {
    statistics.clear();
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(queryCount)
        .as("List users should not suffer from N+1 queries")
        .isLessThanOrEqualTo(2L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("GET /api/v1/api-clients uses at most 2 queries (count + page content)")
  void testListApiClientsQueryCount() throws Exception {
    statistics.clear();
    mockMvc.perform(get("/api/v1/api-clients")).andExpect(status().isOk());
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(queryCount)
        .as("List api clients should not suffer from N+1 queries")
        .isLessThanOrEqualTo(2L);
  }
}
