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
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { ChartData, ChartOptions } from 'chart.js';
import { forkJoin } from 'rxjs';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { ChartWrapperComponent } from '../../shared/ui/chart-wrapper.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { KpiCardComponent } from '../../shared/ui/kpi-card.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import {
  DailyReportResponse,
  ReportsApiService,
  SummaryReportResponse,
} from '../reports/reports-api.service';
import { TransactionDetailDialogComponent } from '../transactions/dialogs/transaction-detail-dialog.component';
import {
  TransactionSummary,
  TransactionsApiService,
} from '../transactions/transactions-api.service';

function getTodayKathmanduIso(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kathmandu',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
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
  selector: 'mc-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatDialogModule,
    MatTooltipModule,
    PageHeaderComponent,
    KpiCardComponent,
    ChartWrapperComponent,
    StatusChipComponent,
    LoadingSkeletonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    MoneyPipe,
  ],
  template: `
    <mc-page-header
      title="Processor Dashboard"
      subtitle="Real-time operational health, volumes, and fraud alerts"
    >
      <div actions class="header-actions">
        <a mat-stroked-button routerLink="/simulator" class="action-btn">
          <mat-icon>science</mat-icon>
          <span>Payment Simulator</span>
        </a>
        <a mat-flat-button color="primary" routerLink="/cards" class="action-btn">
          <mat-icon>credit_card</mat-icon>
          <span>Manage Cards</span>
        </a>
      </div>
    </mc-page-header>

    @if (loading()) {
      <div class="skeleton-wrap">
        <mc-loading-skeleton [rows]="8"></mc-loading-skeleton>
      </div>
    } @else if (error()) {
      <mc-error-state
        title="Failed to load dashboard data"
        [message]="error() || ''"
        (retry)="loadData()"
      ></mc-error-state>
    } @else {
      <!-- 4 Top KPI Cards -->
      <div class="kpi-grid">
        <mc-kpi-card
          title="Active Cards"
          [value]="activeCards().toString()"
          icon="credit_card"
          subtitle="Currently usable cards"
        ></mc-kpi-card>

        <mc-kpi-card
          title="Today's Approved Volume"
          [value]="todayVolumeSummary()"
          icon="payments"
          [subtitle]="todayApprovedCount() + ' approved authorizations'"
        ></mc-kpi-card>

        <mc-kpi-card
          title="Today's Approval Rate"
          [value]="todayApprovalRate() + '%'"
          icon="verified"
          [subtitle]="todayAttempts() + ' total attempts'"
        ></mc-kpi-card>

        <mc-kpi-card
          title="Pending Disputes"
          [value]="openDisputes().toString()"
          icon="gavel"
          subtitle="Awaiting review or evidence"
        ></mc-kpi-card>
      </div>

      <!-- Trend Chart -->
      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>Authorization Activity Trend (Last 14 Days)</mat-card-title>
          <mat-card-subtitle>Daily attempts vs approvals</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (chartData(); as cData) {
            <mc-chart-wrapper
              type="line"
              [data]="cData"
              [options]="chartOptions"
              height="300px"
            ></mc-chart-wrapper>
          } @else {
            <p class="text-muted padding-box">No authorization data in the last 14 days.</p>
          }
        </mat-card-content>
      </mat-card>

      <!-- Recent Flagged Transactions for Review -->
      <mat-card class="table-card">
        <div class="table-card-header">
          <div>
            <h3 class="table-title">Recent Flagged Transactions</h3>
            <p class="table-subtitle">Transactions flagged for fraud review (Risk Score &ge; 50)</p>
          </div>
          <a mat-stroked-button routerLink="/transactions" [queryParams]="{ flagged: true }">
            <span>View All Flagged</span>
            <mat-icon>chevron_right</mat-icon>
          </a>
        </div>

        <mat-card-content>
          @if (flaggedTransactions().length > 0) {
            <div class="table-responsive">
              <table mat-table [dataSource]="flaggedTransactions()" class="mc-dense-table">
                <!-- Time -->
                <ng-container matColumnDef="time">
                  <th mat-header-cell *matHeaderCellDef>Time</th>
                  <td mat-cell *matCellDef="let t" class="tabular-nums mono">
                    {{ t.createdAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                  </td>
                </ng-container>

                <!-- Card -->
                <ng-container matColumnDef="card">
                  <th mat-header-cell *matHeaderCellDef>Card</th>
                  <td mat-cell *matCellDef="let t" class="mono font-semibold">
                    •••• {{ t.cardLast4 || '----' }}
                  </td>
                </ng-container>

                <!-- Merchant -->
                <ng-container matColumnDef="merchant">
                  <th mat-header-cell *matHeaderCellDef>Merchant</th>
                  <td mat-cell *matCellDef="let t" class="font-medium">{{ t.merchantName }} (MCC {{ t.mcc }})</td>
                </ng-container>

                <!-- Amount -->
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="amount-header">Amount</th>
                  <td mat-cell *matCellDef="let t" class="amount-cell tabular-nums font-semibold mono">
                    {{ t.amountMinor | money: t.currency }}
                  </td>
                </ng-container>

                <!-- Score -->
                <ng-container matColumnDef="riskScore">
                  <th mat-header-cell *matHeaderCellDef class="center-cell">Score</th>
                  <td mat-cell *matCellDef="let t" class="center-cell">
                    <span class="score-pill">{{ t.riskScore }}/100</span>
                  </td>
                </ng-container>

                <!-- Status -->
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef class="center-cell">Status</th>
                  <td mat-cell *matCellDef="let t" class="center-cell">
                    <mc-status-chip [status]="t.status || 'APPROVED'"></mc-status-chip>
                  </td>
                </ng-container>

                <!-- Actions -->
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef class="actions-header">Action</th>
                  <td mat-cell *matCellDef="let t" class="actions-cell">
                    <button
                      mat-icon-button
                      color="primary"
                      (click)="openTxDetail(t.id!)"
                      matTooltip="Inspect rule hits"
                    >
                      <mat-icon>visibility</mat-icon>
                    </button>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
                <tr mat-row *matRowDef="let r; columns: displayedColumns"></tr>
              </table>
            </div>
          } @else {
            <mc-empty-state
              icon="verified_user"
              title="No Flagged Transactions"
              message="No transactions have been flagged by the risk rules engine recently."
            ></mc-empty-state>
          }
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [
    `
      .header-actions {
        display: flex;
        gap: 12px;
        flex-wrap: wrap;
      }
      .skeleton-wrap {
        padding: 24px;
        background: var(--mc-surface);
        border: 1px solid var(--mc-border);
        border-radius: var(--mc-radius);
      }
      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }

      .chart-card {
        margin-bottom: 24px;
        padding: 16px 20px 24px 20px;
      }

      .table-card {
        overflow: hidden;
      }
      .table-card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 18px 24px 12px 24px;
        flex-wrap: wrap;
        gap: 12px;
      }
      .table-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 800;
        letter-spacing: -0.02em;
        color: var(--mc-text-primary);
      }
      .table-subtitle {
        margin: 3px 0 0 0;
        font-size: 0.8125rem;
        color: var(--mc-text-secondary);
      }

      .table-responsive {
        width: 100%;
        overflow-x: auto;
      }

      .mc-dense-table {
        width: 100%;
      }
      .amount-header,
      .amount-cell {
        text-align: right;
      }
      .center-cell {
        text-align: center;
      }
      .actions-header,
      .actions-cell {
        text-align: right;
        padding-right: 16px;
      }
      .score-pill {
        font-size: 0.75rem;
        font-weight: 700;
        font-family: 'JetBrains Mono', monospace;
        background: rgba(245, 158, 11, 0.15);
        color: #f59e0b;
        border: 1px solid rgba(245, 158, 11, 0.35);
        padding: 3px 8px;
        border-radius: 9999px;
      }
      .padding-box {
        padding: 24px;
        color: var(--mc-text-muted);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly reportsApi = inject(ReportsApiService);
  private readonly txApi = inject(TransactionsApiService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly activeCards = signal<number>(0);
  readonly openDisputes = signal<number>(0);
  readonly todayAttempts = signal<number>(0);
  readonly todayApprovedCount = signal<number>(0);
  readonly todayApprovalRate = signal<number>(100);
  readonly todayVolumeSummary = signal<string>('-');

  readonly flaggedTransactions = signal<TransactionSummary[]>([]);
  readonly chartData = signal<ChartData<'line'> | null>(null);

  readonly displayedColumns: string[] = [
    'time',
    'card',
    'merchant',
    'amount',
    'riskScore',
    'status',
    'actions',
  ];

  readonly chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'top' },
    },
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { beginAtZero: true, title: { display: true, text: 'Transactions' } },
    },
  };

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    const todayIso = getTodayKathmanduIso();
    const fourteenDaysAgoIso = getDaysAgoKathmanduIso(14);

    forkJoin({
      daily: this.reportsApi.getDailyReport(todayIso),
      summary: this.reportsApi.getSummaryReport(fourteenDaysAgoIso, todayIso),
      flagged: this.txApi.listTransactions({ flagged: true, size: 5, sort: 'createdAt,desc' }),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ daily, summary, flagged }) => {
          this.loading.set(false);
          this.processDaily(daily);
          this.processSummary(summary);
          this.flaggedTransactions.set(flagged.content || []);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.detail || 'Failed to load dashboard metrics');
        },
      });
  }

  private processDaily(daily: DailyReportResponse): void {
    this.todayAttempts.set(daily.attempts || 0);
    this.todayApprovedCount.set(daily.approved || 0);
    this.todayApprovalRate.set(daily.approvalRatePercent || 0);

    if (daily.approvedVolume && daily.approvedVolume.length > 0) {
      const parts = daily.approvedVolume.map((v) => {
        const major = ((v.amountMinor || 0) / 100).toLocaleString('en-US', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });
        return `${v.currency} ${major}`;
      });
      this.todayVolumeSummary.set(parts.join(' / '));
    } else {
      this.todayVolumeSummary.set('NPR 0.00');
    }
  }

  private processSummary(summary: SummaryReportResponse): void {
    this.activeCards.set(summary.activeCards || 0);
    this.openDisputes.set(summary.openDisputes || 0);

    if (summary.series && summary.series.length > 0) {
      const labels = summary.series.map((s) => s.date || '');
      const attempts = summary.series.map((s) => (s.approved || 0) + (s.declined || 0));
      const approved = summary.series.map((s) => s.approved || 0);

      this.chartData.set({
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
        ],
      });
    } else {
      this.chartData.set(null);
    }
  }

  openTxDetail(transactionId: string): void {
    this.dialog.open(TransactionDetailDialogComponent, {
      data: { transactionId },
      width: '680px',
      maxHeight: '90vh',
    });
  }
}
