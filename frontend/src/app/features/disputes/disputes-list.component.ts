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
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { QueryParams } from '../../core/api/api-client';
import { NotificationService } from '../../core/notify/notification.service';
import { ReferenceDataService } from '../../core/reference/reference-data.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { DisputeResponse, DisputesApiService } from './disputes-api.service';

@Component({
  selector: 'mc-disputes-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatTooltipModule,
    PageHeaderComponent,
    StatusChipComponent,
    LoadingSkeletonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    MoneyPipe,
  ],
  template: `
    <mc-page-header
      title="Disputes"
      subtitle="Manage transaction challenges and evidence collection"
    ></mc-page-header>

    <!-- Filters Card -->
    <mat-card class="filters-card">
      <mat-card-content>
        <form [formGroup]="filterForm" class="filters-form">
          <!-- Status Filter -->
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="">All Statuses</mat-option>
              <mat-option value="OPENED">OPENED</mat-option>
              <mat-option value="EVIDENCE_REQUESTED">EVIDENCE_REQUESTED</mat-option>
              <mat-option value="UNDER_REVIEW">UNDER_REVIEW</mat-option>
              <mat-option value="RESOLVED_WON">RESOLVED_WON</mat-option>
              <mat-option value="RESOLVED_LOST">RESOLVED_LOST</mat-option>
            </mat-select>
          </mat-form-field>

          <!-- Reason Filter -->
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Reason</mat-label>
            <mat-select formControlName="reason">
              <mat-option value="">All Reasons</mat-option>
              @for (r of disputeReasons(); track r) {
                <mat-option [value]="r">{{ formatReason(r) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <!-- Overdue Toggle -->
          <div class="toggle-col">
            <mat-slide-toggle formControlName="overdue" color="warn">
              <span class="overdue-label">Overdue Only</span>
            </mat-slide-toggle>
          </div>

          <div class="filter-actions">
            <button mat-button type="button" (click)="resetFilters()">Reset</button>
            <button mat-flat-button color="primary" type="button" (click)="applyFilters()">
              Filter
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>

    @if (loading()) {
      <div class="skeleton-wrap">
        <mc-loading-skeleton [rows]="8"></mc-loading-skeleton>
      </div>
    } @else if (error()) {
      <mc-error-state
        title="Failed to load disputes"
        [message]="error() || ''"
        (retry)="loadDisputes()"
      ></mc-error-state>
    } @else if (disputes().length === 0) {
      <mc-empty-state
        icon="gavel"
        title="No Disputes Found"
        message="No disputes match the selected filters."
      ></mc-empty-state>
    } @else {
      <div class="table-container">
        <table mat-table [dataSource]="disputes()" class="mc-dense-table">
          <!-- Opened At -->
          <ng-container matColumnDef="openedAt">
            <th mat-header-cell *matHeaderCellDef>Opened</th>
            <td mat-cell *matCellDef="let d" class="tabular-nums">
              {{ d.openedAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
            </td>
          </ng-container>

          <!-- Card -->
          <ng-container matColumnDef="card">
            <th mat-header-cell *matHeaderCellDef>Card</th>
            <td mat-cell *matCellDef="let d" class="mono font-semibold">
              •••• {{ d.cardLast4 || '----' }}
            </td>
          </ng-container>

          <!-- Amount -->
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef class="amount-header">Amount</th>
            <td mat-cell *matCellDef="let d" class="amount-cell tabular-nums font-semibold">
              {{ d.amountMinor | money: d.currency }}
            </td>
          </ng-container>

          <!-- Reason -->
          <ng-container matColumnDef="reason">
            <th mat-header-cell *matHeaderCellDef>Reason</th>
            <td mat-cell *matCellDef="let d">
              {{ formatReason(d.reason) }}
            </td>
          </ng-container>

          <!-- Status Chip -->
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef class="center-cell">Status</th>
            <td mat-cell *matCellDef="let d" class="center-cell">
              <mc-status-chip [status]="d.status || 'OPENED'"></mc-status-chip>
            </td>
          </ng-container>

          <!-- Evidence Due -->
          <ng-container matColumnDef="evidenceDue">
            <th mat-header-cell *matHeaderCellDef>Evidence Due</th>
            <td mat-cell *matCellDef="let d">
              @if (d.evidenceDueAt) {
                <span class="due-badge {{ isOverdue(d.evidenceDueAt) ? 'overdue' : '' }}">
                  {{ d.evidenceDueAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                  @if (isOverdue(d.evidenceDueAt)) {
                    <span class="overdue-tag">OVERDUE</span>
                  }
                </span>
              } @else {
                <span class="text-muted">-</span>
              }
            </td>
          </ng-container>

          <!-- Opened By -->
          <ng-container matColumnDef="openedBy">
            <th mat-header-cell *matHeaderCellDef>Opened By</th>
            <td mat-cell *matCellDef="let d" class="text-muted">
              {{ d.openedBy || '-' }}
            </td>
          </ng-container>

          <!-- Actions -->
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="actions-header">Actions</th>
            <td mat-cell *matCellDef="let d" class="actions-cell">
              <a
                mat-icon-button
                [routerLink]="['/disputes', d.id]"
                matTooltip="View dispute details"
                aria-label="View dispute details"
              >
                <mat-icon>chevron_right</mat-icon>
              </a>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr
            mat-row
            *matRowDef="let row; columns: displayedColumns"
            class="clickable-row"
            (click)="navigateToDetail(row.id)"
          ></tr>
        </table>

        <mat-paginator
          [length]="totalElements()"
          [pageSize]="pageSize()"
          [pageIndex]="pageIndex()"
          [pageSizeOptions]="[10, 20, 50]"
          (page)="onPageChange($event)"
          showFirstLastButtons
        ></mat-paginator>
      </div>
    }
  `,
  styles: [
    `
      .filters-card {
        margin-bottom: 20px;
        border: 1px solid #e2e8f0;
      }
      .filters-form {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 16px;
        padding-top: 8px;
      }
      .filter-field {
        min-width: 180px;
      }
      .toggle-col {
        display: flex;
        align-items: center;
        padding-bottom: 20px;
      }
      .overdue-label {
        font-size: 13px;
        font-weight: 600;
        color: #991b1b;
      }
      .filter-actions {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-left: auto;
        padding-bottom: 20px;
      }

      .skeleton-wrap {
        padding: 24px;
        background: white;
        border-radius: 8px;
      }
      .table-container {
        background: white;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        overflow: hidden;
      }
      .mc-dense-table {
        width: 100%;
      }
      .clickable-row {
        cursor: pointer;
        transition: background-color 0.15s ease;
      }
      .clickable-row:hover {
        background-color: #f8fafc;
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

      .due-badge {
        font-size: 12px;
        padding: 3px 6px;
        border-radius: 4px;
        background: #f1f5f9;
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }
      .due-badge.overdue {
        background: #fee2e2;
        color: #991b1b;
        font-weight: 600;
      }
      .overdue-tag {
        font-size: 10px;
        background: #dc2626;
        color: white;
        padding: 1px 4px;
        border-radius: 3px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisputesListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly disputesApi = inject(DisputesApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly disputes = signal<DisputeResponse[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly totalElements = signal<number>(0);
  readonly pageSize = signal<number>(20);
  readonly pageIndex = signal<number>(0);

  readonly disputeReasons = signal<string[]>([]);

  readonly displayedColumns: string[] = [
    'openedAt',
    'card',
    'amount',
    'reason',
    'status',
    'evidenceDue',
    'openedBy',
    'actions',
  ];

  readonly filterForm: FormGroup = this.fb.group({
    status: [''],
    reason: [''],
    overdue: [false],
  });

  ngOnInit(): void {
    this.loadDisputeReasons();
    this.loadDisputes();
  }

  loadDisputeReasons(): void {
    this.refDataService
      .getReferenceData()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (data.disputeReasons) {
            this.disputeReasons.set(data.disputeReasons);
          }
        },
      });
  }

  loadDisputes(): void {
    this.loading.set(true);
    this.error.set(null);

    const val = this.filterForm.value;
    const params: QueryParams = {
      page: this.pageIndex(),
      size: this.pageSize(),
      status: val.status || undefined,
      reason: val.reason || undefined,
      overdue: val.overdue || undefined,
    };

    this.disputesApi
      .listDisputes(params)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.disputes.set(page.content || []);
          this.totalElements.set(page.page?.totalElements || 0);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.detail || 'Failed to load disputes');
        },
      });
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.loadDisputes();
  }

  resetFilters(): void {
    this.filterForm.reset({
      status: '',
      reason: '',
      overdue: false,
    });
    this.pageIndex.set(0);
    this.loadDisputes();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadDisputes();
  }

  navigateToDetail(disputeId?: string): void {
    if (disputeId) {
      this.router.navigate(['/disputes', disputeId]);
    }
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
}
