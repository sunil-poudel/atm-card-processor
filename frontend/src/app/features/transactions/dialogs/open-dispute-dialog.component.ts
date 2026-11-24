import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { ReferenceDataService } from '../../../core/reference/reference-data.service';
import { FormErrorComponent } from '../../../shared/forms/form-error.component';
import {
  DisputeDetail,
  OpenDisputeRequest,
  TransactionDetail,
  TransactionsApiService,
  TransactionSummary,
} from '../transactions-api.service';

export interface OpenDisputeDialogData {
  transaction: TransactionDetail | TransactionSummary;
}

@Component({
  selector: 'mc-open-dispute-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    FormErrorComponent,
  ],
  template: `
    <h2 mat-dialog-title>Open Dispute</h2>
    <mat-dialog-content>
      <p class="dialog-description">
        Initiate a formal dispute investigation on this transaction. If resolved in the cardholder's
        favor, the transaction amount will be credited back to the card.
      </p>

      @if (errorMessage()) {
        <div class="alert alert-danger">{{ errorMessage() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" id="openDisputeForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Dispute Reason</mat-label>
          <mat-select formControlName="reason">
            @for (r of reasons(); track r) {
              <mat-option [value]="r">{{ formatReason(r) }}</mat-option>
            }
          </mat-select>
          <mc-form-error [control]="form.controls.reason" fieldName="Reason"></mc-form-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Explanation</mat-label>
          <textarea
            matInput
            formControlName="explanation"
            rows="4"
            placeholder="Detailed description of the claim, evidence timeline, or cardholder report"
            maxlength="1000"
          ></textarea>
          <mat-hint align="end">{{ form.controls.explanation.value.length }}/1000</mat-hint>
          <mc-form-error
            [control]="form.controls.explanation"
            fieldName="Explanation"
          ></mc-form-error>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="loading()">Cancel</button>
      <button
        mat-flat-button
        color="warn"
        type="submit"
        form="openDisputeForm"
        [disabled]="form.invalid || loading()"
      >
        @if (loading()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Submitting...</span>
        } @else {
          <span>Open Dispute</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full-width {
        width: 100%;
        min-width: 440px;
        margin-bottom: 8px;
      }
      .dialog-description {
        color: #475569;
        font-size: 14px;
        margin-bottom: 16px;
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
export class OpenDisputeDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly transactionsApi = inject(TransactionsApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<OpenDisputeDialogComponent>);
  readonly data = inject<OpenDisputeDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly reasons = signal<string[]>([
    'UNAUTHORIZED_TRANSACTION',
    'DUPLICATE_CHARGE',
    'INCORRECT_AMOUNT',
    'GOODS_NOT_RECEIVED',
    'OTHER',
  ]);

  readonly form = this.fb.nonNullable.group({
    reason: ['UNAUTHORIZED_TRANSACTION', [Validators.required]],
    explanation: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(1000)]],
  });

  ngOnInit(): void {
    this.refDataService.getReferenceData().subscribe({
      next: (data) => {
        if (data.disputeReasons?.length) {
          this.reasons.set(data.disputeReasons);
        }
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading() || !this.data.transaction.id) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { reason, explanation } = this.form.getRawValue();

    this.transactionsApi
      .openDispute({
        transactionId: this.data.transaction.id,
        reason: reason as OpenDisputeRequest['reason'],
        description: explanation.trim(),
      })
      .subscribe({
        next: (dispute: DisputeDetail) => {
          this.loading.set(false);
          this.notify.success('Dispute opened successfully');
          this.dialogRef.close(dispute);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof ApiError) {
            this.errorMessage.set(err.detail || 'Failed to open dispute');
          } else {
            this.errorMessage.set('An error occurred while opening the dispute');
          }
        },
      });
  }

  formatReason(reason: string): string {
    return reason.replace(/_/g, ' ');
  }
}
