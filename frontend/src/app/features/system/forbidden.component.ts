import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'mc-forbidden',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule],
  template: `
    <div class="forbidden-container">
      <div class="forbidden-card">
        <mat-icon class="forbidden-icon">lock</mat-icon>
        <h1>403 - Access Denied</h1>
        <p class="description">
          You do not have the required permissions to access this page. If you believe this is an
          error, please contact your system administrator.
        </p>
        <div class="actions">
          <a mat-flat-button color="primary" routerLink="/dashboard">
            <mat-icon>home</mat-icon>
            Back to Dashboard
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .forbidden-container {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: calc(100vh - 120px);
        padding: 24px;
      }
      .forbidden-card {
        text-align: center;
        max-width: 480px;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 48px 32px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      }
      .forbidden-icon {
        font-size: 64px;
        height: 64px;
        width: 64px;
        color: #ef4444;
        margin-bottom: 16px;
      }
      h1 {
        font-size: 24px;
        font-weight: 700;
        margin: 0 0 12px;
        color: #0f172a;
      }
      .description {
        color: #64748b;
        font-size: 14px;
        line-height: 1.6;
        margin: 0 0 24px;
      }
      .actions {
        display: flex;
        justify-content: center;
        gap: 12px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenComponent {}
