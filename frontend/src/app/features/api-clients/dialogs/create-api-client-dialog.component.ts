import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import { CopyButtonComponent } from '../../../shared/ui/copy-button.component';
import {
  ApiClientCreatedResponse,
  ApiClientsApiService,
  CreateApiClientRequest,
} from '../api-clients-api.service';

@Component({
  selector: 'mc-create-api-client-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CopyButtonComponent,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ createdClient() ? 'API Client Created' : 'Create API Client' }}
    </h2>

    <mat-dialog-content class="dialog-content">
      @if (!createdClient()) {
        <!-- Creation Form -->
        @if (serverError()) {
          <div class="error-banner" role="alert">
            <mat-icon>error_outline</mat-icon>
            <span>{{ serverError() }}</span>
          </div>
        }

        <p class="dialog-intro">
          Register a machine client credential (e.g., acquirer simulator, core banking webhook, or
          merchant POS connector).
        </p>

        <form [formGroup]="form" class="client-form" (ngSubmit)="onSubmit()">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Client Name</mat-label>
            <input
              matInput
              formControlName="name"
              placeholder="e.g. Simulator Acquirer Gateway"
              autocomplete="off"
            />
            <mat-icon matPrefix>badge</mat-icon>
            @if (form.get('name')?.hasError('required')) {
              <mat-error>Client name is required</mat-error>
            } @else if (form.get('name')?.hasError('minlength')) {
              <mat-error>Name must be at least 3 characters</mat-error>
            }
          </mat-form-field>
        </form>
      } @else {
        <!-- One-Time Reveal Panel -->
        <div class="reveal-container">
          <div class="warning-banner" role="alert">
            <mat-icon class="warning-icon">warning</mat-icon>
            <div class="warning-body">
              <strong>Save this API key now</strong>
              <p>
                This API key is only shown once and cannot be retrieved again from the system. If
                lost, you must revoke this client and generate a new key.
              </p>
            </div>
          </div>

          <div class="key-display-card">
            <div class="key-label">API Key:</div>
            <div class="key-box">
              <code class="raw-key">{{ createdClient()?.apiKey }}</code>
              <mc-copy-button
                [value]="createdClient()?.apiKey ?? ''"
                tooltip="Copy API key to clipboard"
              />
            </div>
          </div>

          <div class="client-info">
            <span class="info-label">Client Name:</span>
            <span class="info-value font-medium">{{ createdClient()?.name }}</span>
            <span class="info-label">Key Prefix:</span>
            <span class="info-value text-muted font-mono">{{ createdClient()?.keyPrefix }}</span>
          </div>

          <div class="ack-checkbox">
            <mat-checkbox [(ngModel)]="hasAcknowledged">
              I have safely copied and stored this API key in a secure location.
            </mat-checkbox>
          </div>
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end" class="dialog-actions">
      @if (!createdClient()) {
        <button mat-button mat-dialog-close [disabled]="submitting()">Cancel</button>
        <button
          mat-flat-button
          color="primary"
          (click)="onSubmit()"
          [disabled]="form.invalid || submitting()"
        >
          @if (submitting()) {
            <mat-spinner diameter="18" class="inline-spinner"></mat-spinner>
            <span>Creating...</span>
          } @else {
            <span>Create Client</span>
          }
        </button>
      } @else {
        <button
          mat-flat-button
          color="primary"
          (click)="onDone()"
          [disabled]="!hasAcknowledged"
          aria-label="Confirm key is stored and close dialog"
        >
          Done
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-content {
        min-width: 420px;
        max-width: 540px;
        padding-top: 12px;
      }
      .dialog-intro {
        font-size: 0.875rem;
        color: var(--mc-text-secondary, #64748b);
        margin-bottom: 16px;
      }
      .client-form {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .full-width {
        width: 100%;
      }
      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        background-color: var(--mc-danger-bg, #fef2f2);
        color: var(--mc-danger, #dc2626);
        border: 1px solid var(--mc-danger-border, #fecaca);
        padding: 10px 14px;
        border-radius: 8px;
        margin-bottom: 16px;
        font-size: 0.875rem;
      }
      .reveal-container {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .warning-banner {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        background-color: #fffbeb;
        color: #b45309;
        border: 1px solid #fde68a;
        padding: 12px 14px;
        border-radius: 8px;
        font-size: 0.875rem;
      }
      .warning-icon {
        color: #d97706;
      }
      .warning-body p {
        margin: 4px 0 0 0;
        font-size: 0.8125rem;
        line-height: 1.4;
      }
      .key-display-card {
        background-color: #f8fafc;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 12px 14px;
      }
      .key-label {
        font-size: 0.75rem;
        font-weight: 600;
        text-transform: uppercase;
        color: #475569;
        margin-bottom: 6px;
      }
      .key-box {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
      }
      .raw-key {
        font-family: monospace;
        font-size: 0.875rem;
        word-break: break-all;
        color: #0f172a;
        font-weight: 600;
      }
      .client-info {
        display: grid;
        grid-template-columns: 100px 1fr;
        row-gap: 6px;
        font-size: 0.875rem;
      }
      .info-label {
        color: #64748b;
      }
      .font-medium {
        font-weight: 500;
      }
      .font-mono {
        font-family: monospace;
      }
      .text-muted {
        color: #64748b;
      }
      .ack-checkbox {
        margin-top: 8px;
      }
      .inline-spinner {
        display: inline-block;
        margin-right: 8px;
      }
      .dialog-actions {
        padding: 16px 24px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateApiClientDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<CreateApiClientDialogComponent>);
  private readonly apiClientsApi = inject(ApiClientsApiService);
  private readonly notify = inject(NotificationService);

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
  });

  readonly submitting = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly createdClient = signal<ApiClientCreatedResponse | null>(null);
  hasAcknowledged = false;

  onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.serverError.set(null);
    this.submitting.set(true);

    const req: CreateApiClientRequest = {
      name: this.form.value.name?.trim(),
    };

    this.apiClientsApi.createClient(req).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.createdClient.set(res);
        this.notify.success(`API Client "${res.name ?? ''}" created successfully`);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        applyServerErrors(this.form, err);
        this.serverError.set(
          err instanceof ApiError ? err.detail : 'Failed to create API client credential',
        );
      },
    });
  }

  onDone(): void {
    if (!this.hasAcknowledged) {
      return;
    }
    this.dialogRef.close(this.createdClient());
  }
}
