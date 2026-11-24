import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UserDialogComponent } from './dialogs/user-dialog.component';
import { UserResponse, UsersApiService } from './users-api.service';

@Component({
  selector: 'mc-users-list',
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
    <div class="users-page">
      <mc-page-header
        title="Staff Users"
        subtitle="Manage back-office accounts, role permissions, and access credentials"
      >
        <button
          mat-flat-button
          color="primary"
          (click)="openCreateDialog()"
          aria-label="Add new staff user"
        >
          <mat-icon>person_add</mat-icon>
          <span>Add User</span>
        </button>
      </mc-page-header>

      <mat-card class="table-card">
        @if (loading()) {
          <mc-loading-skeleton [rows]="5" />
        } @else if (error()) {
          <mc-error-state title="Failed to load users" [message]="error()!" (retry)="loadUsers()" />
        } @else if (users().length === 0) {
          <mc-empty-state
            icon="group"
            title="No staff users found"
            message="No staff user accounts have been created yet."
          >
            <button mat-stroked-button color="primary" (click)="openCreateDialog()">
              <mat-icon>person_add</mat-icon>
              <span>Create First User</span>
            </button>
          </mc-empty-state>
        } @else {
          <div class="table-responsive">
            <table mat-table [dataSource]="users()" class="users-table">
              <!-- Name Column -->
              <ng-container matColumnDef="displayName">
                <th mat-header-cell *matHeaderCellDef>User</th>
                <td mat-cell *matCellDef="let user">
                  <div class="user-cell">
                    <div class="user-avatar" [attr.aria-hidden]="true">
                      {{ (user.displayName || user.email || 'U')[0].toUpperCase() }}
                    </div>
                    <div class="user-meta">
                      <span class="user-name">{{ user.displayName }}</span>
                      <span class="user-email-sub text-muted">{{ user.email }}</span>
                    </div>
                  </div>
                </td>
              </ng-container>

              <!-- Role Column -->
              <ng-container matColumnDef="role">
                <th mat-header-cell *matHeaderCellDef>Role</th>
                <td mat-cell *matCellDef="let user">
                  <mc-status-chip [status]="user.role" [customLabel]="user.role" />
                </td>
              </ng-container>

              <!-- Status Column -->
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let user">
                  <div class="status-cell">
                    @if (user.locked) {
                      <mc-status-chip status="LOCKED" customLabel="Locked" />
                    } @else if (user.enabled) {
                      <mc-status-chip status="ACTIVE" customLabel="Enabled" />
                    } @else {
                      <mc-status-chip status="INACTIVE" customLabel="Disabled" />
                    }
                  </div>
                </td>
              </ng-container>

              <!-- Created Column -->
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef>Created (Asia/Kathmandu)</th>
                <td mat-cell *matCellDef="let user" class="text-muted tabular-nums">
                  {{ user.createdAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
                </td>
              </ng-container>

              <!-- Actions Column -->
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef class="actions-header">Actions</th>
                <td mat-cell *matCellDef="let user" class="actions-cell">
                  <button
                    mat-icon-button
                    color="primary"
                    matTooltip="Edit user"
                    [attr.aria-label]="'Edit ' + user.displayName"
                    (click)="openEditDialog(user)"
                  >
                    <mat-icon>edit</mat-icon>
                  </button>
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
            aria-label="Select staff users page"
          />
        }
      </mat-card>
    </div>
  `,
  styles: [
    `
      .users-page {
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
      .users-table {
        width: 100%;
        border-collapse: collapse;
      }
      .user-cell {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .user-avatar {
        width: 36px;
        height: 36px;
        border-radius: 50%;
        background-color: #e0e7ff;
        color: #4338ca;
        font-weight: 700;
        font-size: 0.875rem;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .user-meta {
        display: flex;
        flex-direction: column;
      }
      .user-name {
        font-weight: 600;
        color: var(--mc-text-primary, #0f172a);
      }
      .user-email-sub {
        font-size: 0.8125rem;
      }
      .text-muted {
        color: var(--mc-text-secondary, #64748b);
      }
      .tabular-nums {
        font-variant-numeric: tabular-nums;
      }
      .actions-header,
      .actions-cell {
        text-align: right;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersListComponent implements OnInit {
  private readonly usersApi = inject(UsersApiService);
  private readonly dialog = inject(MatDialog);

  readonly displayedColumns = ['displayName', 'role', 'status', 'createdAt', 'actions'];

  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly totalElements = signal(0);

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);

    this.usersApi.listUsers(this.pageIndex(), this.pageSize(), 'email,asc').subscribe({
      next: (res) => {
        this.users.set(res.content ?? []);
        this.totalElements.set(res.page?.totalElements ?? res.content?.length ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load user list. Please verify network or credentials.');
        this.loading.set(false);
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadUsers();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      width: '480px',
      data: { mode: 'create' },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.loadUsers();
      }
    });
  }

  openEditDialog(user: UserResponse): void {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      width: '480px',
      data: { mode: 'edit', user },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.loadUsers();
      }
    });
  }
}
