import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';

@Component({
  selector: 'mc-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="login-container">
      <mat-card class="login-card">
        <mat-card-header class="login-header">
          <div class="brand-title">
            <div class="brand-logo-icon">
              <mat-icon>credit_card</mat-icon>
            </div>
            <span>ATM Processor</span>
          </div>
          <mat-card-subtitle class="login-subtitle">
            Nepal Card Network &bull; Issuer Back Office
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content class="login-content">
          @if (sessionExpiredNotice()) {
            <div class="alert alert-warning" role="alert">
              <mat-icon>info</mat-icon>
              <span>Your session has expired. Please log in again.</span>
            </div>
          }

          @if (errorMessage()) {
            <div class="alert alert-danger" role="alert">
              <mat-icon>error_outline</mat-icon>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input
                matInput
                type="email"
                formControlName="email"
                placeholder="name@company.com"
                autocomplete="email"
              />
              <mat-icon matPrefix>email</mat-icon>
              @if (form.controls.email.hasError('required') && form.controls.email.touched) {
                <mat-error>Email is required</mat-error>
              }
              @if (form.controls.email.hasError('email') && form.controls.email.touched) {
                <mat-error>Enter a valid email address</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input
                matInput
                [type]="hidePassword() ? 'password' : 'text'"
                formControlName="password"
                autocomplete="current-password"
              />
              <mat-icon matPrefix>lock</mat-icon>
              <button
                mat-icon-button
                matSuffix
                type="button"
                (click)="hidePassword.set(!hidePassword())"
                [attr.aria-label]="hidePassword() ? 'Show password' : 'Hide password'"
              >
                <mat-icon>{{ hidePassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              @if (form.controls.password.hasError('required') && form.controls.password.touched) {
                <mat-error>Password is required</mat-error>
              }
            </mat-form-field>

            <button
              mat-flat-button
              color="primary"
              type="submit"
              class="submit-button full-width"
              [disabled]="form.invalid || loading()"
            >
              @if (loading()) {
                <mat-spinner diameter="20" class="btn-spinner"></mat-spinner>
                <span>Signing in...</span>
              } @else {
                <span>Sign in</span>
              }
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .login-container {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 100vh;
        padding: 16px;
      }
      .login-card {
        width: 100%;
        max-width: 440px;
        border-radius: 18px !important;
        padding: 36px 32px;
      }
      .login-header {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-bottom: 28px;
      }
      .brand-title {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 1.5rem;
        font-weight: 800;
        letter-spacing: -0.02em;
        color: var(--mc-text-primary);
      }
      .brand-logo-icon {
        width: 44px;
        height: 44px;
        border-radius: 12px;
        background: linear-gradient(135deg, #00d8f6 0%, #2563eb 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #ffffff;
        box-shadow: 0 4px 16px rgba(0, 216, 246, 0.4);

        mat-icon {
          font-size: 24px;
          width: 24px;
          height: 24px;
        }
      }
      .login-subtitle {
        color: var(--mc-text-secondary);
        font-size: 0.875rem;
        font-weight: 500;
        margin-top: 6px;
      }
      .login-content {
        padding: 0;
      }
      .full-width {
        width: 100%;
        margin-bottom: 12px;
      }
      .submit-button {
        height: 50px;
        font-size: 1rem;
        font-weight: 700;
        margin-top: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        border-radius: 10px;
      }
      .btn-spinner {
        display: inline-block;
      }
      .alert {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 12px 14px;
        border-radius: 10px;
        font-size: 0.875rem;
        font-weight: 500;
        margin-bottom: 18px;
      }
      .alert-warning {
        background-color: var(--mc-warning-bg);
        color: var(--mc-warning);
        border: 1px solid var(--mc-warning-border);
      }
      .alert-danger {
        background-color: var(--mc-danger-bg);
        color: var(--mc-danger);
        border: 1px solid var(--mc-danger-border);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly hidePassword = signal(true);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly sessionExpiredNotice = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('reason') === 'expired') {
      this.sessionExpiredNotice.set(true);
    }
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }

    this.errorMessage.set(null);
    this.sessionExpiredNotice.set(false);
    this.loading.set(true);

    const { email, password } = this.form.getRawValue();

    this.authService.login({ email, password }).subscribe({
      next: (res) => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        if (returnUrl && returnUrl.startsWith('/')) {
          this.router.navigateByUrl(returnUrl);
        } else {
          // Default landing per role
          if (res.user.role === 'DISPUTE_OFFICER') {
            this.router.navigate(['/disputes']);
          } else {
            this.router.navigate(['/dashboard']);
          }
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err instanceof ApiError) {
          if (err.code === 'INVALID_CREDENTIALS' || err.status === 401) {
            this.errorMessage.set('Email or password is incorrect');
            return;
          }
          this.errorMessage.set(err.detail || 'Authentication failed. Please try again.');
        } else {
          this.errorMessage.set('An unexpected error occurred. Please try again.');
        }
      },
    });
  }
}
