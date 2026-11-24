package dev.minicard.processor.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
class DemoDataSeederIT extends PostgresContainerSupport {

  @Autowired private DemoDataSeeder demoDataSeeder;
  @Autowired private CardRepository cardRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private DisputeRepository disputeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("DemoDataSeeder creates consistent data and is idempotent on second run")
  void testDemoDataSeederIdempotence() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE dispute_events, disputes, transaction_rule_hits, transactions, ledger_entries, card_blocked_mcc, cards CASCADE");
    demoDataSeeder.run(new DefaultApplicationArguments());

    long cardCountInitial = cardRepository.count();
    long txCountInitial = transactionRepository.count();
    long disputeCountInitial = disputeRepository.count();

    assertThat(cardCountInitial).isGreaterThanOrEqualTo(3L);
    assertThat(txCountInitial).isGreaterThanOrEqualTo(2L);
    assertThat(disputeCountInitial).isGreaterThanOrEqualTo(1L);

    // Run again
    demoDataSeeder.run(new DefaultApplicationArguments());

    assertThat(cardRepository.count()).isEqualTo(cardCountInitial);
    assertThat(transactionRepository.count()).isEqualTo(txCountInitial);
    assertThat(disputeRepository.count()).isEqualTo(disputeCountInitial);
  }
}
