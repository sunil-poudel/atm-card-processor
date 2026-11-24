import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NotificationService } from '../../core/notify/notification.service';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { ApiClientResponse, ApiClientsApiService } from './api-clients-api.service';
import { CreateApiClientDialogComponent } from './dialogs/create-api-client-dialog.component';

@Component({
  selector: 'mc-api-clients-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatDialogModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
  ],
  template: `
    <div class="api-clients-page">
      <mc-page-header
        title="API Clients"
        subtitle="Manage machine client credentials and external integration access"
      >
        <button
          mat-flat-button
          color="primary"
          (click)="openCreateDialog()"
          aria-label="Create new API client"
        >
          <mat-icon>add_circle</mat-icon>
          <span>Create Client</span>
        </button>
      </mc-page-header>

      <mat-card class="table-card">
        @if (loading()) {
          <mc-loading-skeleton [rows]="5" />
        } @else if (error()) {
          <mc-error-state
            title="Failed to load API clients"
            [message]="error()!"
            (retry)="loadClients()"
          />
        } @else if (clients().length === 0) {
          <mc-empty-state
            icon="key"
            title="No API clients found"
            message="No API client credentials have been registered yet."
          >
            <button mat-stroked-button color="primary" (click)="openCreateDialog()">
              <mat-icon>add_circle</mat-icon>
              <span>Register First Client</span>
            </button>
          </mc-empty-state>
        } @else {
          <div class="table-responsive">
            <table mat-table [dataSource]="clients()" class="clients-table">
              <!-- Name Column -->
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Client Name</th>
                <td mat-cell *matCellDef="let client" class="font-medium">
                  {{ client.name }}
                </td>
              </ng-container>

              <!-- Prefix Column -->
              <ng-container matColumnDef="keyPrefix">
                <th mat-header-cell *matHeaderCellDef>Key Prefix</th>
                <td mat-cell *matCellDef="let client">
                  <code class="prefix-badge">{{ client.keyPrefix }}...</code>
                </td>
              </ng-container>

              <!-- Status Column -->
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let client">
                  <mc-status-chip [status]="client.status" [customLabel]="client.status" />
                </td>
              </ng-container>

              <!-- Created Column -->
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef>Created (Asia/Kathmandu)</th>
                <td mat-cell *matCellDef="let client" class="text-muted tabular-nums">
                  {{ client.createdAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                </td>
              </ng-container>

              <!-- Last Used Column -->
              <ng-container matColumnDef="lastUsedAt">
                <th mat-header-cell *matHeaderCellDef>Last Used</th>
                <td mat-cell *matCellDef="let client" class="text-muted tabular-nums">
                  @if (client.lastUsedAt) {
                    {{ client.lastUsedAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                  } @else {
                    <span class="text-secondary">Never</span>
                  }
                </td>
              </ng-container>

              <!-- Actions Column -->
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef class="actions-header">Actions</th>
                <td mat-cell *matCellDef="let client" class="actions-cell">
                  @if (client.status === 'ACTIVE') {
                    <button
                      mat-button
                      color="warn"
                      (click)="confirmRevoke(client)"
                      [attr.aria-label]="'Revoke ' + client.name"
                    >
                      <mat-icon>block</mat-icon>
                      <span>Revoke</span>
                    </button>
                  } @else {
                    <span class="text-muted text-sm">Revoked</span>
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
            [pageSizeOptions]="[10, 20, 50]"
            (page)="onPageChange($event)"
            showFirstLastButtons
            aria-label="Select API clients page"
          />
        }
      </mat-card>
    </div>
  `,
  styles: [
    `
      .api-clients-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
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
      .clients-table {
        width: 100%;
        border-collapse: collapse;
      }
      .font-medium {
        font-weight: 500;
        color: var(--mc-text-primary, #0f172a);
      }
      .prefix-badge {
        font-family: monospace;
        background-color: #f1f5f9;
        color: #334155;
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 0.8125rem;
      }
      .text-muted {
        color: var(--mc-text-secondary, #64748b);
      }
      .text-secondary {
        color: #94a3b8;
        font-style: italic;
      }
      .tabular-nums {
        font-variant-numeric: tabular-nums;
      }
      .text-sm {
        font-size: 0.75rem;
      }
      .actions-header,
      .actions-cell {
        text-align: right;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiClientsListComponent implements OnInit {
  private readonly apiClientsApi = inject(ApiClientsApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);

  readonly displayedColumns = ['name', 'keyPrefix', 'status', 'createdAt', 'lastUsedAt', 'actions'];

  readonly clients = signal<ApiClientResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly totalElements = signal(0);

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.loading.set(true);
    this.error.set(null);

    this.apiClientsApi.listClients(this.pageIndex(), this.pageSize(), 'createdAt,desc').subscribe({
      next: (res) => {
        this.clients.set(res.content ?? []);
        this.totalElements.set(res.page?.totalElements ?? res.content?.length ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load API clients list. Please try again.');
        this.loading.set(false);
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadClients();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(CreateApiClientDialogComponent, {
      width: '520px',
      disableClose: true,
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.loadClients();
      }
    });
  }

  confirmRevoke(client: ApiClientResponse): void {
    if (!client.id) {
      return;
    }

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Revoke API Client Credentials',
        message: `Are you sure you want to revoke "${client.name}"? Any external services using this API key will immediately be denied authorization. This action cannot be undone.`,
        confirmText: 'Revoke Key',
        confirmColor: 'warn',
      },
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.apiClientsApi.revokeClient(client.id!).subscribe({
          next: () => {
            this.notify.success(`API client "${client.name ?? ''}" has been revoked`);
            this.loadClients();
          },
          error: () => {
            this.notify.error('Failed to revoke API client credential');
          },
        });
      }
    });
  }
}
