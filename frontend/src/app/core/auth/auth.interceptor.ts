import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, Observable, shareReplay, switchMap, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { NotificationService } from '../notify/notification.service';
import { AuthService, LoginResponseDto } from './auth.service';
import { AuthStore } from './auth.store';

let refreshInFlight$: Observable<LoginResponseDto> | null = null;

export function _resetAuthRefreshState(): void {
  refreshInFlight$ = null;
}

function isAuthEndpoint(url: string, apiBase: string): boolean {
  return (
    url.includes(`${apiBase}/auth/login`) ||
    url.includes(`${apiBase}/auth/refresh`) ||
    url.includes(`${apiBase}/auth/logout`)
  );
}

function addAuthorizationHeader(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const authService = inject(AuthService);
  const router = inject(Router);
  const notify = inject(NotificationService);
  const config = inject(APP_CONFIG);

  const isAuth = isAuthEndpoint(req.url, config.apiBase);
  const token = store.accessToken();

  let outgoing = req;
  if (token && !isAuth && req.url.includes(config.apiBase)) {
    outgoing = addAuthorizationHeader(req, token);
  }

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      const status =
        error instanceof HttpErrorResponse ? error.status : (error as { status?: number })?.status;

      if (status === 401 && !isAuth) {
        if (!refreshInFlight$) {
          refreshInFlight$ = authService.refresh().pipe(
            shareReplay({ bufferSize: 1, refCount: true }),
            finalize(() => {
              refreshInFlight$ = null;
            }),
          );
        }

        return refreshInFlight$.pipe(
          switchMap((loginRes) => {
            const retried = addAuthorizationHeader(req, loginRes.accessToken);
            return next(retried);
          }),
          catchError((refreshErr) => {
            store.clearSession();
            notify.warning('Session expired. Please log in again.');
            router.navigate(['/login'], {
              queryParams: { reason: 'expired', returnUrl: router.url },
            });
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
