package dev.minicard.processor.reports.service;

import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.reference.ReferenceData;
import dev.minicard.processor.disputes.domain.DisputeStatus;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.reports.repository.ReportQueryRepository;
import dev.minicard.processor.reports.web.dto.DailyReportResponse;
import dev.minicard.processor.reports.web.dto.DailySeriesEntry;
import dev.minicard.processor.reports.web.dto.DeclinesByCode;
import dev.minicard.processor.reports.web.dto.HourlyBucket;
import dev.minicard.processor.reports.web.dto.SummaryReportResponse;
import dev.minicard.processor.reports.web.dto.TopMcc;
import dev.minicard.processor.reports.web.dto.TotalsSummary;
import dev.minicard.processor.reports.web.dto.VolumeByCurrency;
import dev.minicard.processor.transactions.domain.ResponseCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kathmandu");
  public static final String BUSINESS_ZONE_STRING = "Asia/Kathmandu";
  private static final int MAX_SUMMARY_DAYS = 92;

  private final ReportQueryRepository reportQueryRepository;
  private final DisputeRepository disputeRepository;
  private final CardRepository cardRepository;
  private final Clock clock;

  public ReportService(
      ReportQueryRepository reportQueryRepository,
      DisputeRepository disputeRepository,
      CardRepository cardRepository,
      Clock clock) {
    this.reportQueryRepository = reportQueryRepository;
    this.disputeRepository = disputeRepository;
    this.cardRepository = cardRepository;
    this.clock = clock;
  }

  public DailyReportResponse getDailyReport(LocalDate date) {
    if (date == null) {
      date = LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    Instant fromInstant = date.atStartOfDay(BUSINESS_ZONE).toInstant();
    Instant toInstant = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

    ReportQueryRepository.RawTotals rawTotals =
        reportQueryRepository.queryTotals(fromInstant, toInstant);
    BigDecimal approvalRatePercent =
        calculateApprovalRate(rawTotals.approved(), rawTotals.attempts());

    List<VolumeByCurrency> approvedVolume =
        reportQueryRepository.queryApprovedVolume(fromInstant, toInstant).stream()
            .map(v -> new VolumeByCurrency(v.currency(), v.amountMinor()))
            .toList();

    List<DeclinesByCode> declinesByCode =
        reportQueryRepository.queryDeclinesByCode(fromInstant, toInstant).stream()
            .map(
                d ->
                    new DeclinesByCode(
                        d.responseCode(), ResponseCode.messageFor(d.responseCode()), d.count()))
            .toList();

    List<ReportQueryRepository.RawTopMcc> rawTopMccs =
        reportQueryRepository.queryTopMccs(fromInstant, toInstant, 5);
    List<ReportQueryRepository.RawMccVolume> rawMccVolumes =
        reportQueryRepository.queryApprovedVolumeByMcc(fromInstant, toInstant);

    Map<String, List<VolumeByCurrency>> mccVolumesMap = new HashMap<>();
    for (ReportQueryRepository.RawMccVolume mv : rawMccVolumes) {
      mccVolumesMap
          .computeIfAbsent(mv.mcc(), k -> new ArrayList<>())
          .add(new VolumeByCurrency(mv.currency(), mv.amountMinor()));
    }

    List<TopMcc> topMccs =
        rawTopMccs.stream()
            .map(
                tm ->
                    new TopMcc(
                        tm.mcc(),
                        ReferenceData.MCC_LABELS.get(tm.mcc()),
                        tm.attempts(),
                        mccVolumesMap.getOrDefault(tm.mcc(), List.of())))
            .toList();

    List<ReportQueryRepository.RawHourly> rawHourly =
        reportQueryRepository.queryHourly(fromInstant, toInstant, BUSINESS_ZONE_STRING);
    Map<Integer, ReportQueryRepository.RawHourly> hourlyMap =
        rawHourly.stream()
            .collect(
                Collectors.toMap(
                    ReportQueryRepository.RawHourly::hour,
                    Function.identity(),
                    (existing, replacement) -> existing));

    List<HourlyBucket> hourly = new ArrayList<>(24);
    for (int h = 0; h < 24; h++) {
      ReportQueryRepository.RawHourly item = hourlyMap.get(h);
      if (item != null) {
        hourly.add(new HourlyBucket(h, item.attempts(), item.approved(), item.declined()));
      } else {
        hourly.add(new HourlyBucket(h, 0, 0, 0));
      }
    }

    return new DailyReportResponse(
        date,
        BUSINESS_ZONE_STRING,
        rawTotals.attempts(),
        rawTotals.approved(),
        rawTotals.declined(),
        rawTotals.flagged(),
        approvalRatePercent,
        approvedVolume,
        declinesByCode,
        topMccs,
        hourly);
  }

  public SummaryReportResponse getSummaryReport(LocalDate from, LocalDate to) {
    if (from == null || to == null) {
      throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "From and to dates are required");
    }
    if (from.isAfter(to)) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "From date must be less than or equal to to date");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days > MAX_SUMMARY_DAYS) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "Date range cannot exceed 92 days");
    }

    Instant fromInstant = from.atStartOfDay(BUSINESS_ZONE).toInstant();
    Instant toInstant = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

    ReportQueryRepository.RawTotals rawTotals =
        reportQueryRepository.queryTotals(fromInstant, toInstant);
    BigDecimal approvalRatePercent =
        calculateApprovalRate(rawTotals.approved(), rawTotals.attempts());
    TotalsSummary totals =
        new TotalsSummary(
            rawTotals.attempts(),
            rawTotals.approved(),
            rawTotals.declined(),
            rawTotals.flagged(),
            approvalRatePercent);

    List<VolumeByCurrency> approvedVolume =
        reportQueryRepository.queryApprovedVolume(fromInstant, toInstant).stream()
            .map(v -> new VolumeByCurrency(v.currency(), v.amountMinor()))
            .toList();

    List<ReportQueryRepository.RawDailySummary> rawDailySummaries =
        reportQueryRepository.queryDailySeries(fromInstant, toInstant, BUSINESS_ZONE_STRING);
    Map<LocalDate, ReportQueryRepository.RawDailySummary> summaryMap =
        rawDailySummaries.stream()
            .collect(
                Collectors.toMap(
                    ReportQueryRepository.RawDailySummary::date,
                    Function.identity(),
                    (existing, replacement) -> existing));

    List<ReportQueryRepository.RawDailyVolume> rawDailyVolumes =
        reportQueryRepository.queryDailyVolumeSeries(fromInstant, toInstant, BUSINESS_ZONE_STRING);
    Map<LocalDate, List<VolumeByCurrency>> dailyVolumeMap = new HashMap<>();
    for (ReportQueryRepository.RawDailyVolume dv : rawDailyVolumes) {
      dailyVolumeMap
          .computeIfAbsent(dv.date(), k -> new ArrayList<>())
          .add(new VolumeByCurrency(dv.currency(), dv.amountMinor()));
    }

    List<DailySeriesEntry> series = new ArrayList<>();
    LocalDate current = from;
    while (!current.isAfter(to)) {
      ReportQueryRepository.RawDailySummary sum = summaryMap.get(current);
      List<VolumeByCurrency> vols = dailyVolumeMap.getOrDefault(current, List.of());
      if (sum != null) {
        series.add(
            new DailySeriesEntry(current, sum.approved(), sum.declined(), sum.flagged(), vols));
      } else {
        series.add(new DailySeriesEntry(current, 0, 0, 0, List.of()));
      }
      current = current.plusDays(1);
    }

    long openDisputes =
        disputeRepository.countByStatusNotIn(
            List.of(DisputeStatus.RESOLVED_WON, DisputeStatus.RESOLVED_LOST));
    long activeCards = cardRepository.countByStatus(CardStatus.ACTIVE);

    return new SummaryReportResponse(
        from, to, BUSINESS_ZONE_STRING, totals, approvedVolume, series, openDisputes, activeCards);
  }

  public BigDecimal calculateApprovalRate(long approved, long attempts) {
    if (attempts == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf(approved)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(attempts), 2, RoundingMode.HALF_UP);
  }
}
