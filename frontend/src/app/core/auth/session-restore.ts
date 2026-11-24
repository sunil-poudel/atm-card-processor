import { inject, provideAppInitializer } from '@angular/core';
import { firstValueFrom, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

export function provideSessionRestore() {
  return provideAppInitializer(() => {
    const authService = inject(AuthService);
    return firstValueFrom(authService.refresh().pipe(catchError(() => of(null))));
  });
}
