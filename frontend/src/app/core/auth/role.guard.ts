import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore, UserRole } from './auth.store';

export const roleGuard: CanActivateFn = (route) => {
  const store = inject(AuthStore);
  const router = inject(Router);

  const allowedRoles = (route.data?.['roles'] as UserRole[]) || [];
  if (allowedRoles.length === 0 || store.hasRole(allowedRoles)) {
    return true;
  }

  return router.createUrlTree(['/403']);
};
