package dev.minicard.processor.disputes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.disputes.domain.Dispute;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.disputes.job.DisputeDeadlineJob;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.ledger.domain.LedgerEntry;
import dev.minicard.processor.ledger.domain.LedgerEntryType;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import dev.minicard.processor.security.ApiKeyAuthenticationFilter;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
class DisputeIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private CardRepository cardRepository;
  @Autowired private DisputeRepository disputeRepository;
  @Autowired private LedgerEntryRepository ledgerEntryRepository;
  @Autowired private DisputeDeadlineJob disputeDeadlineJob;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;
  private String analystToken;
  private String disputeOfficerToken;
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

    // 2. Create Analyst user
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

    // 3. Create Dispute Officer user
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
                      "displayName": "Dispute Officer",
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

    // 4. Create POS API Client
    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Dispute Test POS " + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    posApiKey = JsonPath.read(clientResult.getResponse().getContentAsString(), "$.apiKey");
  }

  @Test
  @DisplayName("Full dispute workflow and Worked Example 20: win credits balance exactly once")
  void testFullDisputeLifecycleAndWorkedExample20() throws Exception {
    // Card initial balance 100,000 NPR
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);

    // Purchase 20,000 NPR -> remaining balance 80,000 NPR
    String txId = authorizePurchase(cardId, 20_000L);

    // 1. Open dispute
    MvcResult openResult =
        mockMvc
            .perform(
                post("/api/v1/disputes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "transactionId": "%s",
                          "reason": "UNAUTHORIZED_TRANSACTION",
                          "description": "Customer was overseas during charge"
                        }
                        """
                            .formatted(txId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPENED"))
            .andExpect(jsonPath("$.reason").value("UNAUTHORIZED_TRANSACTION"))
            .andExpect(jsonPath("$.amountMinor").value(20000))
            .andExpect(jsonPath("$.allowedActions[0]").value("REQUEST_EVIDENCE"))
            .andExpect(jsonPath("$.events[0].toStatus").value("OPENED"))
            .andReturn();

    String disputeId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

    // Check transaction detail has dispute link
    mockMvc
        .perform(
            get("/api/v1/transactions/" + txId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dispute.id").value(disputeId))
        .andExpect(jsonPath("$.dispute.status").value("OPENED"));

    // Duplicate open on same transaction fails with 409 DISPUTE_ALREADY_EXISTS
    mockMvc
        .perform(
            post("/api/v1/disputes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "transactionId": "%s",
                      "reason": "DUPLICATE_CHARGE"
                    }
                    """
                        .formatted(txId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DISPUTE_ALREADY_EXISTS"));

    // 2. Request evidence
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/request-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Please provide travel documents\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EVIDENCE_REQUESTED"))
        .andExpect(jsonPath("$.evidenceDueAt").isNotEmpty())
        .andExpect(jsonPath("$.allowedActions[0]").value("SUBMIT_EVIDENCE"))
        .andExpect(jsonPath("$.events[1].toStatus").value("EVIDENCE_REQUESTED"));

    // 3. Submit evidence
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/submit-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Passport stamps and ticket attached\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.allowedActions").isArray())
        .andExpect(
            jsonPath("$.allowedActions")
                .value(org.hamcrest.Matchers.hasItems("RESOLVE_WON", "RESOLVE_LOST")))
        .andExpect(jsonPath("$.events[2].toStatus").value("UNDER_REVIEW"));

    // 4. Resolve WON
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "outcome": "WON",
                      "note": "Merchant could not prove cardholder presence"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED_WON"))
        .andExpect(jsonPath("$.resolvedBy").isNotEmpty())
        .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
        .andExpect(jsonPath("$.allowedActions").isEmpty())
        .andExpect(jsonPath("$.events[3].toStatus").value("RESOLVED_WON"));

    // Check Card balance is credited back: 80,000 + 20,000 = 100,000 NPR
    Card card = cardRepository.findById(UUID.fromString(cardId)).orElseThrow();
    assertThat(card.getBalanceMinor()).isEqualTo(100_000L);

    // Check Ledger has exactly one DISPUTE_CREDIT entry
    List<LedgerEntry> entries =
        ledgerEntryRepository.findByCardIdOrderByCreatedAtAsc(UUID.fromString(cardId));
    List<LedgerEntry> disputeCredits =
        entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DISPUTE_CREDIT).toList();
    assertThat(disputeCredits).hasSize(1);
    LedgerEntry creditEntry = disputeCredits.get(0);
    assertThat(creditEntry.getAmountMinor()).isEqualTo(20_000L);
    assertThat(creditEntry.getBalanceAfterMinor()).isEqualTo(100_000L);
    assertThat(creditEntry.getDisputeId()).isEqualTo(UUID.fromString(disputeId));
  }

  @Test
  @DisplayName("Resolve LOST leaves card balance untouched and creates no credit")
  void testResolveLostMovesNoMoney() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String txId = authorizePurchase(cardId, 15_000L);

    // Open dispute and advance to UNDER_REVIEW
    String disputeId = openAndAdvanceToUnderReview(txId);

    // Resolve as LOST
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "outcome": "LOST",
                      "note": "Merchant proved chip and PIN authorization"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED_LOST"))
        .andExpect(jsonPath("$.allowedActions").isEmpty());

    // Card balance remains 85,000 NPR
    Card card = cardRepository.findById(UUID.fromString(cardId)).orElseThrow();
    assertThat(card.getBalanceMinor()).isEqualTo(85_000L);

    // No DISPUTE_CREDIT entries in ledger
    List<LedgerEntry> entries =
        ledgerEntryRepository.findByCardIdOrderByCreatedAtAsc(UUID.fromString(cardId));
    long creditCount =
        entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DISPUTE_CREDIT).count();
    assertThat(creditCount).isZero();
  }

  @Test
  @DisplayName("Resolve WON on BLOCKED card still receives dispute credit")
  void testResolveWonOnBlockedCard() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String txId = authorizePurchase(cardId, 10_000L);

    String disputeId = openAndAdvanceToUnderReview(txId);

    // Block card
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Suspected compromised\"}"))
        .andExpect(status().isOk());

    // Resolve WON on blocked card
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"WON\",\"note\":\"Cardholder was right\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED_WON"));

    // Card balance is credited back to 100,000 NPR
    Card card = cardRepository.findById(UUID.fromString(cardId)).orElseThrow();
    assertThat(card.getBalanceMinor()).isEqualTo(100_000L);
  }

  @Test
  @DisplayName(
      "Resolve WON on CLOSED card fails with 409 INVALID_STATE_TRANSITION and card is closed")
  void testResolveWonOnClosedCardFails() throws Exception {
    // Initial balance 10,000 NPR
    String cardId = createActiveCard(10_000L, 50_000L, 120_000L);
    // Purchase 10,000 NPR -> balance becomes exactly 0
    String txId = authorizePurchase(cardId, 10_000L);

    String disputeId = openAndAdvanceToUnderReview(txId);

    // Close the card (allowed since balance is 0)
    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());

    // Resolve WON fails with 409 and message "card is closed"
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"WON\",\"note\":\"Valid claim\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
        .andExpect(jsonPath("$.detail").value("card is closed"));

    // Dispute remains UNDER_REVIEW
    Dispute dispute = disputeRepository.findById(UUID.fromString(disputeId)).orElseThrow();
    assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);

    // Card balance remains 0
    Card card = cardRepository.findById(UUID.fromString(cardId)).orElseThrow();
    assertThat(card.getBalanceMinor()).isZero();
  }

  @Test
  @DisplayName("submitEvidence after deadline passes fails with 409 evidence deadline passed")
  void testSubmitEvidenceDeadlinePassed() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String txId = authorizePurchase(cardId, 10_000L);

    // Open dispute
    MvcResult openResult =
        mockMvc
            .perform(
                post("/api/v1/disputes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"transactionId\":\"" + txId + "\",\"reason\":\"GOODS_NOT_RECEIVED\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String disputeId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

    // Request evidence
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/request-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken))
        .andExpect(status().isOk());

    // Simulate deadline passing by setting evidence_due_at in the past
    jdbcTemplate.update(
        "UPDATE disputes SET evidence_due_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
        UUID.fromString(disputeId));

    // Submit evidence fails with 409 "evidence deadline passed"
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/submit-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Here are the shipping details\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
        .andExpect(jsonPath("$.detail").value("evidence deadline passed"));
  }

  @Test
  @DisplayName("Deadline sweeper job auto-closes overdue disputes and is idempotent")
  void testDeadlineSweeperJob() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String tx1 = authorizePurchase(cardId, 5_000L);
    String tx2 = authorizePurchase(cardId, 6_000L);
    String tx3 = authorizePurchase(cardId, 7_000L);

    // Dispute 1: overdue
    String d1 = openDispute(tx1);
    requestEvidence(d1);
    jdbcTemplate.update(
        "UPDATE disputes SET evidence_due_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)),
        UUID.fromString(d1));

    // Dispute 2: not overdue (deadline in future)
    String d2 = openDispute(tx2);
    requestEvidence(d2);

    // Dispute 3: OPENED
    String d3 = openDispute(tx3);

    // Run deadline job
    int processed = disputeDeadlineJob.runOnce();
    assertThat(processed).isGreaterThanOrEqualTo(1);

    // Dispute 1 is now RESOLVED_LOST
    Dispute dispute1 = disputeRepository.findById(UUID.fromString(d1)).orElseThrow();
    assertThat(dispute1.getStatus()).isEqualTo(DisputeStatus.RESOLVED_LOST);
    assertThat(dispute1.getResolvedBy()).isEqualTo("SYSTEM");
    assertThat(dispute1.getResolutionNote()).isEqualTo("Evidence deadline passed");

    // Dispute 2 is still EVIDENCE_REQUESTED
    Dispute dispute2 = disputeRepository.findById(UUID.fromString(d2)).orElseThrow();
    assertThat(dispute2.getStatus()).isEqualTo(DisputeStatus.EVIDENCE_REQUESTED);

    // Dispute 3 is still OPENED
    Dispute dispute3 = disputeRepository.findById(UUID.fromString(d3)).orElseThrow();
    assertThat(dispute3.getStatus()).isEqualTo(DisputeStatus.OPENED);

    // Idempotent second run
    int secondRun = disputeDeadlineJob.runOnce();
    assertThat(secondRun).isZero();
  }

  @Test
  @DisplayName(
      "Role access matrix: ANALYST read-only, DISPUTE_OFFICER/ADMIN write, unauthenticated 401")
  void testRoleAccess() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String txId = authorizePurchase(cardId, 10_000L);

    // Unauthenticated GET /disputes -> 401
    mockMvc.perform(get("/api/v1/disputes")).andExpect(status().isUnauthorized());

    // ANALYST can list disputes -> 200
    mockMvc
        .perform(
            get("/api/v1/disputes").header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk());

    // ANALYST cannot open dispute -> 403
    mockMvc
        .perform(
            post("/api/v1/disputes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionId\":\"" + txId + "\",\"reason\":\"OTHER\"}"))
        .andExpect(status().isForbidden());

    // DISPUTE_OFFICER can open dispute -> 201
    MvcResult openResult =
        mockMvc
            .perform(
                post("/api/v1/disputes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"transactionId\":\"" + txId + "\",\"reason\":\"OTHER\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String disputeId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

    // ANALYST can GET /disputes/{id} -> 200
    mockMvc
        .perform(
            get("/api/v1/disputes/" + disputeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isOk());

    // ANALYST cannot request evidence -> 403
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/request-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Concurrent resolve race: exactly one succeeds, one gets 409, balance credited once")
  void testConcurrentResolveRace() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String txId = authorizePurchase(cardId, 25_000L);
    String disputeId = openAndAdvanceToUnderReview(txId);

    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              MvcResult result =
                  mockMvc
                      .perform(
                          post("/api/v1/disputes/" + disputeId + "/resolve")
                              .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  "{\"outcome\":\"WON\",\"note\":\"Concurrent test resolve\"}"))
                      .andReturn();
              int status = result.getResponse().getStatus();
              if (status == 200) {
                successCount.incrementAndGet();
              } else if (status == 409) {
                conflictCount.incrementAndGet();
              }
            } catch (Exception e) {
              // ignore
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(1);

    // Balance increased by 25,000 exactly once (75,000 + 25,000 = 100,000)
    Card card = cardRepository.findById(UUID.fromString(cardId)).orElseThrow();
    assertThat(card.getBalanceMinor()).isEqualTo(100_000L);

    // Exactly one DISPUTE_CREDIT entry
    List<LedgerEntry> entries =
        ledgerEntryRepository.findByCardIdOrderByCreatedAtAsc(UUID.fromString(cardId));
    long creditCount =
        entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DISPUTE_CREDIT).count();
    assertThat(creditCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Concurrent open race on same transaction: exactly one succeeds, one gets 409")
  void testConcurrentOpenRace() throws Exception {
    String cardId = createActiveCard(100_000L, 50_000L, 120_000L);
    String txId = authorizePurchase(cardId, 10_000L);

    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger createdCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              MvcResult result =
                  mockMvc
                      .perform(
                          post("/api/v1/disputes")
                              .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  "{\"transactionId\":\""
                                      + txId
                                      + "\",\"reason\":\"UNAUTHORIZED_TRANSACTION\"}"))
                      .andReturn();
              int status = result.getResponse().getStatus();
              if (status == 201) {
                createdCount.incrementAndGet();
              } else if (status == 409) {
                conflictCount.incrementAndGet();
              }
            } catch (Exception e) {
              // ignore
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(createdCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(1);
    assertThat(disputeRepository.existsByTransactionId(UUID.fromString(txId))).isTrue();
  }

  private String openAndAdvanceToUnderReview(String txId) throws Exception {
    String disputeId = openDispute(txId);
    requestEvidence(disputeId);
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/submit-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Customer provided receipts\"}"))
        .andExpect(status().isOk());
    return disputeId;
  }

  private String openDispute(String txId) throws Exception {
    MvcResult openResult =
        mockMvc
            .perform(
                post("/api/v1/disputes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"transactionId\":\"" + txId + "\",\"reason\":\"DUPLICATE_CHARGE\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");
  }

  private void requestEvidence(String disputeId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/disputes/" + disputeId + "/request-evidence")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + disputeOfficerToken))
        .andExpect(status().isOk());
  }

  private String authorizePurchase(String cardId, long amount) throws Exception {
    String authPayload =
        """
        {
          "cardId": "%s",
          "amountMinor": %d,
          "currency": "NPR",
          "merchantName": "Dispute Test Merchant",
          "mcc": "5411"
        }
        """
            .formatted(cardId, amount);

    MvcResult authResult =
        mockMvc
            .perform(
                post("/api/v1/authorize")
                    .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, posApiKey)
                    .header("Idempotency-Key", "idemp-dispute-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.responseCode").value("00"))
            .andReturn();

    return JsonPath.read(authResult.getResponse().getContentAsString(), "$.transactionId");
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
