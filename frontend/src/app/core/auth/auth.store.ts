import { computed, Injectable, signal } from '@angular/core';

export type UserRole = 'ADMIN' | 'ANALYST' | 'DISPUTE_OFFICER';

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
}

@Injectable({
  providedIn: 'root',
})
export class AuthStore {
  readonly accessToken = signal<string | null>(null);
  readonly user = signal<AuthUser | null>(null);

  readonly isAuthenticated = computed(() => !!this.accessToken());
  readonly role = computed(() => this.user()?.role ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isAnalyst = computed(() => this.role() === 'ANALYST');
  readonly isDisputeOfficer = computed(() => this.role() === 'DISPUTE_OFFICER');
  readonly displayName = computed(() => this.user()?.displayName ?? '');
  readonly email = computed(() => this.user()?.email ?? '');

  setSession(accessToken: string, user: AuthUser): void {
    this.accessToken.set(accessToken);
    this.user.set(user);
  }

  clearSession(): void {
    this.accessToken.set(null);
    this.user.set(null);
  }

  hasRole(allowedRoles: UserRole[]): boolean {
    const currentRole = this.role();
    if (!currentRole) {
      return false;
    }
    return allowedRoles.includes(currentRole);
  }
}
