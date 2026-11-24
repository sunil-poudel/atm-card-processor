package dev.minicard.processor.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.ledger.domain.LedgerEntry;
import dev.minicard.processor.ledger.repository.LedgerEntryRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class AuthorizationConcurrencyIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private CardRepository cardRepository;
  @Autowired private LedgerEntryRepository ledgerEntryRepository;
  @Autowired private TransactionRepository transactionRepository;

  @Autowired
  private dev.minicard.processor.fraud.repository.FraudRuleRepository fraudRuleRepository;

  private String adminToken;
  private String apiKey;

  @BeforeEach
  void setUp() throws Exception {
    fraudRuleRepository
        .findByNameIgnoreCase("Rapid-fire attempts")
        .ifPresent(
            r -> {
              r.setEnabled(false);
              fraudRuleRepository.save(r);
            });

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

    MvcResult clientResult =
        mockMvc
            .perform(
                post("/api/v1/api-clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"concurrency-pos-" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    apiKey = JsonPath.read(clientResult.getResponse().getContentAsString(), "$.apiKey");
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    fraudRuleRepository
        .findByNameIgnoreCase("Rapid-fire attempts")
        .ifPresent(
            r -> {
              r.setEnabled(true);
              fraudRuleRepository.save(r);
            });
  }

  @org.junit.jupiter.api.RepeatedTest(10)
  @DisplayName("50 parallel requests competing for balance: exactly 20 approved, 30 declined (51)")
  void parallelApprovalsCompetingForBalance() throws Exception {
    // Card with balance 20,000 NPR (200.00 NPR)
    // 50 parallel requests of 1,000 NPR each
    // Exactly 20 must approve (total 20,000 debited), 30 must decline (51 INSUFFICIENT_FUNDS)
    // Final balance must be exactly 0
    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Concurrency Test User",
                          "currency": "NPR",
                          "validityYears": 3,
                          "perTransactionLimitMinor": 50000,
                          "dailyLimitMinor": 100000,
                          "initialBalanceMinor": 20000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String cardId = JsonPath.read(issueResult.getResponse().getContentAsString(), "$.id");
    UUID cardUuid = UUID.fromString(cardId);

    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());

    int threadCount = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger approvedCount = new AtomicInteger();
    AtomicInteger declinedCount = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      String idempKey = "concurrent-key-" + i + "-" + UUID.randomUUID();
      String payload =
          """
          {
            "cardId": "%s",
            "amountMinor": 1000,
            "currency": "NPR",
            "merchantName": "Online Store",
            "mcc": "5411"
          }
          """
              .formatted(cardId);

      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  MvcResult result =
                      mockMvc
                          .perform(
                              post("/api/v1/authorize")
                                  .header("X-Api-Key", apiKey)
                                  .header("Idempotency-Key", idempKey)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(payload))
                          .andReturn();

                  int status = result.getResponse().getStatus();
                  if (status == 200) {
                    String body = result.getResponse().getContentAsString();
                    String outcome = JsonPath.read(body, "$.status");
                    if ("APPROVED".equals(outcome)) {
                      approvedCount.incrementAndGet();
                    } else if ("DECLINED".equals(outcome)) {
                      declinedCount.incrementAndGet();
                    }
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }

    startLatch.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    for (Future<?> f : futures) {
      f.get();
    }

    assertThat(approvedCount.get()).isEqualTo(20);
    assertThat(declinedCount.get()).isEqualTo(30);

    // Final balance check: exactly 0
    Card card = cardRepository.findById(cardUuid).orElseThrow();
    assertThat(card.getBalanceMinor()).isEqualTo(0L);

    // Ledger sum invariant: initial 20000 + 20 * (-1000) = 0
    List<LedgerEntry> entries = ledgerEntryRepository.findByCardIdOrderByCreatedAtAsc(cardUuid);
    long ledgerSum = entries.stream().mapToLong(LedgerEntry::getAmountMinor).sum();
    assertThat(ledgerSum).isEqualTo(0L);
    assertThat(card.getBalanceMinor()).isEqualTo(ledgerSum);
  }

  @org.junit.jupiter.api.RepeatedTest(10)
  @DisplayName(
      "Concurrent duplicate idempotency requests: exactly 1 debit, all identical bodies or 409")
  void concurrentDuplicateIdempotencyRequests() throws Exception {
    MvcResult issueResult =
        mockMvc
            .perform(
                post("/api/v1/cards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "holderName": "Duplicate Key User",
                          "currency": "NPR",
                          "validityYears": 3,
                          "perTransactionLimitMinor": 50000,
                          "dailyLimitMinor": 100000,
                          "initialBalanceMinor": 50000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String cardId = JsonPath.read(issueResult.getResponse().getContentAsString(), "$.id");
    UUID cardUuid = UUID.fromString(cardId);

    mockMvc
        .perform(
            post("/api/v1/cards/" + cardId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());

    int threadCount = 10;
    String duplicateKey = "duplicate-key-" + UUID.randomUUID();
    String payload =
        """
        {
          "cardId": "%s",
          "amountMinor": 5000,
          "currency": "NPR",
          "merchantName": "Grocery",
          "mcc": "5411"
        }
        """
            .formatted(cardId);

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<MvcResult>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                return mockMvc
                    .perform(
                        post("/api/v1/authorize")
                            .header("X-Api-Key", apiKey)
                            .header("Idempotency-Key", duplicateKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn();
              }));
    }

    startLatch.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    int success200 = 0;
    int conflict409 = 0;
    String approvedTxId = null;

    for (Future<MvcResult> f : futures) {
      MvcResult res = f.get();
      int status = res.getResponse().getStatus();
      if (status == 200) {
        success200++;
        String body = res.getResponse().getContentAsString();
        String txId = JsonPath.read(body, "$.transactionId");
        if (approvedTxId == null) {
          approvedTxId = txId;
        } else {
          assertThat(txId).isEqualTo(approvedTxId);
        }
      } else if (status == 409) {
        conflict409++;
      }
    }

    assertThat(success200).isGreaterThanOrEqualTo(1);
    assertThat(success200 + conflict409).isEqualTo(threadCount);

    // Exactly one transaction row in DB
    List<Transaction> txs =
        transactionRepository.findAll().stream()
            .filter(t -> duplicateKey.equals(t.getIdempotencyKey()))
            .toList();
    assertThat(txs).hasSize(1);

    // Exactly one debit of 5,000 NPR (balance: 50,000 - 5,000 = 45,000)
    Card card = cardRepository.findById(cardUuid).orElseThrow();
    assertThat(card.getBalanceMinor()).isEqualTo(45000L);
  }
}
