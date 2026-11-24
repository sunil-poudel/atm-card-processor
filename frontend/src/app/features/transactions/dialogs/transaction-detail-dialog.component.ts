import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CopyButtonComponent } from '../../../shared/ui/copy-button.component';
import { LoadingSkeletonComponent } from '../../../shared/ui/loading-skeleton.component';
import { StatusChipComponent } from '../../../shared/ui/status-chip.component';
import { TransactionDetail, TransactionsApiService } from '../transactions-api.service';
import { OpenDisputeDialogComponent } from './open-dispute-dialog.component';

export interface TransactionDetailDialogData {
  transactionId: string;
}

@Component({
  selector: 'mc-transaction-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    MatDividerModule,
    StatusChipComponent,
    CopyButtonComponent,
    LoadingSkeletonComponent,
    MoneyPipe,
  ],
  template: `
    @if (loading()) {
      <div class="dialog-skeleton">
        <mc-loading-skeleton [rows]="6"></mc-loading-skeleton>
      </div>
    } @else {
      @if (tx(); as t) {
        <div class="tx-dialog-header">
          <div class="header-left">
            <div class="amount-large tabular-nums">
              {{ t.amountMinor | money: t.currency }}
            </div>
            <div class="status-wrap">
              <mc-status-chip [status]="t.status || 'UNKNOWN'"></mc-status-chip>
              <span class="code-badge">Code: {{ t.responseCode }}</span>
              @if (t.flagged) {
                <span class="flagged-badge">
                  <mat-icon>flag</mat-icon>
                  <span>Flagged</span>
                </span>
              }
            </div>
          </div>

          <button mat-icon-button mat-dialog-close aria-label="Close dialog">
            <mat-icon>close</mat-icon>
          </button>
        </div>

        <mat-dialog-content class="tx-dialog-body">
          <!-- Overview Grid -->
          <div class="info-grid">
            <div class="info-cell">
              <span class="cell-label">Timestamp (Asia/Kathmandu)</span>
              <span class="cell-value">{{
                t.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545'
              }}</span>
            </div>

            <div class="info-cell">
              <span class="cell-label">Merchant</span>
              <span class="cell-value font-medium">{{ t.merchantName }} (MCC {{ t.mcc }})</span>
            </div>

            <div class="info-cell">
              <span class="cell-label">Card</span>
              <span class="cell-value">
                <a
                  [routerLink]="['/cards', t.cardId]"
                  (click)="dialogRef.close()"
                  class="card-link"
                >
                  •••• {{ t.cardLast4 }} ({{ t.holderName }})
                </a>
              </span>
            </div>

            <div class="info-cell">
              <span class="cell-label">Risk Score</span>
              <span class="cell-value">
                <span class="score-badge {{ getScoreClass(t.riskScore ?? 0) }}">
                  {{ t.riskScore ?? 0 }}/100
                </span>
              </span>
            </div>

            <div class="info-cell">
              <span class="cell-label">Processing Time</span>
              <span class="cell-value tabular-nums">{{ t.processingMillis }} ms</span>
            </div>

            <div class="info-cell">
              <span class="cell-label">Balance After</span>
              <span class="cell-value tabular-nums font-medium">
                {{
                  t.balanceAfterMinor !== null && t.balanceAfterMinor !== undefined
                    ? (t.balanceAfterMinor | money: t.currency)
                    : '-'
                }}
              </span>
            </div>

            <div class="info-cell full-span">
              <span class="cell-label">Idempotency Key</span>
              <div class="copy-cell mono">
                <span>{{ t.idempotencyKey || '-' }}</span>
                @if (t.idempotencyKey) {
                  <mc-copy-button [value]="t.idempotencyKey"></mc-copy-button>
                }
              </div>
            </div>

            @if (t.declineReason) {
              <div class="info-cell full-span decline-box">
                <span class="cell-label text-danger">Decline Reason:</span>
                <span class="cell-value font-semibold text-danger">{{ t.declineReason }}</span>
              </div>
            }
          </div>

          <!-- Dispute Information / Action -->
          <div class="dispute-section">
            @if (t.dispute) {
              <div class="dispute-box">
                <div class="dispute-box-header">
                  <span class="font-medium">Active Dispute</span>
                  <mc-status-chip [status]="t.dispute.status || 'OPEN'"></mc-status-chip>
                </div>
                <p class="dispute-info-text">
                  Dispute ID: <strong>{{ t.dispute.id }}</strong>
                </p>
                <a
                  mat-stroked-button
                  color="primary"
                  [routerLink]="['/disputes', t.dispute.id]"
                  (click)="dialogRef.close()"
                >
                  <span>View Dispute Record</span>
                  <mat-icon>chevron_right</mat-icon>
                </a>
              </div>
            } @else if (t.status === 'APPROVED' && canOpenDispute()) {
              <div class="open-dispute-card">
                <div>
                  <div class="font-medium">Need to contest this transaction?</div>
                  <div class="text-muted text-sm">
                    Disputes can be filed by officers for unauthorized or incorrect charges.
                  </div>
                </div>
                <button mat-stroked-button color="warn" (click)="openDispute(t)">
                  <mat-icon>gavel</mat-icon>
                  <span>Open Dispute</span>
                </button>
              </div>
            }
          </div>

          <!-- Fraud Rule Hits Table -->
          @if (t.ruleHits && t.ruleHits.length > 0) {
            <div class="rule-hits-section">
              <h4 class="section-title">Triggered Fraud Rules ({{ t.ruleHits.length }})</h4>
              <table mat-table [dataSource]="t.ruleHits" class="rules-table">
                <ng-container matColumnDef="ruleName">
                  <th mat-header-cell *matHeaderCellDef>Rule Name</th>
                  <td mat-cell *matCellDef="let r" class="font-medium">{{ r.ruleName }}</td>
                </ng-container>

                <ng-container matColumnDef="action">
                  <th mat-header-cell *matHeaderCellDef>Action</th>
                  <td mat-cell *matCellDef="let r">
                    <span
                      class="action-tag {{ r.action === 'DECLINE' ? 'tag-decline' : 'tag-flag' }}"
                    >
                      {{ r.action }}
                    </span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="points">
                  <th mat-header-cell *matHeaderCellDef class="amount-cell">Points</th>
                  <td mat-cell *matCellDef="let r" class="amount-cell tabular-nums font-semibold">
                    +{{ r.points }}
                  </td>
                </ng-container>

                <ng-container matColumnDef="detail">
                  <th mat-header-cell *matHeaderCellDef>Details</th>
                  <td mat-cell *matCellDef="let r" class="text-muted">{{ r.detail || '-' }}</td>
                </ng-container>

                <tr
                  mat-header-row
                  *matHeaderRowDef="['ruleName', 'action', 'points', 'detail']"
                ></tr>
                <tr
                  mat-row
                  *matRowDef="let row; columns: ['ruleName', 'action', 'points', 'detail']"
                ></tr>
              </table>
            </div>
          }
        </mat-dialog-content>
      }
    }
  `,
  styles: [
    `
      .dialog-skeleton {
        padding: 24px;
        min-width: 540px;
      }
      .tx-dialog-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 24px 24px 16px 24px;
        border-bottom: 1px solid #e2e8f0;
      }
      .amount-large {
        font-size: 28px;
        font-weight: 700;
        color: #0f172a;
      }
      .status-wrap {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 8px;
      }
      .code-badge {
        font-family: var(--mc-font-mono);
        font-size: 12px;
        font-weight: 600;
        background: #f1f5f9;
        padding: 2px 6px;
        border-radius: 4px;
      }
      .flagged-badge {
        display: inline-flex;
        align-items: center;
        gap: 2px;
        background: #fef3c7;
        color: #92400e;
        border: 1px solid #fde68a;
        font-size: 11px;
        font-weight: 700;
        padding: 1px 6px;
        border-radius: 9999px;
      }
      .flagged-badge mat-icon {
        font-size: 14px;
        width: 14px;
        height: 14px;
      }
      .tx-dialog-body {
        padding: 20px 24px;
        max-height: 80vh;
      }
      .info-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
        margin-bottom: 20px;
      }
      .full-span {
        grid-column: 1 / -1;
      }
      .info-cell {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .cell-label {
        font-size: 12px;
        color: #64748b;
      }
      .cell-value {
        font-size: 14px;
        color: #0f172a;
      }
      .copy-cell {
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        padding: 4px 10px;
        font-size: 13px;
      }
      .decline-box {
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 6px;
        padding: 8px 12px;
      }
      .card-link {
        color: #0284c7;
        text-decoration: none;
        font-weight: 600;
      }
      .card-link:hover {
        text-decoration: underline;
      }
      .score-badge {
        padding: 2px 8px;
        border-radius: 4px;
        font-weight: 700;
        font-size: 12px;
      }
      .score-low {
        background: #f0fdf4;
        color: #16a34a;
      }
      .score-med {
        background: #fffbeb;
        color: #d97706;
      }
      .score-high {
        background: #fef2f2;
        color: #dc2626;
      }
      .dispute-section {
        margin-bottom: 20px;
      }
      .dispute-box {
        background: #f8fafc;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 16px;
      }
      .dispute-box-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 6px;
      }
      .dispute-info-text {
        font-size: 13px;
        color: #475569;
        margin: 0 0 12px 0;
      }
      .open-dispute-card {
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: #f8fafc;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        padding: 16px;
      }
      .text-sm {
        font-size: 12px;
      }
      .section-title {
        font-size: 14px;
        font-weight: 600;
        margin: 16px 0 8px 0;
        color: #0f172a;
      }
      .rules-table {
        width: 100%;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
      }
      .action-tag {
        font-size: 11px;
        font-weight: 700;
        padding: 2px 6px;
        border-radius: 4px;
      }
      .tag-decline {
        background: #fee2e2;
        color: #dc2626;
      }
      .tag-flag {
        background: #fef3c7;
        color: #92400e;
      }
      .amount-cell {
        text-align: right;
      }
      .font-medium {
        font-weight: 500;
      }
      .font-semibold {
        font-weight: 600;
      }
      .text-danger {
        color: #dc2626;
      }
      .text-muted {
        color: #64748b;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionDetailDialogComponent implements OnInit {
  private readonly transactionsApi = inject(TransactionsApiService);
  private readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  readonly dialogRef = inject(MatDialogRef<TransactionDetailDialogComponent>);
  readonly data = inject<TransactionDetailDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly tx = signal<TransactionDetail | null>(null);

  readonly canOpenDispute = () => this.authStore.hasRole(['ADMIN', 'DISPUTE_OFFICER']);

  ngOnInit(): void {
    if (this.data.transactionId) {
      this.loadTransaction();
    }
  }

  loadTransaction(): void {
    this.loading.set(true);
    this.transactionsApi.getTransaction(this.data.transactionId).subscribe({
      next: (detail) => {
        this.loading.set(false);
        this.tx.set(detail);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  openDispute(transaction: TransactionDetail): void {
    const dialogRef = this.dialog.open(OpenDisputeDialogComponent, {
      data: { transaction },
      width: '500px',
    });

    dialogRef.afterClosed().subscribe((createdDispute) => {
      if (createdDispute) {
        this.loadTransaction();
      }
    });
  }

  getScoreClass(score: number): string {
    if (score >= 80) return 'score-high';
    if (score >= 50) return 'score-med';
    return 'score-low';
  }
}
