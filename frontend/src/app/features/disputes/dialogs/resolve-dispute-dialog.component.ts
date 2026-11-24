import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationService } from '../../../core/notify/notification.service';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { DisputesApiService } from '../disputes-api.service';

export interface ResolveDisputeDialogData {
  disputeId: string;
  outcome: 'WON' | 'LOST';
  amountMinor: number;
  currency: string;
}

@Component({
  selector: 'mc-resolve-dispute-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
  ],
  template: `
    <h2 mat-dialog-title>
      Resolve Dispute:
      <span class="outcome-badge {{ data.outcome.toLowerCase() }}">{{ data.outcome }}</span>
    </h2>

    <mat-dialog-content>
      @if (data.outcome === 'WON') {
        <div class="money-effect-banner won">
          <mat-icon>account_balance_wallet</mat-icon>
          <div>
            <strong>Financial Effect:</strong>
            <p>
              Resolving WON will immediately credit
              <strong>{{ data.amountMinor | money: data.currency }}</strong> back to the cardholder
              via an immutable <code>DISPUTE_CREDIT</code> ledger entry.
            </p>
          </div>
        </div>
      } @else {
        <div class="money-effect-banner lost">
          <mat-icon>cancel</mat-icon>
          <div>
            <strong>No Financial Adjustment:</strong>
            <p>
              Resolving LOST marks the dispute closed in favor of the merchant. No funds are
              credited to the card.
            </p>
          </div>
        </div>
      }

      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Resolution Findings & Explanation</mat-label>
          <textarea
            matInput
            rows="4"
            formControlName="note"
            placeholder="Explain the basis for this determination..."
          ></textarea>
          @if (form.get('note')?.hasError('required')) {
            <mat-error>Resolution note is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="submitting()">Cancel</button>
      <button
        mat-flat-button
        [color]="data.outcome === 'WON' ? 'primary' : 'warn'"
        (click)="submit()"
        [disabled]="form.invalid || submitting()"
      >
        @if (submitting()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Resolving...</span>
        } @else {
          <span>Confirm Resolution ({{ data.outcome }})</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .outcome-badge {
        font-size: 14px;
        font-weight: 700;
        padding: 2px 8px;
        border-radius: 4px;
      }
      .outcome-badge.won {
        background: #dcfce7;
        color: #166534;
      }
      .outcome-badge.lost {
        background: #fee2e2;
        color: #991b1b;
      }

      .money-effect-banner {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        border-radius: 8px;
        padding: 12px 16px;
        margin-bottom: 20px;
        font-size: 13px;
      }
      .money-effect-banner.won {
        background: #f0fdf4;
        border: 1px solid #bbf7d0;
        color: #166534;
      }
      .money-effect-banner.won mat-icon {
        color: #16a34a;
      }
      .money-effect-banner.lost {
        background: #fef2f2;
        border: 1px solid #fecaca;
        color: #991b1b;
      }
      .money-effect-banner.lost mat-icon {
        color: #dc2626;
      }
      .money-effect-banner p {
        margin: 4px 0 0 0;
      }

      .dialog-form {
        min-width: 440px;
      }
      .full-width {
        width: 100%;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResolveDisputeDialogComponent {
  readonly data: ResolveDisputeDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<ResolveDisputeDialogComponent>);
  private readonly fb = inject(FormBuilder);
  private readonly disputesApi = inject(DisputesApiService);
  private readonly notify = inject(NotificationService);

  readonly submitting = signal<boolean>(false);
  readonly form: FormGroup = this.fb.group({
    note: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    const note = this.form.value.note?.trim();

    this.disputesApi
      .resolveDispute(this.data.disputeId, {
        outcome: this.data.outcome,
        note,
      })
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.notify.success(`Dispute resolved as ${this.data.outcome}`);
          this.dialogRef.close(res);
        },
        error: (err) => {
          this.submitting.set(false);
          this.notify.error(err?.detail || 'Failed to resolve dispute');
        },
      });
  }
}
