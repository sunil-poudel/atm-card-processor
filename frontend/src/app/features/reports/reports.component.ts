import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { Router } from '@angular/router';
import { ChartData, ChartOptions } from 'chart.js';
import { NotificationService } from '../../core/notify/notification.service';
import { ReferenceDataService } from '../../core/reference/reference-data.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { ChartWrapperComponent } from '../../shared/ui/chart-wrapper.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { KpiCardComponent } from '../../shared/ui/kpi-card.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import {
  DailyReportResponse,
  ReportsApiService,
  SummaryReportResponse,
} from './reports-api.service';

function getTodayKathmanduIso(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kathmandu',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  return parts; // 'YYYY-MM-DD'
}

function getDaysAgoKathmanduIso(daysAgo: number): string {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kathmandu',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d);
}

@Component({
  selector: 'mc-reports',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTabsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    PageHeaderComponent,
    KpiCardComponent,
    ChartWrapperComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    MoneyPipe,
  ],
  template: `
    <mc-page-header
      title="Operational Reports"
      subtitle="Operational performance analytics in Asia/Kathmandu business zone"
    >
      <div actions class="header-badges">
        <span class="zone-badge">
          <mat-icon>schedule</mat-icon>
          <span>Zone: Asia/Kathmandu (UTC+05:45)</span>
        </span>
      </div>
    </mc-page-header>

    <mat-tab-group class="reports-tabs" animationDuration="0ms">
      <!-- Tab 1: Daily Operational Report -->
      <mat-tab label="Daily Report">
        <div class="tab-body">
          <!-- Date Filter Header -->
          <mat-card class="filter-card">
            <mat-card-content>
              <form [formGroup]="dailyForm" (ngSubmit)="loadDailyReport()" class="filter-form">
                <mat-form-field appearance="outline" class="date-field">
                  <mat-label>Business Date (YYYY-MM-DD)</mat-label>
                  <input matInput type="date" formControlName="date" placeholder="YYYY-MM-DD" />
                </mat-form-field>

                <button mat-flat-button color="primary" type="submit" [disabled]="dailyLoading()">
                  <mat-icon>refresh</mat-icon>
                  <span>Load Report</span>
                </button>
              </form>
            </mat-card-content>
          </mat-card>

          @if (dailyLoading()) {
            <div class="skeleton-wrap">
              <mc-loading-skeleton [rows]="6"></mc-loading-skeleton>
            </div>
          } @else {
            @if (dailyError()) {
              <mc-error-state
                title="Failed to load daily report"
                [message]="dailyError() || ''"
                (retry)="loadDailyReport()"
              ></mc-error-state>
            } @else {
              @if (dailyReport(); as rep) {
                <!-- KPI Summary Cards -->
                <div class="kpi-grid">
                  <mc-kpi-card
                    title="Total Attempts"
                    [value]="rep.attempts?.toString() || '0'"
                    icon="swap_horiz"
                  ></mc-kpi-card>

                  <mc-kpi-card
                    title="Approved"
                    [value]="rep.approved?.toString() || '0'"
                    icon="check_circle"
                    [subtitle]="(rep.approvalRatePercent || 0) + '% Approval Rate'"
                  ></mc-kpi-card>

                  <mc-kpi-card
                    title="Declined"
                    [value]="rep.declined?.toString() || '0'"
                    icon="cancel"
                  ></mc-kpi-card>

                  <mc-kpi-card
                    title="Flagged for Fraud"
                    [value]="rep.flagged?.toString() || '0'"
                    icon="flag"
                  ></mc-kpi-card>
                </div>

                <!-- Approved Volume by Currency -->
                <mat-card class="volume-card">
                  <mat-card-header>
                    <mat-card-title>Approved Volume by Currency</mat-card-title>
                    <mat-card-subtitle>
                      Total volume processed per currency (distinct ledger currencies)
                    </mat-card-subtitle>
                  </mat-card-header>
                  <mat-card-content>
                    @if (rep.approvedVolume && rep.approvedVolume.length > 0) {
                      <div class="volume-chips">
                        @for (v of rep.approvedVolume; track v.currency) {
                          <div class="volume-chip">
                            <span class="curr-tag">{{ v.currency }}</span>
                            <span class="vol-amount tabular-nums">
                              {{ v.amountMinor | money: v.currency }}
                            </span>
                          </div>
                        }
                      </div>
                    } @else {
                      <p class="text-muted">No approved volume recorded for this date.</p>
                    }
                  </mat-card-content>
                </mat-card>

                <!-- Hourly Activity Chart -->
                <mat-card class="chart-card">
                  <mat-card-header>
                    <mat-card-title>Hourly Authorization Distribution</mat-card-title>
                    <mat-card-subtitle
                      >Hourly attempts vs approvals across the 24-hour business
                      day</mat-card-subtitle
                    >
                  </mat-card-header>
                  <mat-card-content>
                    @if (hourlyChartData(); as cData) {
                      <mc-chart-wrapper
                        type="bar"
                        [data]="cData"
                        [options]="hourlyChartOptions"
                        height="280px"
                      ></mc-chart-wrapper>
                    }
                  </mat-card-content>
                </mat-card>

                <!-- Two Column Tables: Declines by Code & Top MCCs -->
                <div class="tables-grid">
                  <!-- Declines by Code -->
                  <mat-card class="table-card">
                    <mat-card-header>
                      <mat-card-title>Declines by Response Code</mat-card-title>
                    </mat-card-header>
                    <mat-card-content>
                      @if (rep.declinesByCode && rep.declinesByCode.length > 0) {
                        <table mat-table [dataSource]="rep.declinesByCode" class="mc-dense-table">
                          <ng-container matColumnDef="code">
                            <th mat-header-cell *matHeaderCellDef>Code</th>
                            <td mat-cell *matCellDef="let row" class="mono font-semibold">
                              {{ row.responseCode }}
                            </td>
                          </ng-container>

                          <ng-container matColumnDef="message">
                            <th mat-header-cell *matHeaderCellDef>Reason</th>
                            <td mat-cell *matCellDef="let row">{{ row.message }}</td>
                          </ng-container>

                          <ng-container matColumnDef="count">
                            <th mat-header-cell *matHeaderCellDef class="count-col">Count</th>
                            <td
                              mat-cell
                              *matCellDef="let row"
                              class="count-col tabular-nums font-semibold"
                            >
                              {{ row.count }}
                            </td>
                          </ng-container>

                          <tr mat-header-row *matHeaderRowDef="['code', 'message', 'count']"></tr>
                          <tr
                            mat-row
                            *matRowDef="let r; columns: ['code', 'message', 'count']"
                          ></tr>
                        </table>
                      } @else {
                        <p class="text-muted padding-box">Zero declines recorded on this date.</p>
                      }
                    </mat-card-content>
                  </mat-card>

                  <!-- Top MCCs -->
                  <mat-card class="table-card">
                    <mat-card-header>
                      <mat-card-title>Top Merchant Categories (MCC)</mat-card-title>
                    </mat-card-header>
                    <mat-card-content>
                      @if (rep.topMccs && rep.topMccs.length > 0) {
                        <table mat-table [dataSource]="rep.topMccs" class="mc-dense-table">
                          <ng-container matColumnDef="mcc">
                            <th mat-header-cell *matHeaderCellDef>MCC</th>
                            <td mat-cell *matCellDef="let row" class="mono font-semibold">
                              {{ row.mcc }}
                            </td>
                          </ng-container>

                          <ng-container matColumnDef="category">
                            <th mat-header-cell *matHeaderCellDef>Category</th>
                            <td mat-cell *matCellDef="let row">
                              {{ row.label || getMccLabel(row.mcc) }}
                            </td>
                          </ng-container>

                          <ng-container matColumnDef="count">
                            <th mat-header-cell *matHeaderCellDef class="count-col">Attempts</th>
                            <td
                              mat-cell
                              *matCellDef="let row"
                              class="count-col tabular-nums font-semibold"
                            >
                              {{ row.attempts }}
                            </td>
                          </ng-container>

                          <tr mat-header-row *matHeaderRowDef="['mcc', 'category', 'count']"></tr>
                          <tr
                            mat-row
                            *matRowDef="let r; columns: ['mcc', 'category', 'count']"
                          ></tr>
                        </table>
                      } @else {
                        <p class="text-muted padding-box">No merchant categories recorded.</p>
                      }
                    </mat-card-content>
                  </mat-card>
                </div>
              }
            }
          }
        </div>
      </mat-tab>

      <!-- Tab 2: Range Summary Report -->
      <mat-tab label="Range Summary">
        <div class="tab-body">
          <!-- Range Filter Header -->
          <mat-card class="filter-card">
            <mat-card-content>
              <form [formGroup]="rangeForm" (ngSubmit)="loadRangeSummary()" class="filter-form">
                <mat-form-field appearance="outline" class="date-field">
                  <mat-label>From Date</mat-label>
                  <input matInput type="date" formControlName="from" />
                </mat-form-field>

                <mat-form-field appearance="outline" class="date-field">
                  <mat-label>To Date</mat-label>
                  <input matInput type="date" formControlName="to" />
                </mat-form-field>

                <button mat-flat-button color="primary" type="submit" [disabled]="rangeLoading()">
                  <mat-icon>analytics</mat-icon>
                  <span>Generate Summary</span>
                </button>

                <button
                  mat-stroked-button
                  color="accent"
                  type="button"
                  (click)="exportTransactionsRange()"
                  matTooltip="Export all transactions in this date range to CSV"
                >
                  <mat-icon>file_download</mat-icon>
                  <span>Export Range CSV</span>
                </button>
              </form>
            </mat-card-content>
          </mat-card>

          @if (rangeLoading()) {
            <div class="skeleton-wrap">
              <mc-loading-skeleton [rows]="6"></mc-loading-skeleton>
            </div>
          } @else {
            @if (rangeError()) {
              <mc-error-state
                title="Failed to load range summary"
                [message]="rangeError() || ''"
                (retry)="loadRangeSummary()"
              ></mc-error-state>
            } @else {
              @if (summaryReport(); as sum) {
                <!-- KPI Summary Cards -->
                <div class="kpi-grid">
                  <mc-kpi-card
                    title="Total Attempts"
                    [value]="sum.totals?.attempts?.toString() || '0'"
                    icon="swap_horiz"
                  ></mc-kpi-card>

                  <mc-kpi-card
                    title="Approved"
                    [value]="sum.totals?.approved?.toString() || '0'"
                    icon="check_circle"
                    [subtitle]="(sum.totals?.approvalRatePercent || 0) + '% Approval Rate'"
                  ></mc-kpi-card>

                  <mc-kpi-card
                    title="Active Cards"
                    [value]="sum.activeCards?.toString() || '0'"
                    icon="credit_card"
                  ></mc-kpi-card>

                  <mc-kpi-card
                    title="Pending Disputes"
                    [value]="sum.openDisputes?.toString() || '0'"
                    icon="gavel"
                  ></mc-kpi-card>
                </div>

                <!-- Approved Volume by Currency -->
                <mat-card class="volume-card">
                  <mat-card-header>
                    <mat-card-title>Total Range Volume by Currency</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    @if (sum.approvedVolume && sum.approvedVolume.length > 0) {
                      <div class="volume-chips">
                        @for (v of sum.approvedVolume; track v.currency) {
                          <div class="volume-chip">
                            <span class="curr-tag">{{ v.currency }}</span>
                            <span class="vol-amount tabular-nums">
                              {{ v.amountMinor | money: v.currency }}
                            </span>
                          </div>
                        }
                      </div>
                    } @else {
                      <p class="text-muted">No approved volume recorded in this date range.</p>
                    }
                  </mat-card-content>
                </mat-card>

                <!-- Series Trend Chart -->
                <mat-card class="chart-card">
                  <mat-card-header>
                    <mat-card-title>Daily Authorization Volume Trend</mat-card-title>
                    <mat-card-subtitle
                      >Attempts, approvals, and declines across the selected date
                      range</mat-card-subtitle
                    >
                  </mat-card-header>
                  <mat-card-content>
                    @if (seriesChartData(); as sData) {
                      <mc-chart-wrapper
                        type="line"
                        [data]="sData"
                        [options]="seriesChartOptions"
                        height="320px"
                      ></mc-chart-wrapper>
                    }
                  </mat-card-content>
                </mat-card>
              }
            }
          }
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [
    `
      .header-badges {
        display: flex;
        align-items: center;
      }
      .zone-badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #cbd5e1;
        border-radius: 16px;
        padding: 4px 12px;
        font-size: 13px;
        font-weight: 500;
      }
      .zone-badge mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }

      .tab-body {
        padding: 20px 0;
      }

      .filter-card {
        margin-bottom: 24px;
        border: 1px solid #e2e8f0;
      }
      .filter-form {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 16px;
        padding-top: 8px;
      }
      .date-field {
        min-width: 200px;
      }

      .skeleton-wrap {
        padding: 24px;
        background: white;
        border-radius: 8px;
      }

      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }

      .volume-card {
        margin-bottom: 24px;
        border: 1px solid #e2e8f0;
      }
      .volume-chips {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        padding-top: 12px;
      }
      .volume-chip {
        display: flex;
        align-items: center;
        gap: 8px;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 8px 16px;
      }
      .curr-tag {
        font-size: 12px;
        font-weight: 700;
        color: #475569;
        background: #e2e8f0;
        padding: 2px 6px;
        border-radius: 4px;
      }
      .vol-amount {
        font-size: 16px;
        font-weight: 700;
        color: #0f172a;
      }

      .chart-card {
        margin-bottom: 24px;
        border: 1px solid #e2e8f0;
        padding: 16px 20px 24px 20px;
      }

      .tables-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 24px;
      }
      @media (max-width: 960px) {
        .tables-grid {
          grid-template-columns: 1fr;
        }
      }

      .table-card {
        border: 1px solid #e2e8f0;
      }
      .mc-dense-table {
        width: 100%;
      }
      .count-col {
        text-align: right;
        padding-right: 16px;
      }
      .padding-box {
        padding: 16px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly reportsApi = inject(ReportsApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly dailyReport = signal<DailyReportResponse | null>(null);
  readonly dailyLoading = signal<boolean>(false);
  readonly dailyError = signal<string | null>(null);

  readonly summaryReport = signal<SummaryReportResponse | null>(null);
  readonly rangeLoading = signal<boolean>(false);
  readonly rangeError = signal<string | null>(null);

  readonly mccMap = signal<Record<string, string>>({});

  readonly hourlyChartData = signal<ChartData<'bar'> | null>(null);
  readonly seriesChartData = signal<ChartData<'line'> | null>(null);

  readonly dailyForm: FormGroup = this.fb.group({
    date: [getTodayKathmanduIso(), [Validators.required]],
  });

  readonly rangeForm: FormGroup = this.fb.group({
    from: [getDaysAgoKathmanduIso(14), [Validators.required]],
    to: [getTodayKathmanduIso(), [Validators.required]],
  });

  readonly hourlyChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'top' },
    },
    scales: {
      x: { title: { display: true, text: 'Hour of Day (Asia/Kathmandu)' } },
      y: { beginAtZero: true, title: { display: true, text: 'Count' } },
    },
  };

  readonly seriesChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'top' },
    },
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { beginAtZero: true, title: { display: true, text: 'Count' } },
    },
  };

  ngOnInit(): void {
    this.loadRefData();
    this.loadDailyReport();
    this.loadRangeSummary();
  }

  loadRefData(): void {
    this.refDataService
      .getReferenceData()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (data.mccs) {
            this.mccMap.set(data.mccs);
          }
        },
      });
  }

  loadDailyReport(): void {
    const date = this.dailyForm.value.date;
    if (!date) return;

    this.dailyLoading.set(true);
    this.dailyError.set(null);

    this.reportsApi
      .getDailyReport(date)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rep) => {
          this.dailyReport.set(rep);
          this.dailyLoading.set(false);
          this.buildHourlyChart(rep);
        },
        error: (err) => {
          this.dailyLoading.set(false);
          this.dailyError.set(err?.detail || 'Failed to load daily report');
        },
      });
  }

  loadRangeSummary(): void {
    const { from, to } = this.rangeForm.value;
    if (!from || !to) return;

    // Validation: from <= to and max 92 days
    const fromTime = new Date(from).getTime();
    const toTime = new Date(to).getTime();
    if (fromTime > toTime) {
      this.notify.error('"From" date must be earlier than or equal to "To" date');
      return;
    }
    const daysDiff = (toTime - fromTime) / (1000 * 3600 * 24);
    if (daysDiff > 92) {
      this.notify.error('Maximum range is 92 days (approx. 3 months)');
      return;
    }

    this.rangeLoading.set(true);
    this.rangeError.set(null);

    this.reportsApi
      .getSummaryReport(from, to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sum) => {
          this.summaryReport.set(sum);
          this.rangeLoading.set(false);
          this.buildSeriesChart(sum);
        },
        error: (err) => {
          this.rangeLoading.set(false);
          this.rangeError.set(err?.detail || 'Failed to load summary report');
        },
      });
  }

  private buildHourlyChart(rep: DailyReportResponse): void {
    const labels = Array.from({ length: 24 }, (_, i) => `${i.toString().padStart(2, '0')}:00`);
    const attempts = new Array(24).fill(0);
    const approved = new Array(24).fill(0);

    if (rep.hourly) {
      for (const bucket of rep.hourly) {
        if (bucket.hour !== undefined && bucket.hour >= 0 && bucket.hour < 24) {
          attempts[bucket.hour] = bucket.attempts || 0;
          approved[bucket.hour] = bucket.approved || 0;
        }
      }
    }

    this.hourlyChartData.set({
      labels,
      datasets: [
        {
          label: 'Total Attempts',
          data: attempts,
          backgroundColor: '#94a3b8',
        },
        {
          label: 'Approved',
          data: approved,
          backgroundColor: '#16a34a',
        },
      ],
    });
  }

  private buildSeriesChart(sum: SummaryReportResponse): void {
    if (!sum.series || sum.series.length === 0) {
      this.seriesChartData.set(null);
      return;
    }

    const labels = sum.series.map((s) => s.date || '');
    const attempts = sum.series.map((s) => (s.approved || 0) + (s.declined || 0));
    const approved = sum.series.map((s) => s.approved || 0);
    const declined = sum.series.map((s) => s.declined || 0);

    this.seriesChartData.set({
      labels,
      datasets: [
        {
          label: 'Attempts',
          data: attempts,
          borderColor: '#64748b',
          backgroundColor: 'rgba(100, 116, 139, 0.1)',
          tension: 0.2,
        },
        {
          label: 'Approved',
          data: approved,
          borderColor: '#16a34a',
          backgroundColor: 'rgba(22, 163, 74, 0.1)',
          tension: 0.2,
        },
        {
          label: 'Declined',
          data: declined,
          borderColor: '#dc2626',
          backgroundColor: 'rgba(220, 38, 38, 0.1)',
          tension: 0.2,
        },
      ],
    });
  }

  getMccLabel(mcc?: string): string {
    if (!mcc) return '-';
    return this.mccMap()[mcc] || mcc;
  }

  exportTransactionsRange(): void {
    const { from, to } = this.rangeForm.value;
    this.router.navigate(['/transactions'], {
      queryParams: {
        fromDate: from,
        toDate: to,
      },
    });
  }
}
