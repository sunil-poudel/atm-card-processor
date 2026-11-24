package dev.minicard.processor.transactions.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.config.AppProperties;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.UnprocessableException;
import dev.minicard.processor.transactions.domain.DeclineReason;
import dev.minicard.processor.transactions.domain.Transaction;
import dev.minicard.processor.transactions.domain.TransactionStatus;
import dev.minicard.processor.transactions.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CsvExportService {

  private static final String CSV_HEADER =
      "transactionId,createdAt,cardLast4,amount,currency,merchantName,mcc,status,responseCode,declineReason,flagged,riskScore";
  private static final DateTimeFormatter FILENAME_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final TransactionRepository transactionRepository;
  private final AuditService auditService;
  private final AppProperties appProperties;
  private final Clock clock;
  private final int maxRows;

  public CsvExportService(
      TransactionRepository transactionRepository,
      AuditService auditService,
      AppProperties appProperties,
      Clock clock,
      @Value("${app.export.max-rows:50000}") int maxRows) {
    this.transactionRepository = transactionRepository;
    this.auditService = auditService;
    this.appProperties = appProperties;
    this.clock = clock;
    this.maxRows = maxRows;
  }

  public record CsvExportResult(String filename, byte[] csvBytes, long rowCount) {}

  public CsvExportResult exportTransactions(
      UUID cardId,
      TransactionStatus status,
      String responseCode,
      DeclineReason declineReason,
      String mcc,
      Boolean flagged,
      Long minAmountMinor,
      Long maxAmountMinor,
      Instant from,
      Instant to,
      String q,
      UUID actorId,
      String actorLabel,
      ActorType actorType,
      String ipAddress) {

    Specification<Transaction> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (query != null && !Long.class.equals(query.getResultType())) {
            root.fetch("card", JoinType.INNER);
          }

          if (cardId != null) {
            predicates.add(cb.equal(root.get("card").get("id"), cardId));
          }
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (responseCode != null && !responseCode.isBlank()) {
            predicates.add(cb.equal(root.get("responseCode"), responseCode.trim()));
          }
          if (declineReason != null) {
            predicates.add(cb.equal(root.get("declineReason"), declineReason));
          }
          if (minAmountMinor != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amountMinor"), minAmountMinor));
          }
          if (maxAmountMinor != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amountMinor"), maxAmountMinor));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThan(root.get("createdAt"), to));
          }
          if (q != null && !q.isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("merchantName")), "%" + q.trim().toLowerCase() + "%"));
          }
          if (flagged != null) {
            predicates.add(cb.equal(root.get("flagged"), flagged));
          }
          if (mcc != null && !mcc.isBlank()) {
            predicates.add(cb.equal(root.get("mcc"), mcc.trim()));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    long count = transactionRepository.count(spec);
    if (count > maxRows) {
      throw new UnprocessableException(
          ErrorCode.EXPORT_TOO_LARGE,
          "Export size of " + count + " exceeds limit of " + maxRows + " rows");
    }

    List<Transaction> transactions =
        transactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

    StringWriter writer = new StringWriter();
    writer.write(CSV_HEADER);
    writer.write("\r\n");

    for (Transaction t : transactions) {
      String line = formatTransactionRow(t);
      writer.write(line);
      writer.write("\r\n");
    }

    byte[] bytes = writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

    Instant now = clock.instant();
    String formattedDate = FILENAME_DATE_FORMAT.withZone(appProperties.businessZone()).format(now);
    String filename = "transactions-" + formattedDate + ".csv";

    auditService.record(
        actorType,
        actorId,
        actorLabel,
        "EXPORT_GENERATED",
        "Transaction",
        null,
        ipAddress,
        "Exported " + transactions.size() + " transactions to " + filename);

    return new CsvExportResult(filename, bytes, transactions.size());
  }

  private String formatTransactionRow(Transaction t) {
    String txId = t.getId().toString();
    String createdAt = t.getCreatedAt().toString();
    String cardLast4 = t.getCard().getLast4();
    String amount =
        BigDecimal.valueOf(t.getAmountMinor(), 2)
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString();
    String currency = t.getCurrency();
    String merchantName = t.getMerchantName();
    String mcc = t.getMcc();
    String status = t.getStatus().name();
    String responseCode = t.getResponseCode();
    String declineReason = t.getDeclineReason() != null ? t.getDeclineReason().name() : "";
    String flagged = Boolean.toString(t.isFlagged());
    String riskScore = Integer.toString(t.getRiskScore());

    return String.join(
        ",",
        escapeCell(txId),
        escapeCell(createdAt),
        escapeCell(cardLast4),
        escapeCell(amount),
        escapeCell(currency),
        escapeCell(merchantName),
        escapeCell(mcc),
        escapeCell(status),
        escapeCell(responseCode),
        escapeCell(declineReason),
        escapeCell(flagged),
        escapeCell(riskScore));
  }

  public static String escapeCell(String value) {
    if (value == null) {
      return "";
    }
    String processed = value;

    // Formula injection guard: prefix with ' if starts with =, +, -, @, tab, or carriage return
    if (!processed.isEmpty()) {
      char firstChar = processed.charAt(0);
      if (firstChar == '='
          || firstChar == '+'
          || firstChar == '-'
          || firstChar == '@'
          || firstChar == '\t'
          || firstChar == '\r') {
        processed = "'" + processed;
      }
    }

    // RFC 4180 quoting
    boolean containsSpecial =
        processed.contains(",")
            || processed.contains("\"")
            || processed.contains("\n")
            || processed.contains("\r");

    if (containsSpecial) {
      return "\"" + processed.replace("\"", "\"\"") + "\"";
    }

    return processed;
  }
}
