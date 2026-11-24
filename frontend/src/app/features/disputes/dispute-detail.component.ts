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
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NotificationService } from '../../core/notify/notification.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { CopyButtonComponent } from '../../shared/ui/copy-button.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import {
  RequestEvidenceDialogComponent,
  RequestEvidenceDialogData,
} from './dialogs/request-evidence-dialog.component';
import {
  ResolveDisputeDialogComponent,
  ResolveDisputeDialogData,
} from './dialogs/resolve-dispute-dialog.component';
import {
  SubmitEvidenceDialogComponent,
  SubmitEvidenceDialogData,
} from './dialogs/submit-evidence-dialog.component';
import { DisputeDetail, DisputesApiService } from './disputes-api.service';

@Component({
  selector: 'mc-dispute-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatDialogModule,
    MatTooltipModule,
    PageHeaderComponent,
    StatusChipComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    CopyButtonComponent,
    MoneyPipe,
  ],
  template: `
    @if (loading()) {
      <div class="skeleton-wrap">
        <mc-loading-skeleton [rows]="8"></mc-loading-skeleton>
      </div>
    } @else if (error()) {
      <mc-error-state
        title="Failed to load dispute"
        [message]="error() || ''"
        (retry)="loadDispute()"
      ></mc-error-state>
    } @else {
      @if (dispute(); as d) {
        <mc-page-header
          [title]="'Dispute ' + (d.id?.slice(0, 8) || '')"
          subtitle="Cardholder transaction challenge workflow"
        >
          <div actions class="header-actions">
            <a mat-stroked-button routerLink="/disputes">
              <mat-icon>arrow_back</mat-icon>
              <span>Back to Disputes</span>
            </a>
          </div>
        </mc-page-header>

        <div class="detail-grid">
          <!-- Left Column: Dispute Facts & Transaction -->
          <div class="facts-column">
            <!-- Dispute Overview Card -->
            <mat-card class="detail-card">
              <div class="card-header-status">
                <div>
                  <span class="eyebrow">Dispute Amount</span>
                  <div class="amount-title tabular-nums">
                    {{ d.amountMinor | money: d.currency }}
                  </div>
                </div>
                <mc-status-chip [status]="d.status || 'OPENED'"></mc-status-chip>
              </div>

              <mat-divider></mat-divider>

              <div class="facts-list">
                <div class="fact-row">
                  <span class="fact-label">Reason</span>
                  <span class="fact-val font-semibold">{{ formatReason(d.reason) }}</span>
                </div>

                <div class="fact-row">
                  <span class="fact-label">Description</span>
                  <span class="fact-val">{{ d.description || 'No description provided' }}</span>
                </div>

                <div class="fact-row">
                  <span class="fact-label">Opened By</span>
                  <span class="fact-val">{{ d.openedBy }}</span>
                </div>

                <div class="fact-row">
                  <span class="fact-label">Opened At</span>
                  <span class="fact-val tabular-nums">
                    {{ d.openedAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                  </span>
                </div>

                @if (d.evidenceDueAt) {
                  <div class="fact-row">
                    <span class="fact-label">Evidence Due</span>
                    <div class="fact-val">
                      <span class="due-badge {{ isOverdue(d.evidenceDueAt) ? 'overdue' : '' }}">
                        {{ d.evidenceDueAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                        @if (isOverdue(d.evidenceDueAt)) {
                          <strong>(OVERDUE)</strong>
                        }
                      </span>
                    </div>
                  </div>
                }

                @if (d.resolvedAt) {
                  <div class="fact-row">
                    <span class="fact-label">Resolved At</span>
                    <span class="fact-val tabular-nums">
                      {{ d.resolvedAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                    </span>
                  </div>

                  <div class="fact-row">
                    <span class="fact-label">Resolved By</span>
                    <span class="fact-val">{{ d.resolvedBy }}</span>
                  </div>

                  @if (d.resolutionNote) {
                    <div class="fact-row full">
                      <span class="fact-label">Resolution Note</span>
                      <span class="fact-val resolution-box">{{ d.resolutionNote }}</span>
                    </div>
                  }
                }
              </div>
            </mat-card>

            <!-- Linked Transaction Card -->
            <mat-card class="detail-card">
              <mat-card-header>
                <mat-card-title>Linked Transaction</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="facts-list">
                  <div class="fact-row">
                    <span class="fact-label">Transaction ID</span>
                    <div class="fact-val copy-cell mono">
                      <span>{{ d.transactionId }}</span>
                      @if (d.transactionId) {
                        <mc-copy-button [value]="d.transactionId"></mc-copy-button>
                      }
                    </div>
                  </div>

                  <div class="fact-row">
                    <span class="fact-label">Card</span>
                    <span class="fact-val">
                      <a [routerLink]="['/cards', d.cardId]" class="card-link">
                        •••• {{ d.cardLast4 }}
                      </a>
                    </span>
                  </div>

                  @if (d.transaction) {
                    <div class="fact-row">
                      <span class="fact-label">Merchant</span>
                      <span class="fact-val">
                        {{ d.transaction.merchantName }} (MCC {{ d.transaction.mcc }})
                      </span>
                    </div>

                    <div class="fact-row">
                      <span class="fact-label">Transaction Date</span>
                      <span class="fact-val tabular-nums">
                        {{ d.transaction.createdAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                      </span>
                    </div>
                  }
                </div>
              </mat-card-content>
            </mat-card>
          </div>

          <!-- Right Column: Action Panel & Timeline -->
          <div class="actions-column">
            <!-- Action Panel: Strictly rendering allowedActions -->
            <mat-card class="action-panel-card">
              <mat-card-header>
                <mat-card-title>Workflow Actions</mat-card-title>
                <mat-card-subtitle>
                  Available transitions based on current dispute status
                </mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @if (d.allowedActions && d.allowedActions.length > 0) {
                  <div class="action-buttons-group">
                    @for (act of d.allowedActions; track act) {
                      @switch (act) {
                        @case ('REQUEST_EVIDENCE') {
                          <button
                            mat-flat-button
                            color="primary"
                            (click)="openRequestEvidenceDialog(d.id!)"
                          >
                            <mat-icon>help_outline</mat-icon>
                            <span>Request Evidence</span>
                          </button>
                        }
                        @case ('SUBMIT_EVIDENCE') {
                          <button
                            mat-flat-button
                            color="accent"
                            (click)="openSubmitEvidenceDialog(d.id!)"
                          >
                            <mat-icon>upload_file</mat-icon>
                            <span>Submit Evidence</span>
                          </button>
                        }
                        @case ('RESOLVE_WON') {
                          <button
                            mat-flat-button
                            class="won-btn"
                            (click)="
                              openResolveDialog(
                                d.id!,
                                'WON',
                                d.amountMinor || 0,
                                d.currency || 'NPR'
                              )
                            "
                          >
                            <mat-icon>check_circle</mat-icon>
                            <span>Resolve WON (Credit Card)</span>
                          </button>
                        }
                        @case ('RESOLVE_LOST') {
                          <button
                            mat-stroked-button
                            color="warn"
                            (click)="
                              openResolveDialog(
                                d.id!,
                                'LOST',
                                d.amountMinor || 0,
                                d.currency || 'NPR'
                              )
                            "
                          >
                            <mat-icon>cancel</mat-icon>
                            <span>Resolve LOST (No Credit)</span>
                          </button>
                        }
                      }
                    }
                  </div>
                } @else {
                  <div class="no-actions-box">
                    <mat-icon>lock</mat-icon>
                    <span>This dispute is in final state. No further actions available.</span>
                  </div>
                }
              </mat-card-content>
            </mat-card>

            <!-- Event Timeline -->
            <mat-card class="timeline-card">
              <mat-card-header>
                <mat-card-title>Dispute Timeline</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                @if (d.events && d.events.length > 0) {
                  <div class="timeline">
                    @for (evt of d.events; track evt.id) {
                      <div class="timeline-item">
                        <div class="timeline-badge"></div>
                        <div class="timeline-content">
                          <div class="timeline-header">
                            <span class="status-transition">
                              @if (evt.fromStatus) {
                                <mc-status-chip [status]="evt.fromStatus"></mc-status-chip>
                                <mat-icon class="arrow-icon">arrow_forward</mat-icon>
                              }
                              <mc-status-chip [status]="evt.toStatus || ''"></mc-status-chip>
                            </span>
                            <span class="timeline-time tabular-nums text-muted">
                              {{ evt.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                            </span>
                          </div>

                          <div class="timeline-actor">
                            <mat-icon>person</mat-icon>
                            <span>{{ evt.actor || 'System' }}</span>
                          </div>

                          @if (evt.note) {
                            <div class="timeline-note">"{{ evt.note }}"</div>
                          }
                        </div>
                      </div>
                    }
                  </div>
                } @else {
                  <p class="text-muted">No timeline events recorded yet.</p>
                }
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      }
    }
  `,
  styles: [
    `
      .skeleton-wrap {
        padding: 24px;
        background: white;
        border-radius: 8px;
      }
      .header-actions {
        display: flex;
        align-items: center;
      }

      .detail-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 24px;
      }
      @media (max-width: 960px) {
        .detail-grid {
          grid-template-columns: 1fr;
        }
      }

      .detail-card {
        border: 1px solid #e2e8f0;
        margin-bottom: 20px;
        padding: 20px;
      }
      .card-header-status {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding-bottom: 16px;
      }
      .eyebrow {
        font-size: 11px;
        text-transform: uppercase;
        color: #64748b;
        font-weight: 600;
        letter-spacing: 0.5px;
      }
      .amount-title {
        font-size: 26px;
        font-weight: 700;
        color: #0f172a;
      }

      .facts-list {
        display: flex;
        flex-direction: column;
        gap: 12px;
        padding-top: 16px;
      }
      .fact-row {
        display: flex;
        justify-content: space-between;
        font-size: 13px;
      }
      .fact-row.full {
        flex-direction: column;
        gap: 6px;
      }
      .fact-label {
        color: #64748b;
        font-weight: 500;
      }
      .fact-val {
        color: #0f172a;
      }
      .resolution-box {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        padding: 8px 12px;
        font-style: italic;
      }

      .due-badge {
        font-size: 12px;
        padding: 2px 6px;
        border-radius: 4px;
        background: #f1f5f9;
      }
      .due-badge.overdue {
        background: #fee2e2;
        color: #991b1b;
        font-weight: 600;
      }

      .card-link {
        color: var(--mc-color-primary, #2563eb);
        text-decoration: none;
        font-weight: 600;
      }
      .copy-cell {
        display: flex;
        align-items: center;
        gap: 6px;
      }

      /* Actions */
      .action-panel-card {
        border: 1px solid #e2e8f0;
        margin-bottom: 20px;
      }
      .action-buttons-group {
        display: flex;
        flex-direction: column;
        gap: 12px;
        padding-top: 12px;
      }
      .action-buttons-group button {
        height: 42px;
        font-weight: 600;
      }
      .won-btn {
        background: #16a34a !important;
        color: white !important;
      }
      .no-actions-box {
        display: flex;
        align-items: center;
        gap: 8px;
        background: #f8fafc;
        padding: 16px;
        border-radius: 6px;
        color: #64748b;
        font-size: 13px;
        margin-top: 8px;
      }

      /* Timeline */
      .timeline-card {
        border: 1px solid #e2e8f0;
      }
      .timeline {
        position: relative;
        padding-left: 20px;
        border-left: 2px solid #e2e8f0;
        display: flex;
        flex-direction: column;
        gap: 20px;
        margin-top: 12px;
      }
      .timeline-item {
        position: relative;
      }
      .timeline-badge {
        position: absolute;
        left: -27px;
        top: 4px;
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: #2563eb;
        border: 2px solid white;
      }
      .timeline-content {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .timeline-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .status-transition {
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .arrow-icon {
        font-size: 14px;
        width: 14px;
        height: 14px;
        color: #94a3b8;
      }
      .timeline-time {
        font-size: 12px;
      }
      .timeline-actor {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: #475569;
        font-weight: 500;
      }
      .timeline-actor mat-icon {
        font-size: 14px;
        width: 14px;
        height: 14px;
      }
      .timeline-note {
        background: #f8fafc;
        border-left: 3px solid #cbd5e1;
        padding: 6px 10px;
        font-size: 12px;
        color: #334155;
        margin-top: 4px;
        border-radius: 0 4px 4px 0;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisputeDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly disputesApi = inject(DisputesApiService);
  private readonly notify = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly dispute = signal<DisputeDetail | null>(null);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDispute();
  }

  loadDispute(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Dispute ID missing');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.disputesApi
      .getDispute(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (d) => {
          this.dispute.set(d);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.detail || 'Failed to load dispute details');
        },
      });
  }

  formatReason(reason?: string): string {
    if (!reason) return '-';
    return reason
      .toLowerCase()
      .split('_')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(' ');
  }

  isOverdue(dueAt?: string): boolean {
    if (!dueAt) return false;
    return new Date(dueAt).getTime() < Date.now();
  }

  openRequestEvidenceDialog(disputeId: string): void {
    const ref = this.dialog.open(RequestEvidenceDialogComponent, {
      data: { disputeId } as RequestEvidenceDialogData,
      width: '480px',
    });
    ref.afterClosed().subscribe((res) => {
      if (res) {
        this.dispute.set(res);
      }
    });
  }

  openSubmitEvidenceDialog(disputeId: string): void {
    const ref = this.dialog.open(SubmitEvidenceDialogComponent, {
      data: { disputeId } as SubmitEvidenceDialogData,
      width: '480px',
    });
    ref.afterClosed().subscribe((res) => {
      if (res) {
        this.dispute.set(res);
      }
    });
  }

  openResolveDialog(
    disputeId: string,
    outcome: 'WON' | 'LOST',
    amountMinor: number,
    currency: string,
  ): void {
    const ref = this.dialog.open(ResolveDisputeDialogComponent, {
      data: {
        disputeId,
        outcome,
        amountMinor,
        currency,
      } as ResolveDisputeDialogData,
      width: '500px',
    });
    ref.afterClosed().subscribe((res) => {
      if (res) {
        this.dispute.set(res);
      }
    });
  }
}
