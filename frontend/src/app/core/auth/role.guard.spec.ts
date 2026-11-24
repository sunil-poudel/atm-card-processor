import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { AuthStore } from './auth.store';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  let store: AuthStore;
  let router: Router;
  const dummyState = {} as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), AuthStore],
    });

    store = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
  });

  it('should allow navigation when no roles required on route', () => {
    const route = { data: {} } as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => roleGuard(route, dummyState));
    expect(result).toBeTrue();
  });

  it('should allow navigation when user has one of allowed roles', () => {
    store.setSession('token', {
      id: '1',
      email: 'analyst@minicard.local',
      displayName: 'Analyst',
      role: 'ANALYST',
    });

    const route = {
      data: { roles: ['ADMIN', 'ANALYST'] },
    } as unknown as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() => roleGuard(route, dummyState));
    expect(result).toBeTrue();
  });

  it('should redirect to /403 when user does not have allowed role', () => {
    store.setSession('token', {
      id: '1',
      email: 'officer@minicard.local',
      displayName: 'Officer',
      role: 'DISPUTE_OFFICER',
    });

    const route = {
      data: { roles: ['ADMIN'] },
    } as unknown as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() => roleGuard(route, dummyState)) as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result)).toBe('/403');
  });
});
