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
import { AuthStore } from '../../core/auth/auth.store';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { MaskedPanComponent } from '../../shared/ui/masked-pan.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { CardResponse, CardsApiService } from './cards-api.service';
import { CardIssueDialogComponent } from './dialogs/card-issue-dialog.component';

@Component({
  selector: 'mc-cards-list',
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
    MaskedPanComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
    MoneyPipe,
  ],
  template: `
    <div class="cards-page">
      <mc-page-header
        title="Prepaid Cards"
        subtitle="Manage card issuance, lifecycles, balances, and operational limits"
      >
        <div actions>
          @if (isAdmin()) {
            <button mat-flat-button color="primary" (click)="openIssueDialog()">
              <mat-icon>add_card</mat-icon>
              <span>Issue Card</span>
            </button>
          }
        </div>
      </mc-page-header>

      <!-- Filters Card -->
      <div class="filters-card">
        <form [formGroup]="filterForm" class="filters-form">
          <mat-form-field appearance="outline" class="filter-search">
            <mat-label>Search</mat-label>
            <input
              matInput
              formControlName="q"
              placeholder="Search by holder name, PAN, or last4"
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

          <mat-form-field appearance="outline" class="filter-select">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="">All Statuses</mat-option>
              <mat-option value="ACTIVE">ACTIVE</mat-option>
              <mat-option value="INACTIVE">INACTIVE</mat-option>
              <mat-option value="BLOCKED">BLOCKED</mat-option>
              <mat-option value="CLOSED">CLOSED</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-select">
            <mat-label>Currency</mat-label>
            <mat-select formControlName="currency">
              <mat-option value="">All Currencies</mat-option>
              <mat-option value="NPR">NPR</mat-option>
              <mat-option value="USD">USD</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-select">
            <mat-label>Expiration</mat-label>
            <mat-select formControlName="expired">
              <mat-option value="">All</mat-option>
              <mat-option value="false">Active (Not Expired)</mat-option>
              <mat-option value="true">Expired</mat-option>
            </mat-select>
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

      <!-- Main Content Card -->
      <div class="table-container-card">
        @if (loading()) {
          <mc-loading-skeleton [rows]="5"></mc-loading-skeleton>
        } @else if (errorMessage()) {
          <mc-error-state
            title="Failed to load cards"
            [message]="errorMessage()!"
            (retry)="loadCards()"
          ></mc-error-state>
        } @else if (cards().length === 0) {
          <mc-empty-state
            icon="credit_card_off"
            title="No cards found"
            [message]="
              isDefaultFilters()
                ? 'No cards have been issued yet. Click Issue Card to create the first card.'
                : 'No cards match the active filter criteria.'
            "
            [actionLabel]="isAdmin() && isDefaultFilters() ? 'Issue Card' : undefined"
            (action)="openIssueDialog()"
          ></mc-empty-state>
        } @else {
          <div class="table-responsive">
            <table
              mat-table
              [dataSource]="cards()"
              matSort
              (matSortChange)="onSortChange($event)"
              class="cards-table"
            >
              <!-- Masked PAN Column -->
              <ng-container matColumnDef="maskedPan">
                <th mat-header-cell *matHeaderCellDef>Card Number</th>
                <td mat-cell *matCellDef="let card">
                  <mc-masked-pan [pan]="card.maskedPan"></mc-masked-pan>
                </td>
              </ng-container>

              <!-- Cardholder Column -->
              <ng-container matColumnDef="holderName">
                <th mat-header-cell *matHeaderCellDef mat-sort-header>Holder Name</th>
                <td mat-cell *matCellDef="let card" class="font-medium">
                  {{ card.holderName }}
                </td>
              </ng-container>

              <!-- Currency Column -->
              <ng-container matColumnDef="currency">
                <th mat-header-cell *matHeaderCellDef>Currency</th>
                <td mat-cell *matCellDef="let card">
                  <span class="currency-tag">{{ card.currency }}</span>
                </td>
              </ng-container>

              <!-- Status Column -->
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let card">
                  <mc-status-chip [status]="card.status"></mc-status-chip>
                </td>
              </ng-container>

              <!-- Balance Column -->
              <ng-container matColumnDef="balanceMinor">
                <th mat-header-cell *matHeaderCellDef mat-sort-header class="amount-cell">
                  Balance
                </th>
                <td mat-cell *matCellDef="let card" class="amount-cell tabular-nums font-semibold">
                  {{ card.balanceMinor | money: card.currency }}
                </td>
              </ng-container>

              <!-- Expiry Column -->
              <ng-container matColumnDef="expiry">
                <th mat-header-cell *matHeaderCellDef>Expiry</th>
                <td
                  mat-cell
                  *matCellDef="let card"
                  [class.text-danger]="card.expired"
                  [class.font-semibold]="card.expired"
                >
                  {{ formatExpiry(card.expiryMonth, card.expiryYear) }}
                  @if (card.expired) {
                    <span class="expired-badge">EXPIRED</span>
                  }
                </td>
              </ng-container>

              <!-- Created At Column -->
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef mat-sort-header>Issued On</th>
                <td mat-cell *matCellDef="let card" class="text-muted">
                  {{ card.createdAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
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
            aria-label="Select card table page"
          ></mat-paginator>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .cards-page {
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
      .filter-search {
        flex: 1 1 240px;
        min-width: 200px;
      }
      .filter-select {
        flex: 0 1 180px;
        min-width: 140px;
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
      .cards-table {
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
      .currency-tag {
        font-weight: 600;
        font-size: 12px;
        background: #f1f5f9;
        color: #475569;
        padding: 2px 6px;
        border-radius: 4px;
      }
      .text-danger {
        color: #dc2626;
      }
      .expired-badge {
        font-size: 10px;
        font-weight: 700;
        background: #fee2e2;
        color: #dc2626;
        padding: 1px 4px;
        border-radius: 3px;
        margin-left: 4px;
      }
      .text-muted {
        color: #64748b;
        font-size: 13px;
      }
      .font-medium {
        font-weight: 500;
      }
      .font-semibold {
        font-weight: 600;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardsListComponent implements OnInit {
  private readonly cardsApi = inject(CardsApiService);
  private readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isAdmin = () => this.authStore.hasRole(['ADMIN']);

  readonly displayedColumns = [
    'maskedPan',
    'holderName',
    'currency',
    'status',
    'balanceMinor',
    'expiry',
    'createdAt',
  ];

  readonly cards = signal<CardResponse[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  readonly sortField = signal<string>('createdAt');
  readonly sortDirection = signal<string>('desc');

  readonly filterForm = new FormGroup({
    q: new FormControl(''),
    status: new FormControl(''),
    currency: new FormControl(''),
    expired: new FormControl(''),
  });

  ngOnInit(): void {
    this.readUrlParams();

    this.filterForm.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.pageIndex.set(0);
      this.updateUrlAndLoad();
    });

    this.loadCards();
  }

  isDefaultFilters(): boolean {
    const val = this.filterForm.value;
    return !val.q && !val.status && !val.currency && !val.expired;
  }

  clearFilters(): void {
    this.filterForm.reset({ q: '', status: '', currency: '', expired: '' });
  }

  private readUrlParams(): void {
    const params = this.route.snapshot.queryParamMap;
    const q = params.get('q') || '';
    const status = params.get('status') || '';
    const currency = params.get('currency') || '';
    const expired = params.get('expired') || '';

    const page = Number(params.get('page') || '0');
    const size = Number(params.get('size') || '20');
    const sort = params.get('sort') || 'createdAt,desc';

    if (q || status || currency || expired) {
      this.filterForm.patchValue({ q, status, currency, expired }, { emitEvent: false });
    }

    this.pageIndex.set(page >= 0 ? page : 0);
    this.pageSize.set(size > 0 ? size : 20);

    const [field, dir] = sort.split(',');
    if (field) {
      this.sortField.set(field);
      this.sortDirection.set(dir || 'desc');
    }
  }

  private updateUrlAndLoad(): void {
    const filters = this.filterForm.value;
    const queryParams: Record<string, string | number | undefined> = {
      q: filters.q || undefined,
      status: filters.status || undefined,
      currency: filters.currency || undefined,
      expired: filters.expired || undefined,
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

    this.loadCards();
  }

  loadCards(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const filters = this.filterForm.value;
    const params: Record<string, string | number | boolean | undefined> = {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
    };

    if (filters.q) params['q'] = filters.q.trim();
    if (filters.status) params['status'] = filters.status;
    if (filters.currency) params['currency'] = filters.currency;
    if (filters.expired) params['expired'] = filters.expired === 'true';

    this.cardsApi.listCards(params).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.cards.set(res.content || []);
        this.totalElements.set(res.page?.totalElements || 0);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.detail || 'Failed to retrieve cards');
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

  onRowClick(card: CardResponse): void {
    if (card.id) {
      this.router.navigate(['/cards', card.id]);
    }
  }

  openIssueDialog(): void {
    const dialogRef = this.dialog.open(CardIssueDialogComponent, {
      width: '560px',
      disableClose: true,
    });

    dialogRef.afterClosed().subscribe((result: CardResponse | undefined) => {
      if (result) {
        this.loadCards();
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
