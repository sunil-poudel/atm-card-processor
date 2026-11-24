import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AuthStore, UserRole } from '../core/auth/auth.store';
import { ThemeService } from '../core/theme/theme.service';
import { StatusChipComponent } from '../shared/ui/status-chip.component';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  roles: UserRole[];
  section?: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', route: '/dashboard', icon: 'dashboard', roles: ['ADMIN', 'ANALYST'] },
  {
    label: 'Cards',
    route: '/cards',
    icon: 'credit_card',
    roles: ['ADMIN', 'ANALYST', 'DISPUTE_OFFICER'],
  },
  {
    label: 'Transactions',
    route: '/transactions',
    icon: 'receipt_long',
    roles: ['ADMIN', 'ANALYST', 'DISPUTE_OFFICER'],
  },
  { label: 'Payment Simulator', route: '/simulator', icon: 'point_of_sale', roles: ['ADMIN'] },
  { label: 'Fraud Rules', route: '/fraud-rules', icon: 'security', roles: ['ADMIN', 'ANALYST'] },
  {
    label: 'Disputes',
    route: '/disputes',
    icon: 'gavel',
    roles: ['ADMIN', 'ANALYST', 'DISPUTE_OFFICER'],
  },
  { label: 'Reports', route: '/reports', icon: 'bar_chart', roles: ['ADMIN', 'ANALYST'] },
  {
    label: 'Users',
    route: '/admin/users',
    icon: 'people',
    roles: ['ADMIN'],
    section: 'Administration',
  },
  {
    label: 'API Clients',
    route: '/admin/api-clients',
    icon: 'key',
    roles: ['ADMIN'],
    section: 'Administration',
  },
  {
    label: 'Audit Log',
    route: '/admin/audit',
    icon: 'history',
    roles: ['ADMIN'],
    section: 'Administration',
  },
];

@Component({
  selector: 'mc-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatMenuModule,
    MatDividerModule,
    MatTooltipModule,
    StatusChipComponent,
  ],
  template: `
    <div class="shell-container">
      <!-- Futuristic Top Header / Toolbar -->
      <header class="shell-header card-glass">
        <div class="header-left">
          <button
            mat-icon-button
            class="nav-toggle-btn"
            (click)="toggleSidenav()"
            aria-label="Toggle navigation drawer"
          >
            <mat-icon>menu</mat-icon>
          </button>

          <!-- Brand Logo & Identity -->
          <div class="brand-container">
            <div class="brand-logo-icon">
              <mat-icon>credit_card</mat-icon>
            </div>
            <span class="brand-name">ATM Processor</span>
          </div>
        </div>

        <!-- Right Side: Theme Toggle & User Menu -->
        <div class="header-right">
          <!-- Theme Switcher -->
          <button
            type="button"
            class="theme-toggle-btn"
            (click)="themeService.toggleTheme()"
            [attr.aria-label]="
              themeService.currentTheme() === 'dark'
                ? 'Switch to Light Mode'
                : 'Switch to Dark Mode'
            "
            [matTooltip]="
              themeService.currentTheme() === 'dark'
                ? 'Switch to Light Mode'
                : 'Switch to Dark Mode'
            "
          >
            @if (themeService.currentTheme() === 'dark') {
              <mat-icon class="theme-icon sun-icon">wb_sunny</mat-icon>
              <span class="theme-text hide-on-phone">Light</span>
            } @else {
              <mat-icon class="theme-icon moon-icon">nightlight_round</mat-icon>
              <span class="theme-text hide-on-phone">Dark</span>
            }
          </button>

          <!-- User Menu -->
          @if (user(); as u) {
            <div class="user-block">
              <button
                [matMenuTriggerFor]="userMenu"
                class="user-profile-btn"
                aria-label="User account menu"
              >
                <div class="user-avatar-circle">
                  {{ u.displayName ? u.displayName.charAt(0).toUpperCase() : 'U' }}
                </div>
                <span class="user-role-inline hide-on-phone">{{ u.role }}</span>
                <mat-icon class="chevron-icon hide-on-phone">expand_more</mat-icon>
              </button>

              <mat-menu #userMenu="matMenu" xPosition="before" class="futuristic-user-menu">
                <div class="user-menu-header">
                  <div class="user-avatar-large">
                    {{ u.displayName ? u.displayName.charAt(0).toUpperCase() : 'U' }}
                  </div>
                  <div>
                    <div class="header-name">{{ u.displayName }}</div>
                    <div class="header-email">{{ u.email }}</div>
                    <div class="header-role">
                      <mc-status-chip [status]="u.role"></mc-status-chip>
                    </div>
                  </div>
                </div>
                <mat-divider></mat-divider>
                <button mat-menu-item (click)="logout()" class="logout-menu-item">
                  <mat-icon>logout</mat-icon>
                  <span>Log out</span>
                </button>
              </mat-menu>
            </div>
          }
        </div>
      </header>

      <!-- Side Navigation & Main Content Container -->
      <mat-sidenav-container class="shell-sidenav-container">
        <mat-sidenav
          [opened]="sidenavOpened()"
          [mode]="isMobile() ? 'over' : 'side'"
          (openedChange)="sidenavOpened.set($event)"
          class="shell-sidenav"
        >
          <div class="sidenav-inner">
            <div class="nav-brand-mobile show-on-mobile">
              <span class="brand-name">ATM Processor</span>
              <span class="status-online-pill">
                <span class="pulsing-dot"></span> ONLINE
              </span>
            </div>

            <mat-nav-list class="nav-list">
              <div class="nav-section-label">MAIN NAVIGATION</div>
              @for (item of mainNavItems(); track item.route) {
                <a
                  mat-list-item
                  [routerLink]="item.route"
                  routerLinkActive="active-nav-link"
                  (click)="onNavClick()"
                  class="futuristic-nav-item"
                >
                  <mat-icon matListItemIcon class="nav-icon">{{ item.icon }}</mat-icon>
                  <span matListItemTitle class="nav-label">{{ item.label }}</span>
                </a>
              }

              @if (adminNavItems().length > 0) {
                <div class="nav-section-label admin-section-label">ADMINISTRATION</div>
                @for (item of adminNavItems(); track item.route) {
                  <a
                    mat-list-item
                    [routerLink]="item.route"
                    routerLinkActive="active-nav-link"
                    (click)="onNavClick()"
                    class="futuristic-nav-item"
                  >
                    <mat-icon matListItemIcon class="nav-icon">{{ item.icon }}</mat-icon>
                    <span matListItemTitle class="nav-label">{{ item.label }}</span>
                  </a>
                }
              }
            </mat-nav-list>
          </div>
        </mat-sidenav>

        <mat-sidenav-content class="shell-content">
          <div class="content-viewport">
            <main class="content-wrapper">
              <router-outlet></router-outlet>
            </main>
          </div>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [
    `
      .shell-container {
        display: flex;
        flex-direction: column;
        height: 100vh;
        overflow: hidden;
      }

      /* Top Header */
      .shell-header {
        position: sticky;
        top: 0;
        z-index: 1000;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 10px 20px;
        min-height: 64px;
        border-bottom: 1px solid var(--mc-border);
      }

      .header-left {
        display: flex;
        align-items: center;
        gap: 14px;
      }

      .nav-toggle-btn {
        color: var(--mc-text-primary);
      }

      /* Brand Identity */
      .brand-container {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .brand-logo-icon {
        width: 40px;
        height: 40px;
        border-radius: 12px;
        background: linear-gradient(135deg, #00d8f6 0%, #2563eb 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #ffffff;
        box-shadow: 0 4px 14px rgba(0, 216, 246, 0.35);

        mat-icon {
          font-size: 22px;
          width: 22px;
          height: 22px;
        }
      }

      .brand-titles {
        display: flex;
        flex-direction: column;
      }

      .brand-name-row {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .brand-name {
        font-size: 1.15rem;
        font-weight: 800;
        letter-spacing: -0.02em;
        color: var(--mc-text-primary);
      }

      .status-online-pill {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        padding: 2px 8px;
        border-radius: 9999px;
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.05em;
        font-family: 'JetBrains Mono', monospace;
        background: rgba(16, 185, 129, 0.15);
        color: #10b981;
        border: 1px solid rgba(16, 185, 129, 0.35);
      }

      .pulsing-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background-color: #10b981;
        box-shadow: 0 0 8px #10b981;
        animation: pulseDot 2s infinite ease-in-out;
      }

      @keyframes pulseDot {
        0%,
        100% {
          opacity: 1;
          transform: scale(1);
        }
        50% {
          opacity: 0.4;
          transform: scale(0.8);
        }
      }

      .brand-tagline {
        font-size: 0.75rem;
        font-weight: 500;
        color: var(--mc-text-secondary);
        margin-top: 1px;
      }

      /* View Mode Switcher */
      .view-mode-pill {
        display: flex;
        align-items: center;
        padding: 4px;
        border-radius: 12px;
        background: rgba(148, 163, 184, 0.15);
        border: 1px solid var(--mc-border);
      }

      .view-pill-btn {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 14px;
        border-radius: 8px;
        border: none;
        background: transparent;
        color: var(--mc-text-secondary);
        font-size: 0.8125rem;
        font-weight: 700;
        cursor: pointer;
        transition: all 0.2s ease;

        .view-icon {
          font-size: 16px;
          width: 16px;
          height: 16px;
        }

        &.active {
          background: var(--mc-surface-solid);
          color: var(--mc-primary);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
        }
      }

      html.dark .view-pill-btn.active {
        background: rgba(0, 216, 246, 0.15);
        color: #00d8f6;
        border: 1px solid rgba(0, 216, 246, 0.3);
      }

      /* Right Controls */
      .header-right {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .theme-toggle-btn {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px 14px;
        border-radius: 10px;
        border: 1px solid var(--mc-border);
        background: var(--mc-surface);
        color: var(--mc-text-primary);
        font-size: 0.8125rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
          border-color: var(--mc-primary);
          box-shadow: 0 0 10px var(--mc-primary-glow);
        }

        .theme-icon {
          font-size: 18px;
          width: 18px;
          height: 18px;
        }

        .sun-icon {
          color: #f59e0b;
        }

        .moon-icon {
          color: #00d8f6;
        }
      }

      /* User Block */
      .user-profile-btn {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        padding: 6px 12px;
        border-radius: 12px;
        border: 1px solid var(--mc-border);
        background: var(--mc-surface);
        color: var(--mc-text-primary);
        cursor: pointer;
        font-family: inherit;
        line-height: 1;

        /* Kill any Material button internal wrapping */
        .mdc-button__label {
          display: flex;
          flex-direction: row;
          align-items: center;
          gap: 8px;
        }
      }

      .user-avatar-circle {
        width: 32px;
        height: 32px;
        border-radius: 8px;
        background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
        color: white;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.875rem;
      }

      .user-role-inline {
        font-size: 0.75rem;
        font-weight: 700;
        letter-spacing: 0.06em;
        color: var(--mc-text-secondary);
        text-transform: uppercase;
      }

      .user-meta {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        text-align: left;
      }

      .user-display-name {
        font-size: 0.8125rem;
        font-weight: 700;
        color: var(--mc-text-primary);
        line-height: 1.2;
      }

      .user-role-badge {
        transform: scale(0.85);
        transform-origin: left center;
        margin-top: -2px;
      }

      .chevron-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: var(--mc-text-secondary);
      }

      /* User Menu Header */
      .user-menu-header {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px;
      }

      .user-avatar-large {
        width: 44px;
        height: 44px;
        border-radius: 12px;
        background: linear-gradient(135deg, #00d8f6 0%, #2563eb 100%);
        color: white;
        font-size: 1.25rem;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .header-name {
        font-weight: 700;
        font-size: 0.9375rem;
        color: var(--mc-text-primary);
      }

      .header-email {
        font-size: 0.8125rem;
        color: var(--mc-text-secondary);
        margin-top: 2px;
      }

      .header-role {
        margin-top: 6px;
      }

      .logout-menu-item {
        color: #ef4444 !important;

        mat-icon {
          color: #ef4444 !important;
        }
      }

      /* Sidenav */
      .shell-sidenav-container {
        flex: 1;
        background: transparent;
      }

      .shell-sidenav {
        width: 256px;
        background: var(--mc-surface);
        border-right: 1px solid var(--mc-border);
        backdrop-filter: blur(16px);
      }

      .sidenav-inner {
        display: flex;
        flex-direction: column;
        height: 100%;
        padding: 12px 10px;
      }

      .nav-brand-mobile {
        display: none;
        padding: 12px 14px 16px 14px;
        border-bottom: 1px solid var(--mc-border);
        margin-bottom: 8px;
      }

      .nav-list {
        padding: 0;
        flex: 1;
      }

      .nav-section-label {
        font-size: 0.6875rem;
        font-weight: 800;
        letter-spacing: 0.08em;
        color: var(--mc-text-muted);
        padding: 12px 14px 6px 14px;
      }

      .admin-section-label {
        margin-top: 14px;
      }

      .futuristic-nav-item {
        border-radius: 10px !important;
        margin-bottom: 3px !important;
        transition: all 0.2s ease !important;
        color: var(--mc-text-secondary) !important;

        .nav-icon {
          color: var(--mc-text-secondary);
          transition: color 0.2s ease;
        }

        .nav-label {
          font-size: 0.875rem;
          font-weight: 600;
        }

        &:hover {
          background-color: var(--mc-surface-hover) !important;
          color: var(--mc-text-primary) !important;

          .nav-icon {
            color: var(--mc-primary);
          }
        }
      }

      .active-nav-link {
        background: rgba(0, 216, 246, 0.12) !important;
        color: var(--mc-primary) !important;
        font-weight: 700 !important;
        border-left: 3px solid #00d8f6 !important;

        .nav-icon {
          color: var(--mc-primary) !important;
        }
      }

      html.dark .active-nav-link {
        background: rgba(0, 216, 246, 0.15) !important;
        color: #00d8f6 !important;

        .nav-icon {
          color: #00d8f6 !important;
        }
      }

      .sidenav-footer {
        padding: 12px 8px;
        border-top: 1px solid var(--mc-border);
      }

      .network-badge {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 12px;
        border-radius: 10px;
        background: rgba(148, 163, 184, 0.08);
        border: 1px solid var(--mc-border);
      }

      .net-indicator {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background-color: #10b981;
        box-shadow: 0 0 8px #10b981;
      }

      .net-title {
        font-size: 0.75rem;
        font-weight: 700;
        color: var(--mc-text-primary);
      }

      .net-sub {
        font-size: 0.6875rem;
        font-family: 'JetBrains Mono', monospace;
        color: var(--mc-text-muted);
      }

      /* Content Area */
      .shell-content {
        background: transparent;
        overflow-y: auto;
      }

      .content-viewport {
        min-height: calc(100vh - 64px);
        display: flex;
        flex-direction: column;
      }

      .content-wrapper {
        padding: 24px 28px;
        max-width: 1440px;
        margin: 0 auto;
        width: 100%;
        box-sizing: border-box;
      }

      /* Android / Mobile Simulated View Frame */
      .mobile-chassis-container {
        display: flex;
        justify-content: center;
        padding: 24px 16px;
      }

      .mobile-chassis-frame {
        max-width: 420px;
        width: 100%;
        background: var(--mc-surface);
        border: 10px solid #1e293b;
        border-radius: 36px;
        box-shadow:
          0 25px 60px -15px rgba(0, 0, 0, 0.5),
          0 0 45px rgba(0, 216, 246, 0.15);
        overflow: hidden;
        min-height: 800px;

        .content-wrapper {
          padding: 16px;
        }
      }

      /* Mobile Breakdown */
      @media (max-width: 768px) {
        .hide-on-phone {
          display: none !important;
        }

        .show-on-mobile {
          display: flex !important;
          align-items: center;
          justify-content: space-between;
        }

        .shell-header {
          padding: 8px 12px;
          min-height: 56px;
        }

        .content-wrapper {
          padding: 14px 12px;
        }

        .brand-logo-icon {
          width: 34px;
          height: 34px;
        }

        .brand-name {
          font-size: 1rem;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly authStore = inject(AuthStore);
  private readonly authService = inject(AuthService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly router = inject(Router);
  readonly themeService = inject(ThemeService);

  readonly user = this.authStore.user;
  readonly isMobile = signal(false);
  readonly sidenavOpened = signal(true);

  readonly visibleItems = computed(() => {
    const currentRole = this.user()?.role;
    if (!currentRole) return [];
    return NAV_ITEMS.filter((item) => item.roles.includes(currentRole));
  });

  readonly mainNavItems = computed(() => {
    return this.visibleItems().filter((item) => !item.section);
  });

  readonly adminNavItems = computed(() => {
    return this.visibleItems().filter((item) => item.section === 'Administration');
  });

  constructor() {
    this.breakpointObserver
      .observe([Breakpoints.Handset, Breakpoints.TabletPortrait])
      .subscribe((result) => {
        this.isMobile.set(result.matches);
        if (result.matches) {
          this.sidenavOpened.set(false);
        } else {
          this.sidenavOpened.set(true);
        }
      });
  }

  toggleSidenav(): void {
    this.sidenavOpened.set(!this.sidenavOpened());
  }

  setMobileViewActive(_active: boolean): void {}

  onNavClick(): void {
    if (this.isMobile()) {
      this.sidenavOpened.set(false);
    }
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login']);
      },
      error: () => {
        this.router.navigate(['/login']);
      },
    });
  }
}
