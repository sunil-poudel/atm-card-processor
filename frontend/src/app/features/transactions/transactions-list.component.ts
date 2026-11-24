import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/api/api-error';
import { AuthStore } from '../../core/auth/auth.store';
import { NotificationService } from '../../core/notify/notification.service';
import { ReferenceDataService } from '../../core/reference/reference-data.service';
import { parseMajorToMinor } from '../../shared/forms/money-parser';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { TransactionDetailDialogComponent } from './dialogs/transaction-detail-dialog.component';
import { TransactionsApiService, TransactionSummary } from './transactions-api.service';

@Component({
  selector: 'mc-transactions-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatDialogModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
    MoneyPipe,
  ],
  template: `
    <div class="tx-page">
      <mc-page-header
        title="Transactions"
        subtitle="Real-time transaction authorization ledger and fraud inspection"
      >
        <div actions>
          @if (canExport()) {
            <button
              mat-stroked-button
              color="primary"
              (click)="exportCsv()"
              [disabled]="exporting()"
            >
              <mat-icon>download</mat-icon>
              <span>{{ exporting() ? 'Exporting...' : 'Export CSV' }}</span>
            </button>
          }
        </div>
      </mc-page-header>

      <!-- Advanced Filters Card -->
      <div class="filters-card">
        <form [formGroup]="filterForm" class="filters-form">
          <mat-form-field appearance="outline" class="filter-item filter-search">
            <mat-label>Search</mat-label>
            <input
              matInput
              formControlName="q"
              placeholder="Search merchant, card last4, or reference"
            />
            <mat-icon matPrefix>search</mat-icon>
            @if (filterForm.controls.q.value) {
              <button
                mat-icon-button
                matSuffix
                type="button"
                (click)="filterForm.controls.q.setValue('')"
                aria-label="Clear search"
              >
                <mat-icon>close</mat-icon>
              </button>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="">All Statuses</mat-option>
              <mat-option value="APPROVED">APPROVED</mat-option>
              <mat-option value="DECLINED">DECLINED</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Response Code</mat-label>
            <mat-select formControlName="responseCode">
              <mat-option value="">All Codes</mat-option>
              @for (entry of responseCodesList(); track entry.code) {
                <mat-option [value]="entry.code"> {{ entry.code }} - {{ entry.desc }} </mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Decline Reason</mat-label>
            <mat-select formControlName="declineReason">
              <mat-option value="">All Reasons</mat-option>
              @for (reason of declineReasonsList(); track reason) {
                <mat-option [value]="reason">{{ formatReason(reason) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Merchant Category (MCC)</mat-label>
            <mat-select formControlName="mcc">
              <mat-option value="">All Categories</mat-option>
              @for (entry of mccsList(); track entry.code) {
                <mat-option [value]="entry.code"> {{ entry.code }} - {{ entry.desc }} </mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item filter-small">
            <mat-label>Flagged</mat-label>
            <mat-select formControlName="flagged">
              <mat-option value="">All</mat-option>
              <mat-option value="true">Flagged Only</mat-option>
              <mat-option value="false">Not Flagged</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item filter-small">
            <mat-label>Min Amount</mat-label>
            <input matInput formControlName="minAmount" placeholder="0.00" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item filter-small">
            <mat-label>Max Amount</mat-label>
            <input matInput formControlName="maxAmount" placeholder="10000.00" />
          </mat-form-field>

          <button
            mat-stroked-button
            type="button"
            class="clear-filters-btn"
            (click)="clearFilters()"
            [disabled]="isDefaultFilters()"
          >
            Clear Filters
          </button>
        </form>
      </div>

      <!-- Main Table Card -->
      <div class="table-container-card">
        @if (loading()) {
          <mc-loading-skeleton [rows]="5"></mc-loading-skeleton>
        } @else if (errorMessage()) {
          <mc-error-state
            title="Failed to load transactions"
            [message]="errorMessage()!"
            (retry)="loadTransactions()"
          ></mc-error-state>
        } @else if (transactions().length === 0) {
          <mc-empty-state
            icon="receipt_long"
            title="No transactions found"
            [message]="
              isDefaultFilters()
                ? 'No authorizations have been attempted yet.'
                : 'No transactions match the active filter criteria.'
            "
          ></mc-empty-state>
        } @else {
          <div class="table-responsive">
            <table
              mat-table
              [dataSource]="transactions()"
              matSort
              (matSortChange)="onSortChange($event)"
              class="tx-table"
            >
              <!-- Time Column -->
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef mat-sort-header>Time (Asia/Kathmandu)</th>
                <td mat-cell *matCellDef="let tx" class="text-muted">
                  {{ tx.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                </td>
              </ng-container>

              <!-- Card Column -->
              <ng-container matColumnDef="card">
                <th mat-header-cell *matHeaderCellDef>Card / Holder</th>
                <td mat-cell *matCellDef="let tx">
                  <div class="font-medium">•••• {{ tx.cardLast4 }}</div>
                  <div class="text-muted text-sm">{{ tx.holderName }}</div>
                </td>
              </ng-container>

              <!-- Merchant Column -->
              <ng-container matColumnDef="merchant">
                <th mat-header-cell *matHeaderCellDef>Merchant</th>
                <td mat-cell *matCellDef="let tx">
                  <div class="font-medium">{{ tx.merchantName }}</div>
                  <div class="text-muted text-sm">MCC: {{ tx.mcc }}</div>
                </td>
              </ng-container>

              <!-- Amount Column -->
              <ng-container matColumnDef="amountMinor">
                <th mat-header-cell *matHeaderCellDef mat-sort-header class="amount-cell">
                  Amount
                </th>
                <td mat-cell *matCellDef="let tx" class="amount-cell tabular-nums font-semibold">
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

              <!-- Code Column -->
              <ng-container matColumnDef="responseCode">
                <th mat-header-cell *matHeaderCellDef>Code</th>
                <td mat-cell *matCellDef="let tx">
                  <span class="code-badge" [matTooltip]="tx.declineReason || 'Approved'">
                    {{ tx.responseCode }}
                  </span>
                </td>
              </ng-container>

              <!-- Risk Score Column -->
              <ng-container matColumnDef="riskScore">
                <th mat-header-cell *matHeaderCellDef>Risk Score</th>
                <td mat-cell *matCellDef="let tx">
                  <span class="score-badge {{ getScoreClass(tx.riskScore) }}">
                    {{ tx.riskScore }}
                  </span>
                </td>
              </ng-container>

              <!-- Flagged Column -->
              <ng-container matColumnDef="flagged">
                <th mat-header-cell *matHeaderCellDef>Flagged</th>
                <td mat-cell *matCellDef="let tx">
                  @if (tx.flagged) {
                    <mat-icon color="warn" matTooltip="Flagged by fraud engine">flag</mat-icon>
                  } @else {
                    <span class="text-muted">-</span>
                  }
                </td>
              </ng-container>

              <!-- Source Column -->
              <ng-container matColumnDef="source">
                <th mat-header-cell *matHeaderCellDef>Source</th>
                <td mat-cell *matCellDef="let tx" class="text-muted text-sm">
                  {{ tx.source }}
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr
                mat-row
                *matRowDef="let row; columns: displayedColumns"
                class="clickable-row"
                (click)="onRowClick(row)"
              ></tr>
            </table>
          </div>

          <mat-paginator
            [length]="totalElements()"
            [pageSize]="pageSize()"
            [pageIndex]="pageIndex()"
            [pageSizeOptions]="[10, 20, 50]"
            (page)="onPageChange($event)"
            showFirstLastButtons
            aria-label="Select transaction table page"
          ></mat-paginator>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .tx-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .filters-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 16px 20px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      }
      .filters-form {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
      }
      .filter-item {
        flex: 1 1 180px;
        min-width: 140px;
      }
      .filter-search {
        flex: 2 1 240px;
        min-width: 220px;
      }
      .filter-small {
        flex: 0 1 130px;
        min-width: 110px;
      }
      .clear-filters-btn {
        height: 52px;
        margin-bottom: 4px;
      }
      .table-container-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
        overflow: hidden;
      }
      .table-responsive {
        width: 100%;
        overflow-x: auto;
      }
      .tx-table {
        width: 100%;
      }
      .clickable-row {
        cursor: pointer;
        transition: background-color 0.15s ease;
      }
      .clickable-row:hover {
        background-color: #f8fafc;
      }
      .amount-cell {
        text-align: right;
      }
      .text-sm {
        font-size: 11px;
      }
      .font-medium {
        font-weight: 500;
      }
      .font-semibold {
        font-weight: 600;
      }
      .text-muted {
        color: #64748b;
      }
      .code-badge {
        font-family: var(--mc-font-mono);
        font-weight: 600;
        padding: 2px 6px;
        background: #f1f5f9;
        border-radius: 4px;
        font-size: 12px;
      }
      .score-badge {
        font-family: var(--mc-font-mono);
        padding: 2px 6px;
        border-radius: 4px;
        font-weight: 700;
        font-size: 11px;
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
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsListComponent implements OnInit {
  private readonly transactionsApi = inject(TransactionsApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly authStore = inject(AuthStore);
  private readonly notify = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly canExport = () => this.authStore.hasRole(['ADMIN', 'ANALYST']);

  readonly displayedColumns = [
    'createdAt',
    'card',
    'merchant',
    'amountMinor',
    'status',
    'responseCode',
    'riskScore',
    'flagged',
    'source',
  ];

  readonly transactions = signal<TransactionSummary[]>([]);
  readonly loading = signal(false);
  readonly exporting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  readonly sortField = signal<string>('createdAt');
  readonly sortDirection = signal<string>('desc');

  readonly responseCodesList = signal<{ code: string; desc: string }[]>([]);
  readonly declineReasonsList = signal<string[]>([]);
  readonly mccsList = signal<{ code: string; desc: string }[]>([]);

  readonly filterForm = new FormGroup({
    q: new FormControl(''),
    status: new FormControl(''),
    responseCode: new FormControl(''),
    declineReason: new FormControl(''),
    mcc: new FormControl(''),
    flagged: new FormControl(''),
    minAmount: new FormControl(''),
    maxAmount: new FormControl(''),
  });

  ngOnInit(): void {
    this.loadReferenceData();
    this.readUrlParams();

    this.filterForm.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.pageIndex.set(0);
      this.updateUrlAndLoad();
    });

    this.loadTransactions();
  }

  private loadReferenceData(): void {
    this.refDataService.getReferenceData().subscribe({
      next: (data) => {
        if (data.responseCodes) {
          this.responseCodesList.set(
            Object.entries(data.responseCodes).map(([code, desc]) => ({ code, desc })),
          );
        }
        if (data.declineReasons) {
          this.declineReasonsList.set(Object.keys(data.declineReasons));
        }
        if (data.mccs) {
          this.mccsList.set(Object.entries(data.mccs).map(([code, desc]) => ({ code, desc })));
        }
      },
    });
  }

  isDefaultFilters(): boolean {
    const val = this.filterForm.value;
    return (
      !val.q &&
      !val.status &&
      !val.responseCode &&
      !val.declineReason &&
      !val.mcc &&
      !val.flagged &&
      !val.minAmount &&
      !val.maxAmount
    );
  }

  clearFilters(): void {
    this.filterForm.reset({
      q: '',
      status: '',
      responseCode: '',
      declineReason: '',
      mcc: '',
      flagged: '',
      minAmount: '',
      maxAmount: '',
    });
  }

  private readUrlParams(): void {
    const params = this.route.snapshot.queryParamMap;
    const q = params.get('q') || '';
    const status = params.get('status') || '';
    const responseCode = params.get('responseCode') || '';
    const declineReason = params.get('declineReason') || '';
    const mcc = params.get('mcc') || '';
    const flagged = params.get('flagged') || '';
    const minAmount = params.get('minAmount') || '';
    const maxAmount = params.get('maxAmount') || '';

    const page = Number(params.get('page') || '0');
    const size = Number(params.get('size') || '20');
    const sort = params.get('sort') || 'createdAt,desc';

    this.filterForm.patchValue(
      { q, status, responseCode, declineReason, mcc, flagged, minAmount, maxAmount },
      { emitEvent: false },
    );

    this.pageIndex.set(page >= 0 ? page : 0);
    this.pageSize.set(size > 0 ? size : 20);

    const [field, dir] = sort.split(',');
    if (field) {
      this.sortField.set(field);
      this.sortDirection.set(dir || 'desc');
    }
  }

  private updateUrlAndLoad(): void {
    const val = this.filterForm.value;
    const queryParams: Record<string, string | number | undefined> = {
      q: val.q || undefined,
      status: val.status || undefined,
      responseCode: val.responseCode || undefined,
      declineReason: val.declineReason || undefined,
      mcc: val.mcc || undefined,
      flagged: val.flagged || undefined,
      minAmount: val.minAmount || undefined,
      maxAmount: val.maxAmount || undefined,
      page: this.pageIndex() > 0 ? this.pageIndex() : undefined,
      size: this.pageSize() !== 20 ? this.pageSize() : undefined,
      sort:
        this.sortField() !== 'createdAt' || this.sortDirection() !== 'desc'
          ? `${this.sortField()},${this.sortDirection()}`
          : undefined,
    };

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });

    this.loadTransactions();
  }

  private buildQueryParams(): Record<string, string | number | boolean | undefined> {
    const val = this.filterForm.value;
    const params: Record<string, string | number | boolean | undefined> = {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
    };

    if (val.q) params['q'] = val.q.trim();
    if (val.status) params['status'] = val.status;
    if (val.responseCode) params['responseCode'] = val.responseCode;
    if (val.declineReason) params['declineReason'] = val.declineReason;
    if (val.mcc) params['mcc'] = val.mcc;
    if (val.flagged) params['flagged'] = val.flagged === 'true';

    if (val.minAmount) {
      try {
        params['minAmountMinor'] = parseMajorToMinor(val.minAmount);
      } catch {
        // ignore invalid filter input
      }
    }

    if (val.maxAmount) {
      try {
        params['maxAmountMinor'] = parseMajorToMinor(val.maxAmount);
      } catch {
        // ignore invalid filter input
      }
    }

    return params;
  }

  loadTransactions(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const params = this.buildQueryParams();

    this.transactionsApi.listTransactions(params).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.transactions.set(res.content || []);
        this.totalElements.set(res.page?.totalElements || 0);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.detail || 'Failed to retrieve transactions');
      },
    });
  }

  exportCsv(): void {
    if (this.exporting()) return;

    this.exporting.set(true);
    const params = this.buildQueryParams();
    delete params['page'];
    delete params['size'];

    this.transactionsApi.exportCsv(params).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `transactions-${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.notify.success('Transaction CSV exported');
      },
      error: (err: unknown) => {
        this.exporting.set(false);
        if (err instanceof ApiError && err.code === 'EXPORT_TOO_LARGE') {
          this.notify.error(
            'Export exceeds 50,000 row limit. Please narrow your date range or filters.',
          );
        } else {
          this.notify.error('Failed to export CSV. Please try again.');
        }
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.updateUrlAndLoad();
  }

  onSortChange(sort: Sort): void {
    if (sort.active && sort.direction) {
      this.sortField.set(sort.active);
      this.sortDirection.set(sort.direction);
    } else {
      this.sortField.set('createdAt');
      this.sortDirection.set('desc');
    }
    this.updateUrlAndLoad();
  }

  onRowClick(tx: TransactionSummary): void {
    if (!tx.id) return;
    this.dialog.open(TransactionDetailDialogComponent, {
      data: { transactionId: tx.id },
      width: '640px',
    });
  }

  getScoreClass(score?: number): string {
    const s = score || 0;
    if (s >= 80) return 'score-high';
    if (s >= 50) return 'score-med';
    return 'score-low';
  }

  formatReason(reason: string): string {
    return reason.replace(/_/g, ' ');
  }
}
