import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError, ProblemDetails } from '../api/api-error';
import { NotificationService } from '../notify/notification.service';

function isProblemDetails(body: unknown): body is ProblemDetails {
  return (
    typeof body === 'object' &&
    body !== null &&
    ('detail' in body || 'title' in body || 'type' in body || 'code' in body)
  );
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notify = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        let apiError: ApiError;

        if (isProblemDetails(error.error)) {
          apiError = new ApiError({
            ...error.error,
            status: error.status,
          });
        } else {
          apiError = new ApiError({
            type: 'urn:minicard:problem:network-or-unknown',
            title: error.statusText || 'Error',
            status: error.status,
            detail: error.message || 'An unexpected error occurred',
            code: error.status === 0 ? 'NETWORK_ERROR' : 'HTTP_ERROR',
            requestId: req.headers.get('X-Request-Id') || '',
          });
        }

        // Global notifications per spec (401 is handled by authInterceptor)
        if (error.status === 0) {
          notify.error('Cannot reach the server. Please check your network connection.');
        } else if (error.status === 403) {
          notify.error('Access denied. You do not have permission to perform this action.');
        } else if (error.status >= 500) {
          const ref = apiError.requestId ? ` (Reference: ${apiError.requestId})` : '';
          notify.error(`Server error: ${apiError.detail || 'Internal error'}${ref}`);
        }

        return throwError(() => apiError);
      }

      return throwError(() => error);
    }),
  );
};
