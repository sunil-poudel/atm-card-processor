import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { ReferenceDataService } from '../../../core/reference/reference-data.service';
import { CardResponse, CardsApiService } from '../cards-api.service';

export interface EditBlockedMccsDialogData {
  card: CardResponse;
}

@Component({
  selector: 'mc-edit-blocked-mccs-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Edit Blocked MCCs</h2>
    <mat-dialog-content>
      @if (errorMessage()) {
        <div class="alert alert-danger">{{ errorMessage() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" id="editBlockedMccsForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Blocked Merchant Categories</mat-label>
          <mat-select formControlName="blockedMccs" multiple>
            @for (entry of mccsList(); track entry.code) {
              <mat-option [value]="entry.code">
                {{ entry.code }} ({{ entry.description }})
              </mat-option>
            }
          </mat-select>
          <mat-hint>Select any MCCs that this card should automatically decline</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="loading()">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="submit"
        form="editBlockedMccsForm"
        [disabled]="loading()"
      >
        @if (loading()) {
          <mat-spinner diameter="18"></mat-spinner>
          <span>Saving...</span>
        } @else {
          <span>Save Changes</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full-width {
        width: 100%;
        min-width: 420px;
        margin-top: 8px;
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
export class EditBlockedMccsDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly cardsApi = inject(CardsApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<EditBlockedMccsDialogComponent>);
  readonly data = inject<EditBlockedMccsDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly mccsList = signal<{ code: string; description: string }[]>([]);

  readonly form = this.fb.nonNullable.group({
    blockedMccs: [[] as string[]],
  });

  ngOnInit(): void {
    if (this.data.card.blockedMccs) {
      this.form.controls.blockedMccs.setValue(this.data.card.blockedMccs);
    }

    this.refDataService.getReferenceData().subscribe({
      next: (data) => {
        if (data.mccs) {
          const list = Object.entries(data.mccs).map(([code, description]) => ({
            code,
            description,
          }));
          this.mccsList.set(list);
        }
      },
    });
  }

  onSubmit(): void {
    if (this.loading() || !this.data.card.id) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const blockedMccs = this.form.controls.blockedMccs.value;
    const version = (this.data.card as unknown as { version?: number }).version;

    this.cardsApi.updateBlockedMccs(this.data.card.id, blockedMccs, version).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.notify.success('Blocked MCCs updated successfully');
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
          this.errorMessage.set(err.detail || 'Failed to update blocked MCCs');
        } else {
          this.errorMessage.set('Failed to update blocked MCCs');
        }
      },
    });
  }
}
