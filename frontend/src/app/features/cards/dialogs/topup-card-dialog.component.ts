import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationService } from '../../../core/notify/notification.service';
import { FormErrorComponent } from '../../../shared/forms/form-error.component';
import { parseMajorToMinor } from '../../../shared/forms/money-parser';
import { CardResponse, CardsApiService, LedgerEntryResponse } from '../cards-api.service';

export interface TopUpCardDialogData {
  card: CardResponse;
}

@Component({
  selector: 'mc-topup-card-dialog',
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
    <h2 mat-dialog-title>Top Up Card Balance</h2>
    <mat-dialog-content>
      <p class="dialog-description">
        Credit funds directly to card <strong>{{ data.card.maskedPan }}</strong> ({{
          data.card.holderName
        }}).
      </p>

      @if (errorMessage()) {
        <div class="alert alert-danger">{{ errorMessage() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" id="topUpCardForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Top-Up Amount</mat-label>
          <input matInput formControlName="amount" placeholder="0.00" />
          <span matTextPrefix class="prefix-currency">{{ data.card.currency }}&nbsp;</span>
          <mc-form-error [control]="form.controls.amount" fieldName="Amount"></mc-form-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description (Optional)</mat-label>
          <input
            matInput
            formControlName="description"
            placeholder="e.g. Manual balance top-up by operator"
            maxlength="255"
          />
          <mc-form-error
            [control]="form.controls.description"
            fieldName="Description"
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
        form="topUpCardForm"
        [disabled]="form.invalid || loading()"
      >
        @if (loading()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Processing...</span>
        } @else {
          <span>Confirm Top Up</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full-width {
        width: 100%;
      }
      .dialog-description {
        color: #475569;
        font-size: 14px;
        margin-bottom: 16px;
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
export class TopUpCardDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly cardsApi = inject(CardsApiService);
  private readonly notify = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<TopUpCardDialogComponent>);
  readonly data = inject<TopUpCardDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    amount: ['', [Validators.required]],
    description: ['Manual balance top-up'],
  });

  onSubmit(): void {
    if (this.form.invalid || this.loading() || !this.data.card.id) {
      return;
    }

    let amountMinor = 0;
    try {
      amountMinor = parseMajorToMinor(this.form.controls.amount.value);
    } catch {
      this.form.controls.amount.setErrors({ serverError: 'Invalid numeric amount' });
      return;
    }

    if (amountMinor <= 0) {
      this.form.controls.amount.setErrors({ serverError: 'Amount must be greater than zero' });
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const description = this.form.controls.description.value.trim() || undefined;

    this.cardsApi.topUpCard(this.data.card.id, amountMinor, description).subscribe({
      next: (res: LedgerEntryResponse) => {
        this.loading.set(false);
        this.notify.success('Card successfully topped up');
        this.dialogRef.close(res);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.detail || 'Failed to top up card');
      },
    });
  }
}
