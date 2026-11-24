import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationService } from '../../../core/notify/notification.service';
import { DisputesApiService } from '../disputes-api.service';

export interface RequestEvidenceDialogData {
  disputeId: string;
}

@Component({
  selector: 'mc-request-evidence-dialog',
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
    <h2 mat-dialog-title>Request Dispute Evidence</h2>

    <mat-dialog-content>
      <p class="dialog-desc">
        Requesting evidence transitions this dispute to <strong>EVIDENCE_REQUESTED</strong> and
        starts the 14-day evidence due countdown.
      </p>

      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Request Note (optional)</mat-label>
          <textarea
            matInput
            rows="3"
            formControlName="note"
            placeholder="Specify documents or receipt proofs requested..."
          ></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="submitting()">Cancel</button>
      <button mat-flat-button color="primary" (click)="submit()" [disabled]="submitting()">
        @if (submitting()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Submitting...</span>
        } @else {
          <span>Request Evidence</span>
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
        min-width: 420px;
      }
      .full-width {
        width: 100%;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestEvidenceDialogComponent {
  readonly data: RequestEvidenceDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<RequestEvidenceDialogComponent>);
  private readonly fb = inject(FormBuilder);
  private readonly disputesApi = inject(DisputesApiService);
  private readonly notify = inject(NotificationService);

  readonly submitting = signal<boolean>(false);
  readonly form: FormGroup = this.fb.group({
    note: [''],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    const note = this.form.value.note?.trim();

    this.disputesApi.requestEvidence(this.data.disputeId, { note }).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.notify.success('Evidence requested successfully');
        this.dialogRef.close(res);
      },
      error: (err) => {
        this.submitting.set(false);
        this.notify.error(err?.detail || 'Failed to request evidence');
      },
    });
  }
}
