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
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthStore } from '../../core/auth/auth.store';
import { NotificationService } from '../../core/notify/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { LoadingSkeletonComponent } from '../../shared/ui/loading-skeleton.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import {
  FraudRuleDialogComponent,
  FraudRuleDialogData,
} from './dialogs/fraud-rule-dialog.component';
import { FraudRuleResponse, FraudRulesApiService } from './fraud-rules-api.service';

@Component({
  selector: 'mc-fraud-rules-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatDialogModule,
    MatTooltipModule,
    PageHeaderComponent,
    StatusChipComponent,
    LoadingSkeletonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  template: `
    <mc-page-header
      title="Fraud Rules"
      subtitle="Evaluation rules and risk thresholds for transaction authorizations"
    >
      <div actions>
        @if (isAdmin()) {
          <button mat-flat-button color="primary" (click)="openCreateDialog()">
            <mat-icon>add</mat-icon>
            <span>Create Rule</span>
          </button>
        }
      </div>
    </mc-page-header>

    <!-- Info Banner explaining Flag / Decline mechanics -->
    <div class="threshold-banner">
      <div class="thresh-item">
        <span class="thresh-badge flag">Score &ge; 50</span>
        <span class="thresh-desc"
          >Transaction is <strong>FLAGGED</strong> for dispute/fraud review</span
        >
      </div>
      <div class="thresh-divider"></div>
      <div class="thresh-item">
        <span class="thresh-badge decline">Score &ge; 80 or Action DECLINE</span>
        <span class="thresh-desc"
          >Transaction is immediately <strong>DECLINED</strong> (code 59)</span
        >
      </div>
    </div>

    @if (loading()) {
      <div class="skeleton-wrap">
        <mc-loading-skeleton [rows]="6"></mc-loading-skeleton>
      </div>
    } @else if (error()) {
      <mc-error-state
        title="Failed to load fraud rules"
        [message]="error() || ''"
        (retry)="loadRules()"
      ></mc-error-state>
    } @else if (rules().length === 0) {
      <mc-empty-state
        icon="security"
        title="No Fraud Rules Found"
        message="Configure risk rules to evaluate incoming payment authorizations."
      ></mc-empty-state>
    } @else {
      <div class="table-container">
        <table mat-table [dataSource]="rules()" class="mc-dense-table">
          <!-- Name Column -->
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Rule Name</th>
            <td mat-cell *matCellDef="let r">
              <div class="rule-title-cell">
                <span class="font-semibold text-main">{{ r.name }}</span>
                @if (r.description) {
                  <span class="text-muted desc-text">{{ r.description }}</span>
                }
              </div>
            </td>
          </ng-container>

          <!-- Type Column -->
          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let r">
              <span class="type-pill mono">{{ r.type }}</span>
            </td>
          </ng-container>

          <!-- Parameters Summary Column -->
          <ng-container matColumnDef="parameters">
            <th mat-header-cell *matHeaderCellDef>Parameters</th>
            <td mat-cell *matCellDef="let r">
              <span class="param-summary">{{ formatParams(r) }}</span>
            </td>
          </ng-container>

          <!-- Score Points Column -->
          <ng-container matColumnDef="scorePoints">
            <th mat-header-cell *matHeaderCellDef class="center-cell">Points</th>
            <td mat-cell *matCellDef="let r" class="center-cell">
              <span class="points-badge">+{{ r.scorePoints }}</span>
            </td>
          </ng-container>

          <!-- Action Column -->
          <ng-container matColumnDef="action">
            <th mat-header-cell *matHeaderCellDef class="center-cell">Action</th>
            <td mat-cell *matCellDef="let r" class="center-cell">
              <mc-status-chip [status]="r.action || 'FLAG'"></mc-status-chip>
            </td>
          </ng-container>

          <!-- Enabled Toggle Column -->
          <ng-container matColumnDef="enabled">
            <th mat-header-cell *matHeaderCellDef class="center-cell">Enabled</th>
            <td mat-cell *matCellDef="let r" class="center-cell">
              @if (isAdmin()) {
                <mat-slide-toggle
                  [checked]="r.enabled"
                  color="primary"
                  (change)="toggleRule(r, $event.checked)"
                  [disabled]="togglingId() === r.id"
                  [attr.aria-label]="'Toggle rule ' + r.name"
                ></mat-slide-toggle>
              } @else {
                <span class="enabled-badge {{ r.enabled ? 'on' : 'off' }}">
                  {{ r.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              }
            </td>
          </ng-container>

          <!-- Last Updated Column -->
          <ng-container matColumnDef="updatedAt">
            <th mat-header-cell *matHeaderCellDef>Last Updated</th>
            <td mat-cell *matCellDef="let r" class="text-muted tabular-nums">
              {{ r.updatedAt | date: 'yyyy-MM-dd HH:mm' : '+0545' }}
            </td>
          </ng-container>

          <!-- Actions Column -->
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="actions-header">Actions</th>
            <td mat-cell *matCellDef="let r" class="actions-cell">
              <button
                mat-icon-button
                (click)="openEditDialog(r)"
                [matTooltip]="isAdmin() ? 'Edit rule' : 'View rule details'"
              >
                <mat-icon>{{ isAdmin() ? 'edit' : 'visibility' }}</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
        </table>
      </div>
    }
  `,
  styles: [
    `
      .threshold-banner {
        display: flex;
        align-items: center;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 12px 20px;
        margin-bottom: 20px;
        gap: 24px;
      }
      .thresh-item {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .thresh-badge {
        font-size: 12px;
        font-weight: 700;
        padding: 4px 10px;
        border-radius: 12px;
        letter-spacing: 0.3px;
      }
      .thresh-badge.flag {
        background: #fef3c7;
        color: #92400e;
        border: 1px solid #fcd34d;
      }
      .thresh-badge.decline {
        background: #fee2e2;
        color: #991b1b;
        border: 1px solid #fca5a5;
      }
      .thresh-desc {
        font-size: 13px;
        color: #334155;
      }
      .thresh-divider {
        width: 1px;
        height: 24px;
        background: #cbd5e1;
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
      .rule-title-cell {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 6px 0;
      }
      .text-main {
        color: #0f172a;
      }
      .desc-text {
        font-size: 12px;
        line-height: 1.4;
      }

      .type-pill {
        display: inline-block;
        font-size: 11px;
        font-weight: 600;
        background: #f1f5f9;
        color: #334155;
        padding: 2px 8px;
        border-radius: 4px;
      }

      .param-summary {
        font-size: 13px;
        color: #1e293b;
      }

      .points-badge {
        font-family: var(--mc-font-mono);
        font-size: 13px;
        font-weight: 700;
        color: #4338ca;
        background: #e0e7ff;
        padding: 2px 8px;
        border-radius: 12px;
      }

      .enabled-badge {
        font-size: 12px;
        font-weight: 600;
        padding: 2px 8px;
        border-radius: 10px;
      }
      .enabled-badge.on {
        background: #dcfce7;
        color: #166534;
      }
      .enabled-badge.off {
        background: #f1f5f9;
        color: #64748b;
      }

      .center-cell {
        text-align: center;
      }
      .actions-header,
      .actions-cell {
        text-align: right;
        padding-right: 16px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FraudRulesListComponent implements OnInit {
  private readonly fraudApi = inject(FraudRulesApiService);
  private readonly authStore = inject(AuthStore);
  private readonly notify = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = this.authStore.isAdmin;
  readonly rules = signal<FraudRuleResponse[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly togglingId = signal<string | null>(null);

  readonly displayedColumns: string[] = [
    'name',
    'type',
    'parameters',
    'scorePoints',
    'action',
    'enabled',
    'updatedAt',
    'actions',
  ];

  ngOnInit(): void {
    this.loadRules();
  }

  loadRules(): void {
    this.loading.set(true);
    this.error.set(null);

    this.fraudApi
      .listRules()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rules) => {
          this.rules.set(rules);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.detail || 'Failed to load fraud rules');
        },
      });
  }

  formatParams(rule: FraudRuleResponse): string {
    if (rule.type === 'MAX_AMOUNT') {
      const amount =
        rule.thresholdValue !== undefined
          ? (rule.thresholdValue / 100).toLocaleString('en-US', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })
          : '0.00';
      return `Amount > ${amount}`;
    }
    if (rule.type === 'VELOCITY') {
      return `>${rule.thresholdValue} attempts in ${rule.windowSeconds}s`;
    }
    if (rule.type === 'BLOCKED_MCC') {
      return `MCCs: ${(rule.mccCodes || []).join(', ') || 'None'}`;
    }
    return '-';
  }

  toggleRule(rule: FraudRuleResponse, enabled: boolean): void {
    if (!rule.id || this.togglingId()) {
      return;
    }

    this.togglingId.set(rule.id);
    const obs$ = enabled ? this.fraudApi.enableRule(rule.id) : this.fraudApi.disableRule(rule.id);

    obs$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.togglingId.set(null);
        this.notify.success(`Rule "${rule.name}" ${enabled ? 'enabled' : 'disabled'} successfully`);
        const list = this.rules().map((r) => (r.id === updated.id ? updated : r));
        this.rules.set(list);
      },
      error: (err) => {
        this.togglingId.set(null);
        this.notify.error(err?.detail || 'Failed to update rule status');
        this.loadRules(); // reset UI state
      },
    });
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(FraudRuleDialogComponent, {
      data: { readOnly: false } as FraudRuleDialogData,
      width: '560px',
    });

    dialogRef.afterClosed().subscribe((res) => {
      if (res) {
        this.loadRules();
      }
    });
  }

  openEditDialog(rule: FraudRuleResponse): void {
    const dialogRef = this.dialog.open(FraudRuleDialogComponent, {
      data: {
        rule,
        readOnly: !this.isAdmin(),
      } as FraudRuleDialogData,
      width: '560px',
    });

    dialogRef.afterClosed().subscribe((res) => {
      if (res) {
        this.loadRules();
      }
    });
  }
}
