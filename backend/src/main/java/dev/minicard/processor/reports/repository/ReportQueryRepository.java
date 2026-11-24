package dev.minicard.processor.reports.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportQueryRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public ReportQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public record RawTotals(long attempts, long approved, long declined, long flagged) {}

  public record RawVolume(String currency, long amountMinor) {}

  public record RawDeclineCode(String responseCode, long count) {}

  public record RawTopMcc(String mcc, long attempts) {}

  public record RawMccVolume(String mcc, String currency, long amountMinor) {}

  public record RawHourly(int hour, long attempts, long approved, long declined) {}

  public record RawDailySummary(LocalDate date, long approved, long declined, long flagged) {}

  public record RawDailyVolume(LocalDate date, String currency, long amountMinor) {}

  public RawTotals queryTotals(Instant fromInstant, Instant toInstant) {
    String sql =
        """
        SELECT
          COUNT(*) AS attempts,
          COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
          COUNT(*) FILTER (WHERE status = 'DECLINED') AS declined,
          COUNT(*) FILTER (WHERE status = 'APPROVED' AND flagged = TRUE) AS flagged
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant));

    return jdbcTemplate.queryForObject(
        sql,
        params,
        (rs, rowNum) ->
            new RawTotals(
                rs.getLong("attempts"),
                rs.getLong("approved"),
                rs.getLong("declined"),
                rs.getLong("flagged")));
  }

  public List<RawVolume> queryApprovedVolume(Instant fromInstant, Instant toInstant) {
    String sql =
        """
        SELECT currency, COALESCE(SUM(amount_minor), 0) AS total_amount
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant AND status = 'APPROVED'
        GROUP BY currency
        ORDER BY currency ASC
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant));

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) -> new RawVolume(rs.getString("currency"), rs.getLong("total_amount")));
  }

  public List<RawDeclineCode> queryDeclinesByCode(Instant fromInstant, Instant toInstant) {
    String sql =
        """
        SELECT response_code, COUNT(*) AS count
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant AND status = 'DECLINED'
        GROUP BY response_code
        ORDER BY count DESC, response_code ASC
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant));

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) -> new RawDeclineCode(rs.getString("response_code"), rs.getLong("count")));
  }

  public List<RawTopMcc> queryTopMccs(Instant fromInstant, Instant toInstant, int limit) {
    String sql =
        """
        SELECT mcc, COUNT(*) AS attempts
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant
        GROUP BY mcc
        ORDER BY attempts DESC, mcc ASC
        LIMIT :limit
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant),
            "limit", limit);

    return jdbcTemplate.query(
        sql, params, (rs, rowNum) -> new RawTopMcc(rs.getString("mcc"), rs.getLong("attempts")));
  }

  public List<RawMccVolume> queryApprovedVolumeByMcc(Instant fromInstant, Instant toInstant) {
    String sql =
        """
        SELECT mcc, currency, COALESCE(SUM(amount_minor), 0) AS total_amount
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant AND status = 'APPROVED'
        GROUP BY mcc, currency
        ORDER BY mcc ASC, currency ASC
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant));

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) ->
            new RawMccVolume(
                rs.getString("mcc"), rs.getString("currency"), rs.getLong("total_amount")));
  }

  public List<RawHourly> queryHourly(Instant fromInstant, Instant toInstant, String zone) {
    String sql =
        """
        SELECT
          EXTRACT(HOUR FROM (created_at AT TIME ZONE :zone))::int AS hr,
          COUNT(*) AS attempts,
          COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
          COUNT(*) FILTER (WHERE status = 'DECLINED') AS declined
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant
        GROUP BY hr
        ORDER BY hr ASC
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant),
            "zone", zone);

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) ->
            new RawHourly(
                rs.getInt("hr"),
                rs.getLong("attempts"),
                rs.getLong("approved"),
                rs.getLong("declined")));
  }

  public List<RawDailySummary> queryDailySeries(
      Instant fromInstant, Instant toInstant, String zone) {
    String sql =
        """
        SELECT
          (created_at AT TIME ZONE :zone)::date AS day,
          COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
          COUNT(*) FILTER (WHERE status = 'DECLINED') AS declined,
          COUNT(*) FILTER (WHERE status = 'APPROVED' AND flagged = TRUE) AS flagged
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant
        GROUP BY day
        ORDER BY day ASC
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant),
            "zone", zone);

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) -> {
          Date date = rs.getDate("day");
          return new RawDailySummary(
              date.toLocalDate(),
              rs.getLong("approved"),
              rs.getLong("declined"),
              rs.getLong("flagged"));
        });
  }

  public List<RawDailyVolume> queryDailyVolumeSeries(
      Instant fromInstant, Instant toInstant, String zone) {
    String sql =
        """
        SELECT
          (created_at AT TIME ZONE :zone)::date AS day,
          currency,
          COALESCE(SUM(amount_minor), 0) AS total_amount
        FROM transactions
        WHERE created_at >= :fromInstant AND created_at < :toInstant AND status = 'APPROVED'
        GROUP BY day, currency
        ORDER BY day ASC, currency ASC
        """;

    Map<String, Object> params =
        Map.of(
            "fromInstant", Timestamp.from(fromInstant),
            "toInstant", Timestamp.from(toInstant),
            "zone", zone);

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) -> {
          Date date = rs.getDate("day");
          return new RawDailyVolume(
              date.toLocalDate(), rs.getString("currency"), rs.getLong("total_amount"));
        });
  }
}
