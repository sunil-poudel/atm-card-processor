import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { NotificationService } from '../../core/notify/notification.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/ui/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { MaskedPanComponent } from '../../shared/ui/masked-pan.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import {
  CardResponse,
  CardsApiService,
  LedgerEntryResponse,
  TransactionSummary,
} from './cards-api.service';
import { BlockCardDialogComponent } from './dialogs/block-card-dialog.component';
import { EditBlockedMccsDialogComponent } from './dialogs/edit-blocked-mccs-dialog.component';
import { EditLimitsDialogComponent } from './dialogs/edit-limits-dialog.component';
import { TopUpCardDialogComponent } from './dialogs/topup-card-dialog.component';

@Component({
  selector: 'mc-card-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatTabsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatChipsModule,
    MatDividerModule,
    MatDialogModule,
    PageHeaderComponent,
    StatusChipComponent,
    MaskedPanComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
    MoneyPipe,
  ],
  template: `
    <div class="card-detail-page">
      @if (loadingCard()) {
        <mc-loading-skeleton [rows]="4"></mc-loading-skeleton>
      } @else if (errorCard()) {
        <mc-error-state
          title="Card Not Found"
          [message]="errorCard()!"
          (retry)="loadCard()"
        ></mc-error-state>
      } @else {
        @if (card(); as c) {
          <!-- Breadcrumb & Top Bar -->
          <mc-page-header
            [title]="c.holderName || 'Card Details'"
            [subtitle]="'Issued ' + (c.createdAt | date: 'medium' : '+0545')"
          >
            <div actions class="header-actions">
              <a mat-stroked-button routerLink="/cards">
                <mat-icon>arrow_back</mat-icon>
                <span>Back to Cards</span>
              </a>

              @if (isAdmin()) {
                @if (c.status === 'INACTIVE') {
                  <button mat-flat-button color="primary" (click)="activateCard()">
                    <mat-icon>check_circle</mat-icon>
                    <span>Activate</span>
                  </button>
                }

                @if (c.status === 'ACTIVE' || c.status === 'INACTIVE') {
                  <button mat-stroked-button color="warn" (click)="openBlockDialog()">
                    <mat-icon>block</mat-icon>
                    <span>Block</span>
                  </button>
                }

                @if (c.status === 'BLOCKED') {
                  <button mat-flat-button color="primary" (click)="unblockCard()">
                    <mat-icon>lock_open</mat-icon>
                    <span>Unblock</span>
                  </button>
                }

                @if (c.status !== 'CLOSED') {
                  <button mat-flat-button color="primary" (click)="openTopUpDialog()">
                    <mat-icon>add_circle</mat-icon>
                    <span>Top Up</span>
                  </button>

                  <button
                    mat-stroked-button
                    color="warn"
                    [disabled]="(c.balanceMinor || 0) !== 0"
                    [matTooltip]="
                      (c.balanceMinor || 0) !== 0 ? 'Card balance must be 0 to close' : ''
                    "
                    (click)="closeCard()"
                  >
                    <mat-icon>cancel</mat-icon>
                    <span>Close Card</span>
                  </button>
                }
              }
            </div>
          </mc-page-header>

          <!-- Card Key Metrics / Summary Hero -->
          <div class="summary-hero">
            <div class="hero-left">
              <div class="pan-row">
                <mc-masked-pan [pan]="c.maskedPan || ''"></mc-masked-pan>
                <mc-status-chip [status]="c.status || ''"></mc-status-chip>
              </div>
              <div class="holder-name">{{ c.holderName }}</div>
              <div class="expiry-row">
                <span>Expires:</span>
                <strong [class.text-danger]="c.expired">
                  {{ formatExpiry(c.expiryMonth, c.expiryYear) }}
                </strong>
                @if (c.expired) {
                  <span class="expired-tag">EXPIRED</span>
                }
              </div>
            </div>

            <div class="hero-right">
              <div class="balance-label">Current Balance</div>
              <div class="balance-amount tabular-nums">
                {{ c.balanceMinor | money: c.currency }}
              </div>
              <div class="currency-indicator">{{ c.currency }} (ISO-4217)</div>
            </div>
          </div>

          <!-- Detail Tabs -->
          <div class="tabs-card">
            <mat-tab-group>
              <!-- Tab 1: Overview -->
              <mat-tab label="Overview">
                <div class="tab-content">
                  <div class="overview-grid">
                    <!-- Limits & Controls -->
                    <div class="overview-section">
                      <div class="section-header">
                        <h3>Limits & Controls</h3>
                        @if (isAdmin() && c.status !== 'CLOSED') {
                          <button mat-button color="primary" (click)="openEditLimitsDialog()">
                            <mat-icon>edit</mat-icon>
                            <span>Edit Limits</span>
                          </button>
                        }
                      </div>

                      <div class="property-list">
                        <div class="property-row">
                          <span class="prop-label">Per-Transaction Limit:</span>
                          <strong class="tabular-nums">{{
                            c.perTransactionLimitMinor | money: c.currency
                          }}</strong>
                        </div>
                        <div class="property-row">
                          <span class="prop-label">Daily Limit:</span>
                          <strong class="tabular-nums">{{
                            c.dailyLimitMinor | money: c.currency
                          }}</strong>
                        </div>
                      </div>
                    </div>

                    <!-- Blocked MCCs -->
                    <div class="overview-section">
                      <div class="section-header">
                        <h3>Blocked MCCs</h3>
                        @if (isAdmin() && c.status !== 'CLOSED') {
                          <button mat-button color="primary" (click)="openEditBlockedMccsDialog()">
                            <mat-icon>edit</mat-icon>
                            <span>Edit MCCs</span>
                          </button>
                        }
                      </div>

                      <div class="mcc-container">
                        @if (c.blockedMccs && c.blockedMccs.length > 0) {
                          <div class="chip-list">
                            @for (mcc of c.blockedMccs; track mcc) {
                              <span class="mcc-chip">{{ mcc }}</span>
                            }
                          </div>
                        } @else {
                          <span class="text-muted">No MCCs blocked on this card.</span>
                        }
                      </div>
                    </div>

                    <!-- Lifecycle Timestamps -->
                    <div class="overview-section full-span">
                      <div class="section-header">
                        <h3>Lifecycle & Audit Details</h3>
                      </div>

                      <div class="property-grid">
                        <div class="prop-col">
                          <span class="prop-label">Created At:</span>
                          <span>{{ c.createdAt | date: 'medium' : '+0545' }}</span>
                        </div>
                        <div class="prop-col">
                          <span class="prop-label">Activated At:</span>
                          <span>{{
                            c.activatedAt ? (c.activatedAt | date: 'medium' : '+0545') : 'Never'
                          }}</span>
                        </div>
                        <div class="prop-col">
                          <span class="prop-label">Blocked At:</span>
                          <span>{{
                            c.blockedAt ? (c.blockedAt | date: 'medium' : '+0545') : 'Never'
                          }}</span>
                        </div>
                        <div class="prop-col">
                          <span class="prop-label">Closed At:</span>
                          <span>{{
                            c.closedAt ? (c.closedAt | date: 'medium' : '+0545') : 'Never'
                          }}</span>
                        </div>
                      </div>

                      @if (c.blockReason) {
                        <div class="block-reason-box">
                          <strong>Block Reason:</strong> {{ c.blockReason }}
                        </div>
                      }
                    </div>
                  </div>
                </div>
              </mat-tab>

              <!-- Tab 2: Ledger -->
              <mat-tab label="Ledger">
                <div class="tab-content">
                  @if (loadingLedger()) {
                    <mc-loading-skeleton [rows]="4"></mc-loading-skeleton>
                  } @else if (ledgerEntries().length === 0) {
                    <mc-empty-state
                      icon="receipt"
                      title="No ledger entries"
                      message="No balance mutations recorded on this card yet."
                    ></mc-empty-state>
                  } @else {
                    <div class="table-responsive">
                      <table mat-table [dataSource]="ledgerEntries()" class="ledger-table">
                        <!-- Type Column -->
                        <ng-container matColumnDef="type">
                          <th mat-header-cell *matHeaderCellDef>Type</th>
                          <td mat-cell *matCellDef="let entry">
                            <mc-status-chip [status]="entry.type"></mc-status-chip>
                          </td>
                        </ng-container>

                        <!-- Amount Column -->
                        <ng-container matColumnDef="amount">
                          <th mat-header-cell *matHeaderCellDef class="amount-cell">Amount</th>
                          <td
                            mat-cell
                            *matCellDef="let entry"
                            class="amount-cell tabular-nums font-semibold"
                            [class.text-success]="
                              entry.type === 'TOPUP' || entry.type === 'DISPUTE_CREDIT'
                            "
                            [class.text-danger]="entry.type === 'PURCHASE'"
                          >
                            {{ entry.amountMinor | money: c.currency : true }}
                          </td>
                        </ng-container>

                        <!-- Balance After Column -->
                        <ng-container matColumnDef="balanceAfter">
                          <th mat-header-cell *matHeaderCellDef class="amount-cell">
                            Balance After
                          </th>
                          <td mat-cell *matCellDef="let entry" class="amount-cell tabular-nums">
                            {{ entry.balanceAfterMinor | money: c.currency }}
                          </td>
                        </ng-container>

                        <!-- Description Column -->
                        <ng-container matColumnDef="description">
                          <th mat-header-cell *matHeaderCellDef>Description</th>
                          <td mat-cell *matCellDef="let entry">
                            {{ entry.description || '-' }}
                            @if (entry.transactionId) {
                              <div class="ref-link">
                                <a
                                  [routerLink]="['/transactions']"
                                  [queryParams]="{ q: entry.transactionId }"
                                >
                                  View Transaction
                                </a>
                              </div>
                            }
                            @if (entry.disputeId) {
                              <div class="ref-link">
                                <a [routerLink]="['/disputes', entry.disputeId]"> View Dispute </a>
                              </div>
                            }
                          </td>
                        </ng-container>

                        <!-- Created By Column -->
                        <ng-container matColumnDef="createdBy">
                          <th mat-header-cell *matHeaderCellDef>Actor</th>
                          <td mat-cell *matCellDef="let entry" class="text-muted">
                            {{ entry.createdBy || '-' }}
                          </td>
                        </ng-container>

                        <!-- Occurred At Column -->
                        <ng-container matColumnDef="occurredAt">
                          <th mat-header-cell *matHeaderCellDef>Timestamp</th>
                          <td mat-cell *matCellDef="let entry" class="text-muted">
                            {{ entry.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                          </td>
                        </ng-container>

                        <tr mat-header-row *matHeaderRowDef="ledgerColumns"></tr>
                        <tr mat-row *matRowDef="let row; columns: ledgerColumns"></tr>
                      </table>
                    </div>

                    <mat-paginator
                      [length]="ledgerTotal()"
                      [pageSize]="ledgerPageSize()"
                      [pageIndex]="ledgerPageIndex()"
                      [pageSizeOptions]="[10, 20, 50]"
                      (page)="onLedgerPageChange($event)"
                      showFirstLastButtons
                    ></mat-paginator>
                  }
                </div>
              </mat-tab>

              <!-- Tab 3: Transactions -->
              <mat-tab label="Transactions">
                <div class="tab-content">
                  @if (loadingTransactions()) {
                    <mc-loading-skeleton [rows]="4"></mc-loading-skeleton>
                  } @else if (transactions().length === 0) {
                    <mc-empty-state
                      icon="receipt_long"
                      title="No transactions"
                      message="No transaction attempts have been processed on this card."
                    ></mc-empty-state>
                  } @else {
                    <div class="table-responsive">
                      <table mat-table [dataSource]="transactions()" class="tx-table">
                        <!-- Time Column -->
                        <ng-container matColumnDef="time">
                          <th mat-header-cell *matHeaderCellDef>Time</th>
                          <td mat-cell *matCellDef="let tx" class="text-muted">
                            {{ tx.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                          </td>
                        </ng-container>

                        <!-- Merchant Column -->
                        <ng-container matColumnDef="merchant">
                          <th mat-header-cell *matHeaderCellDef>Merchant</th>
                          <td mat-cell *matCellDef="let tx">
                            <div class="font-medium">{{ tx.merchantName }}</div>
                            <div class="mcc-label text-muted">MCC: {{ tx.mcc }}</div>
                          </td>
                        </ng-container>

                        <!-- Amount Column -->
                        <ng-container matColumnDef="amount">
                          <th mat-header-cell *matHeaderCellDef class="amount-cell">Amount</th>
                          <td
                            mat-cell
                            *matCellDef="let tx"
                            class="amount-cell tabular-nums font-semibold"
                          >
                            {{ tx.amountMinor | money: tx.currency }}
                          </td>
                        </ng-container>

                        <!-- Status Column -->
                        <ng-container matColumnDef="status">
                          <th mat-header-cell *matHeaderCellDef>Status</th>
                          <td mat-cell *matCellDef="let tx">
                            <mc-status-chip [status]="tx.status"></mc-status-chip>
                          </td>
                        </ng-container>

                        <!-- Response Code Column -->
                        <ng-container matColumnDef="responseCode">
                          <th mat-header-cell *matHeaderCellDef>Code</th>
                          <td mat-cell *matCellDef="let tx">
                            <span class="code-badge">{{ tx.responseCode }}</span>
                          </td>
                        </ng-container>

                        <!-- Flagged Column -->
                        <ng-container matColumnDef="flagged">
                          <th mat-header-cell *matHeaderCellDef>Flagged</th>
                          <td mat-cell *matCellDef="let tx">
                            @if (tx.flagged) {
                              <mat-icon color="warn" matTooltip="Flagged by fraud engine"
                                >flag</mat-icon
                              >
                            } @else {
                              <span class="text-muted">-</span>
                            }
                          </td>
                        </ng-container>

                        <tr mat-header-row *matHeaderRowDef="txColumns"></tr>
                        <tr mat-row *matRowDef="let row; columns: txColumns"></tr>
                      </table>
                    </div>

                    <mat-paginator
                      [length]="txTotal()"
                      [pageSize]="txPageSize()"
                      [pageIndex]="txPageIndex()"
                      [pageSizeOptions]="[10, 20, 50]"
                      (page)="onTxPageChange($event)"
                      showFirstLastButtons
                    ></mat-paginator>
                  }
                </div>
              </mat-tab>
            </mat-tab-group>
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
      .card-detail-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 8px;
        flex-wrap: wrap;
      }
      .summary-hero {
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 24px 32px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      }
      .hero-left {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .pan-row {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .holder-name {
        font-size: 18px;
        font-weight: 600;
        color: #0f172a;
      }
      .expiry-row {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        color: #64748b;
      }
      .expired-tag {
        font-size: 10px;
        font-weight: 700;
        background: #fee2e2;
        color: #dc2626;
        padding: 1px 4px;
        border-radius: 3px;
      }
      .hero-right {
        text-align: right;
      }
      .balance-label {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        color: #64748b;
      }
      .balance-amount {
        font-size: 32px;
        font-weight: 700;
        color: #0f172a;
        margin: 4px 0;
      }
      .currency-indicator {
        font-size: 12px;
        color: #94a3b8;
      }
      .tabs-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        overflow: hidden;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      }
      .tab-content {
        padding: 24px;
      }
      .overview-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 20px;
      }
      .overview-section {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 16px 20px;
      }
      .full-span {
        grid-column: 1 / -1;
      }
      .section-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;
      }
      .section-header h3 {
        margin: 0;
        font-size: 15px;
        font-weight: 600;
        color: #0f172a;
      }
      .property-list {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .property-row {
        display: flex;
        justify-content: space-between;
        font-size: 14px;
      }
      .property-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 16px;
      }
      .prop-col {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 13px;
      }
      .prop-label {
        color: #64748b;
        font-size: 12px;
      }
      .chip-list {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .mcc-chip {
        background: #e2e8f0;
        color: #1e293b;
        padding: 3px 8px;
        border-radius: 4px;
        font-size: 12px;
        font-weight: 600;
      }
      .block-reason-box {
        margin-top: 16px;
        padding: 12px;
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 6px;
        color: #991b1b;
        font-size: 13px;
      }
      .table-responsive {
        width: 100%;
        overflow-x: auto;
      }
      .amount-cell {
        text-align: right;
      }
      .text-success {
        color: #16a34a;
      }
      .text-danger {
        color: #dc2626;
      }
      .text-muted {
        color: #64748b;
        font-size: 13px;
      }
      .code-badge {
        font-family: var(--mc-font-mono);
        font-weight: 600;
        padding: 2px 6px;
        background: #f1f5f9;
        border-radius: 4px;
        font-size: 12px;
      }
      .mcc-label {
        font-size: 11px;
      }
      .ref-link {
        font-size: 12px;
        margin-top: 2px;
      }
      @media (max-width: 768px) {
        .summary-hero {
          flex-direction: column;
          align-items: flex-start;
          gap: 16px;
        }
        .hero-right {
          text-align: left;
        }
        .overview-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardDetailComponent implements OnInit {
  private readonly cardsApi = inject(CardsApiService);
  private readonly authStore = inject(AuthStore);
  private readonly notify = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);

  readonly isAdmin = () => this.authStore.hasRole(['ADMIN']);

  readonly card = signal<CardResponse | null>(null);
  readonly loadingCard = signal(false);
  readonly errorCard = signal<string | null>(null);

  // Ledger Tab State
  readonly ledgerColumns = [
    'type',
    'amount',
    'balanceAfter',
    'description',
    'createdBy',
    'occurredAt',
  ];
  readonly ledgerEntries = signal<LedgerEntryResponse[]>([]);
  readonly loadingLedger = signal(false);
  readonly ledgerTotal = signal(0);
  readonly ledgerPageIndex = signal(0);
  readonly ledgerPageSize = signal(10);

  // Transactions Tab State
  readonly txColumns = ['time', 'merchant', 'amount', 'status', 'responseCode', 'flagged'];
  readonly transactions = signal<TransactionSummary[]>([]);
  readonly loadingTransactions = signal(false);
  readonly txTotal = signal(0);
  readonly txPageIndex = signal(0);
  readonly txPageSize = signal(10);

  private cardId = '';

  ngOnInit(): void {
    this.cardId = this.route.snapshot.paramMap.get('id') || '';
    if (this.cardId) {
      this.loadCard();
      this.loadLedger();
      this.loadTransactions();
    }
  }

  loadCard(): void {
    this.loadingCard.set(true);
    this.errorCard.set(null);

    this.cardsApi.getCard(this.cardId).subscribe({
      next: (c) => {
        this.loadingCard.set(false);
        this.card.set(c);
      },
      error: (err) => {
        this.loadingCard.set(false);
        this.errorCard.set(err?.detail || 'Failed to load card details');
      },
    });
  }

  loadLedger(): void {
    this.loadingLedger.set(true);
    this.cardsApi
      .getCardLedger(this.cardId, {
        page: this.ledgerPageIndex(),
        size: this.ledgerPageSize(),
      })
      .subscribe({
        next: (res) => {
          this.loadingLedger.set(false);
          this.ledgerEntries.set(res.content || []);
          this.ledgerTotal.set(res.page?.totalElements || 0);
        },
        error: () => {
          this.loadingLedger.set(false);
        },
      });
  }

  loadTransactions(): void {
    this.loadingTransactions.set(true);
    this.cardsApi
      .getCardTransactions(this.cardId, {
        page: this.txPageIndex(),
        size: this.txPageSize(),
      })
      .subscribe({
        next: (res) => {
          this.loadingTransactions.set(false);
          this.transactions.set(res.content || []);
          this.txTotal.set(res.page?.totalElements || 0);
        },
        error: () => {
          this.loadingTransactions.set(false);
        },
      });
  }

  onLedgerPageChange(event: PageEvent): void {
    this.ledgerPageIndex.set(event.pageIndex);
    this.ledgerPageSize.set(event.pageSize);
    this.loadLedger();
  }

  onTxPageChange(event: PageEvent): void {
    this.txPageIndex.set(event.pageIndex);
    this.txPageSize.set(event.pageSize);
    this.loadTransactions();
  }

  activateCard(): void {
    const dialogData: ConfirmDialogData = {
      title: 'Activate Card',
      message:
        'Are you sure you want to activate this card? The holder will be able to perform transactions.',
      confirmLabel: 'Activate Card',
    };

    const dialogRef = this.dialog.open(ConfirmDialogComponent, { data: dialogData });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.cardsApi.activateCard(this.cardId).subscribe({
          next: (updated) => {
            this.notify.success('Card activated');
            this.card.set(updated);
          },
          error: (err) => {
            this.notify.error(err?.detail || 'Failed to activate card');
          },
        });
      }
    });
  }

  openBlockDialog(): void {
    const current = this.card();
    if (!current) return;

    const dialogRef = this.dialog.open(BlockCardDialogComponent, {
      data: { card: current },
      width: '480px',
    });

    dialogRef.afterClosed().subscribe((updated: CardResponse | undefined) => {
      if (updated) {
        this.card.set(updated);
      }
    });
  }

  unblockCard(): void {
    const dialogData: ConfirmDialogData = {
      title: 'Unblock Card',
      message:
        'Are you sure you want to unblock this card? Valid authorizations will be permitted again.',
      confirmLabel: 'Unblock Card',
    };

    const dialogRef = this.dialog.open(ConfirmDialogComponent, { data: dialogData });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.cardsApi.unblockCard(this.cardId).subscribe({
          next: (updated) => {
            this.notify.success('Card unblocked');
            this.card.set(updated);
          },
          error: (err) => {
            this.notify.error(err?.detail || 'Failed to unblock card');
          },
        });
      }
    });
  }

  closeCard(): void {
    const current = this.card();
    if (!current) return;

    const dialogData: ConfirmDialogData = {
      title: 'Close Card Permanently',
      message: `Closing this card is irreversible. The card cannot be reopened or reused. Are you sure you wish to close card ${current.maskedPan}?`,
      confirmLabel: 'Close Card',
      isDestructive: true,
    };

    const dialogRef = this.dialog.open(ConfirmDialogComponent, { data: dialogData });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.cardsApi.closeCard(this.cardId).subscribe({
          next: (updated) => {
            this.notify.success('Card closed');
            this.card.set(updated);
          },
          error: (err) => {
            this.notify.error(err?.detail || 'Failed to close card');
          },
        });
      }
    });
  }

  openTopUpDialog(): void {
    const current = this.card();
    if (!current) return;

    const dialogRef = this.dialog.open(TopUpCardDialogComponent, {
      data: { card: current },
      width: '480px',
    });

    dialogRef.afterClosed().subscribe((res: LedgerEntryResponse | undefined) => {
      if (res) {
        this.loadCard();
        this.loadLedger();
      }
    });
  }

  openEditLimitsDialog(): void {
    const current = this.card();
    if (!current) return;

    const dialogRef = this.dialog.open(EditLimitsDialogComponent, {
      data: { card: current },
      width: '480px',
    });

    dialogRef.afterClosed().subscribe((updated: CardResponse | undefined) => {
      if (updated) {
        this.card.set(updated);
      }
    });
  }

  openEditBlockedMccsDialog(): void {
    const current = this.card();
    if (!current) return;

    const dialogRef = this.dialog.open(EditBlockedMccsDialogComponent, {
      data: { card: current },
      width: '500px',
    });

    dialogRef.afterClosed().subscribe((updated: CardResponse | undefined) => {
      if (updated) {
        this.card.set(updated);
      }
    });
  }

  formatExpiry(month?: number, year?: number): string {
    if (!month || !year) return '-';
    const m = String(month).padStart(2, '0');
    const y = String(year).slice(-2);
    return `${m}/${y}`;
  }
}
