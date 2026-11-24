import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthStore } from './auth.store';

describe('authGuard', () => {
  let store: AuthStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), AuthStore],
    });

    store = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
  });

  it('should allow navigation if user is authenticated', () => {
    store.setSession('token', {
      id: '1',
      email: 'admin@minicard.local',
      displayName: 'Admin',
      role: 'ADMIN',
    });

    const route = {} as ActivatedRouteSnapshot;
    const state = { url: '/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => authGuard(route, state));
    expect(result).toBeTrue();
  });

  it('should redirect to login with returnUrl if unauthenticated', () => {
    const route = {} as ActivatedRouteSnapshot;
    const state = { url: '/cards/123' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => authGuard(route, state)) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fcards%2F123');
  });
});
