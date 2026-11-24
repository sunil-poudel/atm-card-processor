import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { FormErrorComponent } from '../../../shared/forms/form-error.component';
import { minorToMajorString, parseMajorToMinor } from '../../../shared/forms/money-parser';
import { CardResponse, CardsApiService } from '../cards-api.service';

export interface EditLimitsDialogData {
  card: CardResponse;
}

@Component({
  selector: 'mc-edit-limits-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    FormErrorComponent,
  ],
  template: `
    <h2 mat-dialog-title>Edit Card Limits</h2>
    <mat-dialog-content>
      @if (errorMessage()) {
        <div class="alert alert-danger">{{ errorMessage() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" id="editLimitsForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Per-Transaction Limit</mat-label>
          <input matInput formControlName="perTransactionLimit" placeholder="0.00" />
          <span matTextPrefix class="prefix-currency">{{ data.card.currency }}&nbsp;</span>
          <mc-form-error
            [control]="form.controls.perTransactionLimit"
            fieldName="Per-Transaction Limit"
          ></mc-form-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Daily Limit</mat-label>
          <input matInput formControlName="dailyLimit" placeholder="0.00" />
          <span matTextPrefix class="prefix-currency">{{ data.card.currency }}&nbsp;</span>
          <mc-form-error
            [control]="form.controls.dailyLimit"
            fieldName="Daily Limit"
          ></mc-form-error>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="loading()">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="submit"
        form="editLimitsForm"
        [disabled]="form.invalid || loading()"
      >
        @if (loading()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Saving...</span>
        } @else {
          <span>Save Limits</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full-width {
        width: 100%;
        margin-bottom: 8px;
      }
      .prefix-currency {
        color: #64748b;
        font-weight: 500;
        font-size: 13px;
      }
      .alert-danger {
        background-color: #fee2e2;
        color: #b91c1c;
        border: 1px solid #fca5a5;
        padding: 10px;
        border-radius: 6px;
        font-size: 13px;
        margin-bottom: 12px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditLimitsDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly cardsApi = inject(CardsApiService);
  private readonly notify = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<EditLimitsDialogComponent>);
  readonly data = inject<EditLimitsDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    perTransactionLimit: ['', [Validators.required]],
    dailyLimit: ['', [Validators.required]],
  });

  ngOnInit(): void {
    const card = this.data.card;
    this.form.patchValue({
      perTransactionLimit: minorToMajorString(card.perTransactionLimitMinor || 0),
      dailyLimit: minorToMajorString(card.dailyLimitMinor || 0),
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading() || !this.data.card.id) {
      return;
    }

    let perTxMinor: number;
    let dailyMinor: number;

    try {
      perTxMinor = parseMajorToMinor(this.form.controls.perTransactionLimit.value);
    } catch {
      this.form.controls.perTransactionLimit.setErrors({ serverError: 'Invalid numeric amount' });
      return;
    }

    try {
      dailyMinor = parseMajorToMinor(this.form.controls.dailyLimit.value);
    } catch {
      this.form.controls.dailyLimit.setErrors({ serverError: 'Invalid numeric amount' });
      return;
    }

    if (dailyMinor < perTxMinor) {
      this.form.controls.dailyLimit.setErrors({
        serverError: 'Daily limit must be greater than or equal to per-transaction limit',
      });
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const version = (this.data.card as unknown as { version?: number }).version;

    this.cardsApi.updateLimits(this.data.card.id, perTxMinor, dailyMinor, version).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.notify.success('Card limits updated successfully');
        this.dialogRef.close(res);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err instanceof ApiError) {
          if (err.code === 'OPTIMISTIC_LOCK_FAILURE') {
            this.errorMessage.set(
              'The card was modified concurrently by another process. Please close this dialog, refresh, and try again.',
            );
            return;
          }
          this.errorMessage.set(err.detail || 'Failed to update limits');
        } else {
          this.errorMessage.set('Failed to update limits');
        }
      },
    });
  }
}
