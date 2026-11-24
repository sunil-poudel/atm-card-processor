import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationService } from '../../../core/notify/notification.service';
import { DisputesApiService } from '../disputes-api.service';

export interface SubmitEvidenceDialogData {
  disputeId: string;
}

@Component({
  selector: 'mc-submit-evidence-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Submit Dispute Evidence</h2>

    <mat-dialog-content>
      <p class="dialog-desc">
        Submitting evidence moves this dispute to <strong>UNDER_REVIEW</strong> so an officer can
        make a final resolution.
      </p>

      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Evidence Summary & References</mat-label>
          <textarea
            matInput
            rows="4"
            formControlName="note"
            placeholder="Document IDs, invoice numbers, merchant statements..."
          ></textarea>
          @if (form.get('note')?.hasError('required')) {
            <mat-error>Evidence note is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="submitting()">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        (click)="submit()"
        [disabled]="form.invalid || submitting()"
      >
        @if (submitting()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Submitting...</span>
        } @else {
          <span>Submit Evidence</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-desc {
        font-size: 13px;
        color: #475569;
        margin-bottom: 16px;
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
export class SubmitEvidenceDialogComponent {
  readonly data: SubmitEvidenceDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<SubmitEvidenceDialogComponent>);
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

    this.disputesApi.submitEvidence(this.data.disputeId, { note }).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.notify.success('Evidence submitted for review');
        this.dialogRef.close(res);
      },
      error: (err) => {
        this.submitting.set(false);
        this.notify.error(err?.detail || 'Failed to submit evidence');
      },
    });
  }
}
