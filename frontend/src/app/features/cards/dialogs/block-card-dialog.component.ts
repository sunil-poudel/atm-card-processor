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
import { CardResponse, CardsApiService } from '../cards-api.service';

export interface BlockCardDialogData {
  card: CardResponse;
}

@Component({
  selector: 'mc-block-card-dialog',
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
    <h2 mat-dialog-title>Block Card</h2>
    <mat-dialog-content>
      <p class="dialog-description">
        Blocking this card will immediately prevent all new authorizations. Existing settled
        transactions are not affected.
      </p>

      @if (errorMessage()) {
        <div class="alert alert-danger">{{ errorMessage() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" id="blockCardForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason for Blocking</mat-label>
          <textarea
            matInput
            formControlName="reason"
            rows="3"
            placeholder="e.g. Suspected fraudulent activity reported by holder"
            maxlength="255"
          ></textarea>
          <mat-hint align="end">{{ form.controls.reason.value.length }}/255</mat-hint>
          <mc-form-error [control]="form.controls.reason" fieldName="Reason"></mc-form-error>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="loading()">Cancel</button>
      <button
        mat-flat-button
        color="warn"
        type="submit"
        form="blockCardForm"
        [disabled]="form.invalid || loading()"
      >
        @if (loading()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Blocking...</span>
        } @else {
          <span>Block Card</span>
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
export class BlockCardDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly cardsApi = inject(CardsApiService);
  private readonly notify = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<BlockCardDialogComponent>);
  readonly data = inject<BlockCardDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(255)]],
  });

  onSubmit(): void {
    if (this.form.invalid || this.loading() || !this.data.card.id) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const reason = this.form.controls.reason.value.trim();

    this.cardsApi.blockCard(this.data.card.id, reason).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.notify.success('Card blocked successfully');
        this.dialogRef.close(res);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.detail || 'Failed to block card');
      },
    });
  }
}
