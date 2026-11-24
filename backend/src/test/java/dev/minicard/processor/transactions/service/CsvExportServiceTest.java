package dev.minicard.processor.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.cards.domain.Card;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class CsvExportServiceTest {

  private TransactionRepository transactionRepository;
  private AuditService auditService;
  private AppProperties appProperties;
  private Clock clock;
  private CsvExportService csvExportService;

  @BeforeEach
  void setUp() {
    transactionRepository = mock(TransactionRepository.class);
    auditService = mock(AuditService.class);
    appProperties = mock(AppProperties.class);
    when(appProperties.businessZone()).thenReturn(ZoneId.of("Asia/Kathmandu"));
    clock = Clock.fixed(Instant.parse("2026-10-20T12:30:45Z"), ZoneId.of("Asia/Kathmandu"));
    csvExportService =
        new CsvExportService(transactionRepository, auditService, appProperties, clock, 50000);
  }

  @Test
  @DisplayName("Formula injection: characters =, +, -, @, \\t, \\r are prefixed with single quote")
  void testFormulaInjectionNeutralized() {
    assertThat(CsvExportService.escapeCell("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
    assertThat(CsvExportService.escapeCell("+12345")).isEqualTo("'+12345");
    assertThat(CsvExportService.escapeCell("-12345")).isEqualTo("'-12345");
    assertThat(CsvExportService.escapeCell("@cmd")).isEqualTo("'@cmd");
    assertThat(CsvExportService.escapeCell("\tdata")).isEqualTo("'\tdata");
  }

  @Test
  @DisplayName("RFC 4180 quoting: commas, quotes, newlines are quoted and quotes doubled")
  void testRfc4180Quoting() {
    assertThat(CsvExportService.escapeCell("Shop, Inc.")).isEqualTo("\"Shop, Inc.\"");
    assertThat(CsvExportService.escapeCell("Shop \"Best\"")).isEqualTo("\"Shop \"\"Best\"\"\"");
    assertThat(CsvExportService.escapeCell("Line 1\nLine 2")).isEqualTo("\"Line 1\nLine 2\"");
  }

  @Test
  @DisplayName(
      "Export generation produces exact header, formatted amount, filename and audit record")
  void testExportGeneration() {
    Card mockCard = mock(Card.class);
    when(mockCard.getLast4()).thenReturn("4321");

    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    when(tx.getCreatedAt()).thenReturn(Instant.parse("2026-10-20T10:00:00Z"));
    when(tx.getCard()).thenReturn(mockCard);
    when(tx.getAmountMinor()).thenReturn(50000L);
    when(tx.getCurrency()).thenReturn("NPR");
    when(tx.getMerchantName()).thenReturn("Bhat-Bhateni, Supermarket");
    when(tx.getMcc()).thenReturn("5411");
    when(tx.getStatus()).thenReturn(TransactionStatus.APPROVED);
    when(tx.getResponseCode()).thenReturn("00");
    when(tx.getDeclineReason()).thenReturn(null);
    when(tx.isFlagged()).thenReturn(false);
    when(tx.getRiskScore()).thenReturn(15);

    when(transactionRepository.count(any(Specification.class))).thenReturn(1L);
    when(transactionRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(tx));

    UUID actorId = UUID.randomUUID();
    CsvExportService.CsvExportResult result =
        csvExportService.exportTransactions(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            actorId,
            "admin@minicard.dev",
            ActorType.USER,
            "127.0.0.1");

    // 12:30:45 UTC is 18:15:45 in Asia/Kathmandu (+05:45)
    assertThat(result.filename()).isEqualTo("transactions-20261020-181545.csv");
    assertThat(result.rowCount()).isEqualTo(1L);

    String csv = new String(result.csvBytes(), StandardCharsets.UTF_8);
    String[] lines = csv.split("\r\n");
    assertThat(lines).hasSize(2);
    assertThat(lines[0])
        .isEqualTo(
            "transactionId,createdAt,cardLast4,amount,currency,merchantName,mcc,status,responseCode,declineReason,flagged,riskScore");
    assertThat(lines[1])
        .contains("11111111-1111-1111-1111-111111111111")
        .contains("4321")
        .contains("500.00") // 50000 minor units -> 500.00
        .contains("\"Bhat-Bhateni, Supermarket\"")
        .contains("5411")
        .contains("APPROVED")
        .contains("00")
        .contains("false")
        .contains("15");

    verify(auditService)
        .record(
            ActorType.USER,
            actorId,
            "admin@minicard.dev",
            "EXPORT_GENERATED",
            "Transaction",
            null,
            "127.0.0.1",
            "Exported 1 transactions to transactions-20261020-181545.csv");
  }

  @Test
  @DisplayName("Export exceeds max rows limit throws 422 EXPORT_TOO_LARGE")
  void testExportTooLargeThrows() {
    CsvExportService smallService =
        new CsvExportService(transactionRepository, auditService, appProperties, clock, 100);
    when(transactionRepository.count(any(Specification.class))).thenReturn(101L);

    assertThatThrownBy(
            () ->
                smallService.exportTransactions(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    UUID.randomUUID(),
                    "admin@minicard.dev",
                    ActorType.USER,
                    "127.0.0.1"))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("Export size of 101 exceeds limit of 100 rows");
  }
}
