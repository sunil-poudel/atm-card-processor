package dev.minicard.processor.reports.web;

import dev.minicard.processor.reports.service.ReportService;
import dev.minicard.processor.reports.web.dto.DailyReportResponse;
import dev.minicard.processor.reports.web.dto.SummaryReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Daily operational report and multi-day summary analytics")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/daily")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
  @Operation(summary = "Get daily operational report for a business date")
  public ResponseEntity<DailyReportResponse> getDailyReport(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(reportService.getDailyReport(date));
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
  @Operation(summary = "Get multi-day summary analytics report")
  public ResponseEntity<SummaryReportResponse> getSummaryReport(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(reportService.getSummaryReport(from, to));
  }
}
