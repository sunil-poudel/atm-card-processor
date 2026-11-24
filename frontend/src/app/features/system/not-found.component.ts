import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'mc-not-found',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule],
  template: `
    <div class="not-found-container">
      <div class="not-found-card">
        <mat-icon class="not-found-icon">search_off</mat-icon>
        <h1>404 - Page Not Found</h1>
        <p class="description">
          The page you requested does not exist or has been moved. Check the URL or return to the
          dashboard.
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
      .not-found-container {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: calc(100vh - 120px);
        padding: 24px;
      }
      .not-found-card {
        text-align: center;
        max-width: 480px;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 48px 32px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      }
      .not-found-icon {
        font-size: 64px;
        height: 64px;
        width: 64px;
        color: #94a3b8;
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
export class NotFoundComponent {}
