package dev.minicard.processor.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.minicard.processor.cards.domain.CardStatus;
import dev.minicard.processor.cards.repository.CardRepository;
import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.reports.repository.ReportQueryRepository;
import dev.minicard.processor.reports.web.dto.DailyReportResponse;
import dev.minicard.processor.reports.web.dto.SummaryReportResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

  private ReportQueryRepository reportQueryRepository;
  private DisputeRepository disputeRepository;
  private CardRepository cardRepository;
  private Clock clock;
  private ReportService reportService;

  private final ZoneId zone = ZoneId.of("Asia/Kathmandu");

  @BeforeEach
  void setUp() {
    reportQueryRepository = mock(ReportQueryRepository.class);
    disputeRepository = mock(DisputeRepository.class);
    cardRepository = mock(CardRepository.class);
    clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), zone);
    reportService =
        new ReportService(reportQueryRepository, disputeRepository, cardRepository, clock);
  }

  @Test
  @DisplayName("Approval rate percent rounding per spec: 2/3 -> 66.67, 1/8 -> 12.50, 0 -> 0.00")
  void testApprovalRateRounding() {
    assertThat(reportService.calculateApprovalRate(2, 3)).isEqualTo(new BigDecimal("66.67"));
    assertThat(reportService.calculateApprovalRate(1, 8)).isEqualTo(new BigDecimal("12.50"));
    assertThat(reportService.calculateApprovalRate(0, 0)).isEqualTo(new BigDecimal("0.00"));
    assertThat(reportService.calculateApprovalRate(101, 120)).isEqualTo(new BigDecimal("84.17"));
    assertThat(reportService.calculateApprovalRate(1, 3)).isEqualTo(new BigDecimal("33.33"));
  }

  @Test
  @DisplayName("Daily report returns 24 zero-filled hourly buckets")
  void testDailyReportZeroFills24Hours() {
    LocalDate date = LocalDate.of(2026, 10, 20);
    when(reportQueryRepository.queryTotals(any(), any()))
        .thenReturn(new ReportQueryRepository.RawTotals(1, 1, 0, 0));
    when(reportQueryRepository.queryApprovedVolume(any(), any())).thenReturn(List.of());
    when(reportQueryRepository.queryDeclinesByCode(any(), any())).thenReturn(List.of());
    when(reportQueryRepository.queryTopMccs(any(), any(), anyInt())).thenReturn(List.of());
    when(reportQueryRepository.queryApprovedVolumeByMcc(any(), any())).thenReturn(List.of());
    when(reportQueryRepository.queryHourly(any(), any(), eq("Asia/Kathmandu")))
        .thenReturn(List.of(new ReportQueryRepository.RawHourly(14, 1, 1, 0)));

    DailyReportResponse response = reportService.getDailyReport(date);

    assertThat(response.date()).isEqualTo(date);
    assertThat(response.zone()).isEqualTo("Asia/Kathmandu");
    assertThat(response.hourly()).hasSize(24);

    // Hour 14 has the query result
    assertThat(response.hourly().get(14).hour()).isEqualTo(14);
    assertThat(response.hourly().get(14).attempts()).isEqualTo(1);
    assertThat(response.hourly().get(14).approved()).isEqualTo(1);
    assertThat(response.hourly().get(14).declined()).isEqualTo(0);

    // Other hours are 0
    assertThat(response.hourly().get(0).hour()).isEqualTo(0);
    assertThat(response.hourly().get(0).attempts()).isEqualTo(0);
    assertThat(response.hourly().get(23).hour()).isEqualTo(23);
    assertThat(response.hourly().get(23).attempts()).isEqualTo(0);
  }

  @Test
  @DisplayName("Summary report rejects null dates, from > to, and range > 92 days")
  void testSummaryReportValidation() {
    assertThatThrownBy(() -> reportService.getSummaryReport(null, LocalDate.of(2026, 10, 20)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("From and to dates are required");

    assertThatThrownBy(
            () ->
                reportService.getSummaryReport(
                    LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("From date must be less than or equal to to date");

    assertThatThrownBy(
            () ->
                reportService.getSummaryReport(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 10))) // 100 days
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Date range cannot exceed 92 days");
  }

  @Test
  @DisplayName("Summary report series is contiguous and zero-filled across month boundaries")
  void testSummaryReportSeriesContiguousZeroFilled() {
    LocalDate from = LocalDate.of(2026, 10, 30);
    LocalDate to = LocalDate.of(2026, 11, 2); // 4 days: Oct 30, Oct 31, Nov 1, Nov 2

    when(reportQueryRepository.queryTotals(any(), any()))
        .thenReturn(new ReportQueryRepository.RawTotals(2, 2, 0, 0));
    when(reportQueryRepository.queryApprovedVolume(any(), any())).thenReturn(List.of());
    when(reportQueryRepository.queryDailySeries(any(), any(), eq("Asia/Kathmandu")))
        .thenReturn(
            List.of(
                new ReportQueryRepository.RawDailySummary(LocalDate.of(2026, 10, 31), 2, 0, 0)));
    when(reportQueryRepository.queryDailyVolumeSeries(any(), any(), eq("Asia/Kathmandu")))
        .thenReturn(List.of());
    when(disputeRepository.countByStatusNotIn(any())).thenReturn(3L);
    when(cardRepository.countByStatus(CardStatus.ACTIVE)).thenReturn(10L);

    SummaryReportResponse response = reportService.getSummaryReport(from, to);

    assertThat(response.from()).isEqualTo(from);
    assertThat(response.to()).isEqualTo(to);
    assertThat(response.series()).hasSize(4);
    assertThat(response.openDisputes()).isEqualTo(3L);
    assertThat(response.activeCards()).isEqualTo(10L);

    assertThat(response.series().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(response.series().get(0).approved()).isEqualTo(0);

    assertThat(response.series().get(1).date()).isEqualTo(LocalDate.of(2026, 10, 31));
    assertThat(response.series().get(1).approved()).isEqualTo(2);

    assertThat(response.series().get(2).date()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(response.series().get(2).approved()).isEqualTo(0);

    assertThat(response.series().get(3).date()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(response.series().get(3).approved()).isEqualTo(0);
  }
}
