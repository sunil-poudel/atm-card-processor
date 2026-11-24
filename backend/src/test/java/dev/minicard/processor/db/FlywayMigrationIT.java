package dev.minicard.processor.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.minicard.processor.support.PostgresContainerSupport;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT extends PostgresContainerSupport {

  @Autowired private DataSource dataSource;

  @Test
  void migrationsCreateAllRequiredTablesAndReferenceData() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
            String.class);

    assertThat(tables)
        .contains(
            "api_clients",
            "app_users",
            "audit_events",
            "card_blocked_mcc",
            "cards",
            "dispute_events",
            "disputes",
            "flyway_schema_history",
            "fraud_rules",
            "ledger_entries",
            "refresh_tokens",
            "transaction_rule_hits",
            "transactions");

    // Check constraints on cards table
    List<String> cardConstraints =
        jdbc.queryForList(
            "SELECT conname FROM pg_constraint WHERE conrelid = 'cards'::regclass", String.class);

    assertThat(cardConstraints)
        .contains(
            "pk_cards",
            "fk_cards_created_by",
            "ux_cards_pan_hash",
            "ck_cards_status",
            "ck_cards_currency",
            "ck_cards_bin",
            "ck_cards_last4",
            "ck_cards_balance",
            "ck_cards_per_txn_limit",
            "ck_cards_daily_limit");

    // Check default reference data from V6
    List<String> defaultRuleNames =
        jdbc.queryForList(
            "SELECT name FROM fraud_rules WHERE name IN ('High value payment', 'Rapid-fire attempts', 'Restricted merchant categories')",
            String.class);
    assertThat(defaultRuleNames)
        .containsExactlyInAnyOrder(
            "High value payment", "Rapid-fire attempts", "Restricted merchant categories");
  }
}
