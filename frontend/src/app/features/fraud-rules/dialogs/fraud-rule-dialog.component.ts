import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { NotificationService } from '../../../core/notify/notification.service';
import { ReferenceDataService } from '../../../core/reference/reference-data.service';
import { parseMajorToMinor } from '../../../shared/forms/money-parser';
import {
  CreateFraudRuleRequest,
  FraudRuleResponse,
  FraudRulesApiService,
  UpdateFraudRuleRequest,
} from '../fraud-rules-api.service';

export interface FraudRuleDialogData {
  rule?: FraudRuleResponse;
  readOnly?: boolean;
}

@Component({
  selector: 'mc-fraud-rule-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.readOnly ? 'View Fraud Rule' : isEdit() ? 'Edit Fraud Rule' : 'Create Fraud Rule' }}
    </h2>

    <mat-dialog-content class="dialog-content">
      <div class="info-alert">
        <mat-icon>info</mat-icon>
        <div class="alert-body">
          <strong>Risk Scoring Mechanics:</strong>
          <span
            >Points accumulate per transaction. Transactions with total risk score &ge; 50 are
            FLAGGED for review; score &ge; 80 or any rule with action DECLINE are immediately
            DECLINED.</span
          >
        </div>
      </div>

      <form [formGroup]="form" class="rule-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Rule Name</mat-label>
          <input matInput formControlName="name" placeholder="e.g. Velocity 5 in 1min" />
          @if (form.get('name')?.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea
            matInput
            rows="2"
            formControlName="description"
            placeholder="Details on what this rule detects..."
          ></textarea>
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Rule Type</mat-label>
            <mat-select formControlName="type" (selectionChange)="onTypeChange()">
              <mat-option value="MAX_AMOUNT">MAX_AMOUNT</mat-option>
              <mat-option value="VELOCITY">VELOCITY</mat-option>
              <mat-option value="BLOCKED_MCC">BLOCKED_MCC</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Action</mat-label>
            <mat-select formControlName="action">
              <mat-option value="FLAG">FLAG (adds score)</mat-option>
              <mat-option value="DECLINE">DECLINE (immediate decline)</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Score Points (0 - 100)</mat-label>
            <input matInput type="number" formControlName="scorePoints" min="0" max="100" />
            @if (form.get('scorePoints')?.hasError('required')) {
              <mat-error>Points are required</mat-error>
            }
          </mat-form-field>

          <div class="checkbox-col flex-1">
            <mat-checkbox formControlName="enabled" color="primary"> Rule Enabled </mat-checkbox>
          </div>
        </div>

        <!-- Dynamic Parameters based on Type -->
        @if (form.get('type')?.value === 'MAX_AMOUNT') {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Threshold Amount (in major units, e.g. 50000)</mat-label>
            <input matInput type="text" formControlName="thresholdMajor" />
            @if (form.get('thresholdMajor')?.hasError('required')) {
              <mat-error>Threshold amount is required</mat-error>
            }
          </mat-form-field>
        }

        @if (form.get('type')?.value === 'VELOCITY') {
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex-1">
              <mat-label>Count Threshold (e.g. 5 attempts)</mat-label>
              <input matInput type="number" formControlName="thresholdValue" min="1" />
              @if (form.get('thresholdValue')?.hasError('required')) {
                <mat-error>Threshold is required</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="flex-1">
              <mat-label>Time Window (seconds, e.g. 60)</mat-label>
              <input matInput type="number" formControlName="windowSeconds" min="1" />
              @if (form.get('windowSeconds')?.hasError('required')) {
                <mat-error>Window seconds is required</mat-error>
              }
            </mat-form-field>
          </div>
        }

        @if (form.get('type')?.value === 'BLOCKED_MCC') {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Select Blocked MCCs</mat-label>
            <mat-select formControlName="mccCodes" multiple>
              @for (entry of mccOptions(); track entry.code) {
                <mat-option [value]="entry.code"> {{ entry.code }} - {{ entry.label }} </mat-option>
              }
            </mat-select>
            @if (form.get('mccCodes')?.hasError('required')) {
              <mat-error>At least one MCC is required</mat-error>
            }
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">
        {{ data.readOnly ? 'Close' : 'Cancel' }}
      </button>

      @if (!data.readOnly) {
        <button
          mat-flat-button
          color="primary"
          (click)="save()"
          [disabled]="form.invalid || saving()"
        >
          @if (saving()) {
            <mat-spinner diameter="18"></mat-spinner>
            <span>Saving...</span>
          } @else {
            <span>{{ isEdit() ? 'Update Rule' : 'Create Rule' }}</span>
          }
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-content {
        min-width: 480px;
        padding-top: 16px;
      }
      .info-alert {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        background: #f0fdf4;
        border: 1px solid #bbf7d0;
        border-radius: 8px;
        padding: 12px 14px;
        margin-bottom: 20px;
        font-size: 13px;
        color: #166534;
      }
      .info-alert mat-icon {
        color: #16a34a;
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
      .alert-body {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .rule-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .full-width {
        width: 100%;
      }
      .form-row {
        display: flex;
        gap: 16px;
      }
      .flex-1 {
        flex: 1;
      }
      .checkbox-col {
        display: flex;
        align-items: center;
        padding-bottom: 20px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FraudRuleDialogComponent implements OnInit {
  readonly data: FraudRuleDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<FraudRuleDialogComponent>);
  private readonly fb = inject(FormBuilder);
  private readonly fraudApi = inject(FraudRulesApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);

  readonly isEdit = signal<boolean>(!!this.data.rule?.id);
  readonly saving = signal<boolean>(false);
  readonly mccOptions = signal<{ code: string; label: string }[]>([]);

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required]],
    description: [''],
    type: ['MAX_AMOUNT', [Validators.required]],
    action: ['FLAG', [Validators.required]],
    scorePoints: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
    enabled: [true],
    thresholdMajor: ['50000.00'],
    thresholdValue: [5],
    windowSeconds: [60],
    mccCodes: [[] as string[]],
  });

  ngOnInit(): void {
    this.loadMccs();

    if (this.data.rule) {
      const r = this.data.rule;
      const thresholdMajor =
        r.type === 'MAX_AMOUNT' && r.thresholdValue !== undefined
          ? (r.thresholdValue / 100).toFixed(2)
          : '50000.00';

      this.form.patchValue({
        name: r.name || '',
        description: r.description || '',
        type: r.type || 'MAX_AMOUNT',
        action: r.action || 'FLAG',
        scorePoints: r.scorePoints ?? 20,
        enabled: r.enabled ?? true,
        thresholdMajor,
        thresholdValue: r.thresholdValue ?? 5,
        windowSeconds: r.windowSeconds ?? 60,
        mccCodes: r.mccCodes || [],
      });

      // Type cannot be changed on edit
      this.form.get('type')?.disable();
    }

    if (this.data.readOnly) {
      this.form.disable();
    }
  }

  loadMccs(): void {
    this.refDataService.getReferenceData().subscribe({
      next: (ref) => {
        if (ref.mccs) {
          const opts = Object.entries(ref.mccs).map(([code, label]) => ({
            code,
            label,
          }));
          this.mccOptions.set(opts);
        }
      },
    });
  }

  onTypeChange(): void {
    // Keep validator logic clean
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }

    const val = this.form.getRawValue();
    const type = val.type;
    let thresholdValue: number | undefined;

    if (type === 'MAX_AMOUNT') {
      try {
        thresholdValue = parseMajorToMinor(val.thresholdMajor);
      } catch {
        this.notify.error('Invalid threshold amount format');
        return;
      }
    } else if (type === 'VELOCITY') {
      thresholdValue = Number(val.thresholdValue);
    }

    this.saving.set(true);

    if (this.isEdit()) {
      const rule = this.data.rule!;
      const updateReq: UpdateFraudRuleRequest = {
        name: val.name,
        description: val.description || undefined,
        enabled: val.enabled,
        thresholdValue,
        windowSeconds: type === 'VELOCITY' ? Number(val.windowSeconds) : undefined,
        mccCodes: type === 'BLOCKED_MCC' ? val.mccCodes : undefined,
        scorePoints: Number(val.scorePoints),
        action: val.action,
        version: rule.version ?? 0,
      };

      this.fraudApi.updateRule(rule.id!, updateReq).subscribe({
        next: (res) => {
          this.saving.set(false);
          this.notify.success('Fraud rule updated successfully');
          this.dialogRef.close(res);
        },
        error: (err) => {
          this.saving.set(false);
          if (err?.code === 'OPTIMISTIC_LOCK_FAILURE') {
            this.notify.error('Rule was modified by another user. Please refresh and retry.');
          } else {
            this.notify.error(err?.detail || 'Failed to update fraud rule');
          }
        },
      });
    } else {
      const createReq: CreateFraudRuleRequest = {
        name: val.name,
        description: val.description || undefined,
        type: val.type,
        enabled: val.enabled,
        thresholdValue,
        windowSeconds: type === 'VELOCITY' ? Number(val.windowSeconds) : undefined,
        mccCodes: type === 'BLOCKED_MCC' ? val.mccCodes : undefined,
        scorePoints: Number(val.scorePoints),
        action: val.action,
      };

      this.fraudApi.createRule(createReq).subscribe({
        next: (res) => {
          this.saving.set(false);
          this.notify.success('Fraud rule created successfully');
          this.dialogRef.close(res);
        },
        error: (err) => {
          this.saving.set(false);
          this.notify.error(err?.detail || 'Failed to create fraud rule');
        },
      });
    }
  }
}
