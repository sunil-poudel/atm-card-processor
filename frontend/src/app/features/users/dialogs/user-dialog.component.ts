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
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import {
  CreateUserRequest,
  UpdateUserRequest,
  UserResponse,
  UsersApiService,
} from '../users-api.service';

export interface UserDialogData {
  mode: 'create' | 'edit';
  user?: UserResponse;
}

@Component({
  selector: 'mc-user-dialog',
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
      {{ isEdit ? 'Edit User' : 'Create Staff User' }}
    </h2>

    <mat-dialog-content class="dialog-content">
      @if (serverError()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ serverError() }}</span>
        </div>
      }

      <form [formGroup]="form" class="user-form">
        @if (!isEdit) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Email Address</mat-label>
            <input
              matInput
              type="email"
              formControlName="email"
              placeholder="e.g. analyst@minicard.local"
              autocomplete="off"
            />
            <mat-icon matPrefix>email</mat-icon>
            @if (form.get('email')?.hasError('required')) {
              <mat-error>Email is required</mat-error>
            } @else if (form.get('email')?.hasError('email')) {
              <mat-error>Must be a valid email address</mat-error>
            } @else if (form.get('email')?.hasError('serverError')) {
              <mat-error>{{ form.get('email')?.getError('serverError') }}</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Display Name</mat-label>
          <input
            matInput
            formControlName="displayName"
            placeholder="e.g. Jane Doe"
            autocomplete="off"
          />
          <mat-icon matPrefix>person</mat-icon>
          @if (form.get('displayName')?.hasError('required')) {
            <mat-error>Display name is required</mat-error>
          } @else if (form.get('displayName')?.hasError('minlength')) {
            <mat-error>Name must be at least 2 characters</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Role</mat-label>
          <mat-select formControlName="role">
            <mat-option value="ADMIN">ADMIN (Full management and config access)</mat-option>
            <mat-option value="ANALYST">ANALYST (Reports, view cards and transactions)</mat-option>
            <mat-option value="DISPUTE_OFFICER"
              >DISPUTE_OFFICER (Dispute management and resolution)</mat-option
            >
          </mat-select>
          <mat-icon matPrefix>shield</mat-icon>
          @if (form.get('role')?.hasError('required')) {
            <mat-error>Role is required</mat-error>
          }
        </mat-form-field>

        @if (!isEdit) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Initial Password</mat-label>
            <input
              matInput
              [type]="hidePassword() ? 'password' : 'text'"
              formControlName="password"
              placeholder="Minimum 12 characters"
              autocomplete="new-password"
            />
            <mat-icon matPrefix>lock</mat-icon>
            <button
              mat-icon-button
              matSuffix
              type="button"
              (click)="hidePassword.set(!hidePassword())"
              [attr.aria-label]="hidePassword() ? 'Show password' : 'Hide password'"
            >
              <mat-icon>{{ hidePassword() ? 'visibility' : 'visibility_off' }}</mat-icon>
            </button>
            <mat-hint>At least 12 characters: uppercase, lowercase, number, symbol</mat-hint>
            @if (form.get('password')?.hasError('required')) {
              <mat-error>Password is required</mat-error>
            } @else if (form.get('password')?.hasError('minlength')) {
              <mat-error>Password must be at least 12 characters</mat-error>
            }
          </mat-form-field>
        } @else {
          <div class="checkbox-field">
            <mat-checkbox formControlName="enabled"> Account Enabled </mat-checkbox>
            <span class="hint-text">Disabled accounts cannot log in or perform actions</span>
          </div>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Reset Password (Optional)</mat-label>
            <input
              matInput
              [type]="hidePassword() ? 'password' : 'text'"
              formControlName="newPassword"
              placeholder="Leave blank to keep existing password"
              autocomplete="new-password"
            />
            <mat-icon matPrefix>lock_reset</mat-icon>
            <button
              mat-icon-button
              matSuffix
              type="button"
              (click)="hidePassword.set(!hidePassword())"
              [attr.aria-label]="hidePassword() ? 'Show password' : 'Hide password'"
            >
              <mat-icon>{{ hidePassword() ? 'visibility' : 'visibility_off' }}</mat-icon>
            </button>
            <mat-hint>At least 12 characters if updating password</mat-hint>
            @if (form.get('newPassword')?.hasError('minlength')) {
              <mat-error>New password must be at least 12 characters</mat-error>
            }
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end" class="dialog-actions">
      <button mat-button mat-dialog-close [disabled]="submitting()">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        (click)="onSubmit()"
        [disabled]="form.invalid || submitting()"
      >
        @if (submitting()) {
          <mat-spinner diameter="18" class="inline-spinner"></mat-spinner>
          <span>Saving...</span>
        } @else {
          <span>{{ isEdit ? 'Update User' : 'Create User' }}</span>
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-content {
        min-width: 380px;
        max-width: 500px;
        padding-top: 12px;
      }
      .user-form {
        display: flex;
        flex-direction: column;
        gap: 16px;
        margin-top: 8px;
      }
      .full-width {
        width: 100%;
      }
      .checkbox-field {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-bottom: 8px;
      }
      .hint-text {
        font-size: 0.75rem;
        color: var(--mc-neutral, #64748b);
        margin-left: 28px;
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
export class UserDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<UserDialogComponent>);
  readonly data: UserDialogData = inject(MAT_DIALOG_DATA);
  private readonly usersApi = inject(UsersApiService);
  private readonly notify = inject(NotificationService);

  readonly isEdit = this.data.mode === 'edit';
  readonly submitting = signal(false);
  readonly hidePassword = signal(true);
  readonly serverError = signal<string | null>(null);

  lateInitForm!: FormGroup;
  get form(): FormGroup {
    return this.lateInitForm;
  }

  ngOnInit(): void {
    if (this.isEdit) {
      const user = this.data.user;
      this.lateInitForm = this.fb.group({
        displayName: [
          user?.displayName ?? '',
          [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
        ],
        role: [user?.role ?? 'ANALYST', [Validators.required]],
        enabled: [user?.enabled ?? true],
        newPassword: ['', [Validators.minLength(12)]],
      });
    } else {
      this.lateInitForm = this.fb.group({
        email: ['', [Validators.required, Validators.email]],
        displayName: [
          '',
          [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
        ],
        role: ['ANALYST', [Validators.required]],
        password: ['', [Validators.required, Validators.minLength(12)]],
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.serverError.set(null);
    this.submitting.set(true);

    if (this.isEdit) {
      const userId = this.data.user?.id;
      if (!userId) {
        this.submitting.set(false);
        return;
      }

      const val = this.form.value;
      const req: UpdateUserRequest = {
        displayName: val.displayName,
        role: val.role,
        enabled: val.enabled,
        version: this.data.user?.version ?? 0,
      };
      if (val.newPassword && val.newPassword.trim()) {
        req.newPassword = val.newPassword.trim();
      }

      this.usersApi.updateUser(userId, req).subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.notify.success(`User ${updated.displayName ?? ''} updated successfully`);
          this.dialogRef.close(updated);
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.handleApiError(err);
        },
      });
    } else {
      const val = this.form.value;
      const req: CreateUserRequest = {
        email: val.email.trim(),
        displayName: val.displayName.trim(),
        role: val.role,
        password: val.password,
      };

      this.usersApi.createUser(req).subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.notify.success(`User ${created.displayName ?? ''} created successfully`);
          this.dialogRef.close(created);
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.handleApiError(err);
        },
      });
    }
  }

  private handleApiError(err: unknown): void {
    if (err instanceof ApiError) {
      if (err.code === 'EMAIL_ALREADY_USED') {
        this.form.get('email')?.setErrors({ serverError: 'This email is already in use' });
        this.serverError.set('This email address is already in use by another user.');
        return;
      }
      if (err.code === 'LAST_ADMIN_PROTECTED') {
        this.serverError.set(
          err.detail ||
            'Cannot disable or demote the last remaining active administrator or your own account.',
        );
        return;
      }
    }
    applyServerErrors(this.form, err);
    if (!this.serverError()) {
      this.serverError.set(err instanceof ApiError ? err.detail : 'An unexpected error occurred');
    }
  }
}
