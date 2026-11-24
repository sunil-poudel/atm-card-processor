import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { CopyButtonComponent } from '../../shared/ui/copy-button.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { AuditApiService, AuditEventResponse, AuditFilters } from './audit-api.service';

@Component({
  selector: 'mc-audit-log',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatTooltipModule,
    PageHeaderComponent,
    CopyButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
  ],
  template: `
    <div class="audit-page">
      <mc-page-header
        title="Audit Log"
        subtitle="Immutable audit trail of staff actions, security events, and configuration mutations (Asia/Kathmandu)"
      >
        <button mat-stroked-button (click)="loadEvents()" aria-label="Refresh audit log">
          <mat-icon>refresh</mat-icon>
          <span>Refresh</span>
        </button>
      </mc-page-header>

      <!-- Filters Card -->
      <mat-card class="filter-card">
        <form [formGroup]="filterForm" (ngSubmit)="applyFilters()" class="filter-grid">
          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Action</mat-label>
            <mat-select formControlName="action">
              <mat-option value="">All Actions</mat-option>
              <mat-option value="USER_CREATED">USER_CREATED</mat-option>
              <mat-option value="USER_UPDATED">USER_UPDATED</mat-option>
              <mat-option value="CARD_ISSUED">CARD_ISSUED</mat-option>
              <mat-option value="CARD_BLOCKED">CARD_BLOCKED</mat-option>
              <mat-option value="CARD_UNBLOCKED">CARD_UNBLOCKED</mat-option>
              <mat-option value="CARD_CLOSED">CARD_CLOSED</mat-option>
              <mat-option value="CARD_TOPUP">CARD_TOPUP</mat-option>
              <mat-option value="CARD_LIMITS_UPDATED">CARD_LIMITS_UPDATED</mat-option>
              <mat-option value="CARD_BLOCKED_MCCS_UPDATED">CARD_BLOCKED_MCCS_UPDATED</mat-option>
              <mat-option value="FRAUD_RULE_CREATED">FRAUD_RULE_CREATED</mat-option>
              <mat-option value="FRAUD_RULE_UPDATED">FRAUD_RULE_UPDATED</mat-option>
              <mat-option value="FRAUD_RULE_ENABLED">FRAUD_RULE_ENABLED</mat-option>
              <mat-option value="FRAUD_RULE_DISABLED">FRAUD_RULE_DISABLED</mat-option>
              <mat-option value="DISPUTE_OPENED">DISPUTE_OPENED</mat-option>
              <mat-option value="DISPUTE_EVIDENCE_REQUESTED">DISPUTE_EVIDENCE_REQUESTED</mat-option>
              <mat-option value="DISPUTE_EVIDENCE_SUBMITTED">DISPUTE_EVIDENCE_SUBMITTED</mat-option>
              <mat-option value="DISPUTE_RESOLVED_WON">DISPUTE_RESOLVED_WON</mat-option>
              <mat-option value="DISPUTE_RESOLVED_LOST">DISPUTE_RESOLVED_LOST</mat-option>
              <mat-option value="API_CLIENT_CREATED">API_CLIENT_CREATED</mat-option>
              <mat-option value="API_CLIENT_REVOKED">API_CLIENT_REVOKED</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Entity Type</mat-label>
            <mat-select formControlName="entityType">
              <mat-option value="">All Entities</mat-option>
              <mat-option value="Card">Card</mat-option>
              <mat-option value="AppUser">AppUser</mat-option>
              <mat-option value="FraudRule">FraudRule</mat-option>
              <mat-option value="Dispute">Dispute</mat-option>
              <mat-option value="ApiClient">ApiClient</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Actor Email / Label</mat-label>
            <input matInput formControlName="actorLabel" placeholder="e.g. admin@minicard.local" />
            <mat-icon matPrefix>person_search</mat-icon>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>Entity ID (UUID)</mat-label>
            <input matInput formControlName="entityId" placeholder="Filter by UUID" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>From Date</mat-label>
            <input matInput type="date" formControlName="from" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-item">
            <mat-label>To Date</mat-label>
            <input matInput type="date" formControlName="to" />
          </mat-form-field>

          <div class="filter-actions">
            <button mat-flat-button color="primary" type="submit">
              <mat-icon>filter_list</mat-icon>
              <span>Apply Filters</span>
            </button>
            <button mat-stroked-button type="button" (click)="clearFilters()">
              <span>Clear</span>
            </button>
          </div>
        </form>
      </mat-card>

      <!-- Table Card -->
      <mat-card class="table-card">
        @if (loading()) {
          <mc-loading-skeleton [rows]="6" />
        } @else if (error()) {
          <mc-error-state
            title="Failed to load audit events"
            [message]="error()!"
            (retry)="loadEvents()"
          />
        } @else if (events().length === 0) {
          <mc-empty-state
            icon="history"
            title="No audit events found"
            message="No system or user actions match the specified filters."
          />
        } @else {
          <div class="table-responsive">
            <table mat-table [dataSource]="events()" class="audit-table">
              <!-- Occurred At Column -->
              <ng-container matColumnDef="occurredAt">
                <th mat-header-cell *matHeaderCellDef>Time (Asia/Kathmandu)</th>
                <td mat-cell *matCellDef="let ev" class="text-muted tabular-nums">
                  {{ ev.occurredAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                </td>
              </ng-container>

              <!-- Actor Column -->
              <ng-container matColumnDef="actor">
                <th mat-header-cell *matHeaderCellDef>Actor</th>
                <td mat-cell *matCellDef="let ev">
                  <div class="actor-cell">
                    <span class="actor-label font-medium">{{ ev.actorLabel || 'SYSTEM' }}</span>
                    <span class="actor-type-chip">{{ ev.actorType }}</span>
                  </div>
                </td>
              </ng-container>

              <!-- Action Column -->
              <ng-container matColumnDef="action">
                <th mat-header-cell *matHeaderCellDef>Action</th>
                <td mat-cell *matCellDef="let ev">
                  <span class="action-tag font-mono">{{ ev.action }}</span>
                </td>
              </ng-container>

              <!-- Entity Column -->
              <ng-container matColumnDef="entity">
                <th mat-header-cell *matHeaderCellDef>Entity</th>
                <td mat-cell *matCellDef="let ev">
                  <div class="entity-cell">
                    <span class="entity-type font-medium">{{ ev.entityType }}</span>
                    @if (ev.entityId) {
                      <div class="entity-id-row">
                        <code class="short-id font-mono">{{ ev.entityId | slice: 0 : 8 }}...</code>
                        <mc-copy-button [value]="ev.entityId" tooltip="Copy Entity ID" />
                      </div>
                    }
                  </div>
                </td>
              </ng-container>

              <!-- Details Column -->
              <ng-container matColumnDef="details">
                <th mat-header-cell *matHeaderCellDef>Details</th>
                <td mat-cell *matCellDef="let ev">
                  <div
                    class="details-text"
                    [matTooltip]="ev.details || 'No details'"
                    matTooltipPosition="above"
                  >
                    {{ ev.details || '—' }}
                  </div>
                </td>
              </ng-container>

              <!-- Request ID Column -->
              <ng-container matColumnDef="requestId">
                <th mat-header-cell *matHeaderCellDef>Request ID</th>
                <td mat-cell *matCellDef="let ev">
                  @if (ev.requestId) {
                    <div class="req-id-box">
                      <code class="short-id font-mono">{{ ev.requestId | slice: 0 : 8 }}...</code>
                      <mc-copy-button [value]="ev.requestId" tooltip="Copy Request ID" />
                    </div>
                  } @else {
                    <span class="text-muted">—</span>
                  }
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
            </table>
          </div>

          <mat-paginator
            [length]="totalElements()"
            [pageSize]="pageSize()"
            [pageIndex]="pageIndex()"
            [pageSizeOptions]="[10, 20, 50, 100]"
            (page)="onPageChange($event)"
            showFirstLastButtons
            aria-label="Select audit log page"
          />
        }
      </mat-card>
    </div>
  `,
  styles: [
    `
      .audit-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .filter-card {
        border-radius: 12px;
        padding: 16px 20px;
        border: 1px solid var(--mc-border, #e2e8f0);
        box-shadow: none;
        background: #ffffff;
      }
      .filter-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
        gap: 12px;
        align-items: center;
      }
      .filter-actions {
        display: flex;
        align-items: center;
        gap: 8px;
        height: 56px;
        padding-top: 4px;
      }
      .table-card {
        border-radius: 12px;
        overflow: hidden;
        border: 1px solid var(--mc-border, #e2e8f0);
        box-shadow: none;
        background: #ffffff;
      }
      .table-responsive {
        overflow-x: auto;
      }
      .audit-table {
        width: 100%;
        border-collapse: collapse;
      }
      .actor-cell {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .actor-label {
        color: var(--mc-text-primary, #0f172a);
      }
      .actor-type-chip {
        display: inline-block;
        font-size: 0.6875rem;
        font-weight: 600;
        text-transform: uppercase;
        color: #475569;
        background: #f1f5f9;
        padding: 1px 6px;
        border-radius: 4px;
        width: fit-content;
      }
      .action-tag {
        font-size: 0.75rem;
        background-color: #f8fafc;
        border: 1px solid #e2e8f0;
        padding: 2px 6px;
        border-radius: 4px;
        font-weight: 600;
        color: #1e293b;
      }
      .entity-cell {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .entity-id-row {
        display: flex;
        align-items: center;
        gap: 4px;
      }
      .details-text {
        max-width: 240px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.8125rem;
        color: #334155;
        cursor: default;
      }
      .req-id-box {
        display: flex;
        align-items: center;
        gap: 4px;
      }
      .short-id {
        font-size: 0.8125rem;
        color: #64748b;
      }
      .font-mono {
        font-family: monospace;
      }
      .font-medium {
        font-weight: 500;
      }
      .text-muted {
        color: var(--mc-text-secondary, #64748b);
      }
      .tabular-nums {
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditLogComponent implements OnInit {
  private readonly auditApi = inject(AuditApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly displayedColumns = ['occurredAt', 'actor', 'action', 'entity', 'details', 'requestId'];

  readonly events = signal<AuditEventResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly totalElements = signal(0);

  readonly filterForm = this.fb.group({
    action: [''],
    entityType: [''],
    entityId: [''],
    actorLabel: [''],
    from: [''],
    to: [''],
  });

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParams;
    this.filterForm.patchValue({
      action: qp['action'] || '',
      entityType: qp['entityType'] || '',
      entityId: qp['entityId'] || '',
      actorLabel: qp['actorLabel'] || '',
      from: qp['from'] || '',
      to: qp['to'] || '',
    });
    if (qp['page']) {
      this.pageIndex.set(parseInt(qp['page'], 10) || 0);
    }
    if (qp['size']) {
      this.pageSize.set(parseInt(qp['size'], 10) || 20);
    }

    this.loadEvents();
  }

  loadEvents(): void {
    this.loading.set(true);
    this.error.set(null);

    const val = this.filterForm.value;
    const filters: AuditFilters = {
      action: val.action || undefined,
      entityType: val.entityType || undefined,
      entityId: val.entityId || undefined,
      actorLabel: val.actorLabel || undefined,
      from: val.from ? `${val.from}T00:00:00Z` : undefined,
      to: val.to ? `${val.to}T23:59:59Z` : undefined,
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: 'occurredAt,desc',
    };

    this.auditApi.listAuditEvents(filters).subscribe({
      next: (res) => {
        this.events.set(res.content ?? []);
        this.totalElements.set(res.page?.totalElements ?? res.content?.length ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load audit events. Please try again.');
        this.loading.set(false);
      },
    });
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.syncUrlAndLoad();
  }

  clearFilters(): void {
    this.filterForm.reset({
      action: '',
      entityType: '',
      entityId: '',
      actorLabel: '',
      from: '',
      to: '',
    });
    this.pageIndex.set(0);
    this.syncUrlAndLoad();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.syncUrlAndLoad();
  }

  private syncUrlAndLoad(): void {
    const val = this.filterForm.value;
    const queryParams: Record<string, string | number | undefined> = {
      action: val.action || undefined,
      entityType: val.entityType || undefined,
      entityId: val.entityId || undefined,
      actorLabel: val.actorLabel || undefined,
      from: val.from || undefined,
      to: val.to || undefined,
      page: this.pageIndex() > 0 ? this.pageIndex() : undefined,
      size: this.pageSize() !== 20 ? this.pageSize() : undefined,
    };

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'replace',
    });

    this.loadEvents();
  }
}
