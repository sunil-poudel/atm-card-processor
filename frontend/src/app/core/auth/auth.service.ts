import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthStore, AuthUser, UserRole } from './auth.store';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserSummaryDto {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  enabled?: boolean;
  locked?: boolean;
}

export interface LoginResponseDto {
  accessToken: string;
  user: UserSummaryDto;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(APP_CONFIG);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  login(credentials: LoginRequest): Observable<LoginResponseDto> {
    return this.http
      .post<LoginResponseDto>(`${this.config.apiBase}/auth/login`, credentials, {
        withCredentials: true,
      })
      .pipe(
        tap((res) => {
          this.store.setSession(res.accessToken, this.mapUser(res.user));
        }),
      );
  }

  refresh(): Observable<LoginResponseDto> {
    return this.http
      .post<LoginResponseDto>(
        `${this.config.apiBase}/auth/refresh`,
        {},
        {
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
          withCredentials: true,
        },
      )
      .pipe(
        tap((res) => {
          this.store.setSession(res.accessToken, this.mapUser(res.user));
        }),
      );
  }

  logout(returnUrl?: string): Observable<void> {
    return this.http
      .post<void>(
        `${this.config.apiBase}/auth/logout`,
        {},
        {
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
          withCredentials: true,
        },
      )
      .pipe(
        catchError(() => of(undefined as void)),
        tap(() => {
          this.store.clearSession();
          const queryParams = returnUrl ? { returnUrl } : undefined;
          this.router.navigate(['/login'], { queryParams });
        }),
        map(() => undefined as void),
      );
  }

  getCurrentUser(): Observable<AuthUser> {
    return this.http.get<UserSummaryDto>(`${this.config.apiBase}/auth/me`).pipe(
      map((dto) => this.mapUser(dto)),
      tap((user) => {
        const token = this.store.accessToken();
        if (token) {
          this.store.setSession(token, user);
        }
      }),
    );
  }

  private mapUser(dto: UserSummaryDto): AuthUser {
    return {
      id: dto.id,
      email: dto.email,
      displayName: dto.displayName,
      role: dto.role,
    };
  }
}
