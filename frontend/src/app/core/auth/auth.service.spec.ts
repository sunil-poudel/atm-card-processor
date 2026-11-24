import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../config/app-config';
import { AuthService, UserSummaryDto } from './auth.service';
import { AuthStore } from './auth.store';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let store: AuthStore;

  const mockUser: UserSummaryDto = {
    id: 'user-1',
    email: 'admin@minicard.local',
    displayName: 'Admin User',
    role: 'ADMIN',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AuthStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should login, set session in store and return login response', () => {
    service.login({ email: 'admin@minicard.local', password: 'password' }).subscribe((res) => {
      expect(res.user).toEqual(mockUser);
      expect(store.accessToken()).toBe('access-token-xyz');
      expect(store.user()).toEqual({
        id: mockUser.id,
        email: mockUser.email,
        displayName: mockUser.displayName,
        role: mockUser.role,
      });
    });

    const req = httpTesting.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'admin@minicard.local',
      password: 'password',
    });

    req.flush({
      accessToken: 'access-token-xyz',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });
  });

  it('should refresh token and update store', () => {
    service.refresh().subscribe((res) => {
      expect(res.accessToken).toBe('new-token-123');
      expect(store.accessToken()).toBe('new-token-123');
    });

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');

    req.flush({
      accessToken: 'new-token-123',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });
  });

  it('should logout and clear store session', () => {
    store.setSession('token', {
      id: mockUser.id,
      email: mockUser.email,
      displayName: mockUser.displayName,
      role: mockUser.role,
    });
    expect(store.isAuthenticated()).toBeTrue();

    service.logout().subscribe(() => {
      expect(store.isAuthenticated()).toBeFalse();
      expect(store.accessToken()).toBeNull();
    });

    const req = httpTesting.expectOne('/api/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
