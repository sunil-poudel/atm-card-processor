import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../config/app-config';
import { NotificationService } from '../notify/notification.service';
import { authInterceptor, _resetAuthRefreshState } from './auth.interceptor';
import { AuthService } from './auth.service';
import { AuthStore } from './auth.store';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let store: AuthStore;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    _resetAuthRefreshState();
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['refresh']);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        AuthStore,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['info', 'error', 'warning']),
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should attach Bearer token to API requests when authenticated', () => {
    store.setSession('test-token-123', {
      id: '1',
      email: 'user@test.com',
      displayName: 'User',
      role: 'ADMIN',
    });

    http.get('/api/v1/cards').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token-123');
    req.flush([]);
  });

  it('should NOT attach Bearer token to auth endpoints', () => {
    store.setSession('test-token-123', {
      id: '1',
      email: 'user@test.com',
      displayName: 'User',
      role: 'ADMIN',
    });

    http.post('/api/v1/auth/login', {}).subscribe();

    const req = httpTesting.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should refresh and retry once when receiving 401', (done) => {
    store.setSession('initial-token', {
      id: '1',
      email: 'user@test.com',
      displayName: 'User',
      role: 'ADMIN',
    });

    authServiceSpy.refresh.and.returnValue(
      of({
        accessToken: 'refreshed-token',
        user: {
          id: '1',
          email: 'user@test.com',
          displayName: 'User',
          role: 'ADMIN',
        },
      }),
    );

    http.get<{ data: string }>('/api/v1/cards').subscribe({
      next: (res) => {
        expect(res.data).toBe('success');
        done();
      },
      error: done.fail,
    });

    // 1st request fails with 401
    const firstReq = httpTesting.expectOne('/api/v1/cards');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer initial-token');
    firstReq.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    // Retry request with new token
    const retryReq = httpTesting.expectOne('/api/v1/cards');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    retryReq.flush({ data: 'success' });
  });

  it('should navigate to login when refresh fails', (done) => {
    store.setSession('initial-token', {
      id: '1',
      email: 'user@test.com',
      displayName: 'User',
      role: 'ADMIN',
    });

    authServiceSpy.refresh.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );

    http.get('/api/v1/cards').subscribe({
      next: () => done.fail('Should fail'),
      error: () => {
        expect(store.isAuthenticated()).toBeFalse();
        expect(router.navigate).toHaveBeenCalledWith(['/login'], {
          queryParams: { reason: 'expired', returnUrl: jasmine.any(String) },
        });
        done();
      },
    });

    const firstReq = httpTesting.expectOne('/api/v1/cards');
    firstReq.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
  });
});
