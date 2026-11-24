import { TestBed } from '@angular/core/testing';
import { AuthStore, AuthUser } from './auth.store';

describe('AuthStore', () => {
  let store: AuthStore;

  const mockUser: AuthUser = {
    id: 'user-1',
    email: 'admin@minicard.local',
    displayName: 'Admin User',
    role: 'ADMIN',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthStore],
    });
    store = TestBed.inject(AuthStore);
  });

  it('should initialize with empty state', () => {
    expect(store.accessToken()).toBeNull();
    expect(store.user()).toBeNull();
    expect(store.role()).toBeNull();
    expect(store.isAuthenticated()).toBeFalse();
  });

  it('should set session and update computed properties', () => {
    store.setSession('token-123', mockUser);

    expect(store.accessToken()).toBe('token-123');
    expect(store.user()).toEqual(mockUser);
    expect(store.role()).toBe('ADMIN');
    expect(store.isAuthenticated()).toBeTrue();
  });

  it('should clear session correctly', () => {
    store.setSession('token-123', mockUser);
    expect(store.isAuthenticated()).toBeTrue();

    store.clearSession();
    expect(store.accessToken()).toBeNull();
    expect(store.user()).toBeNull();
    expect(store.role()).toBeNull();
    expect(store.isAuthenticated()).toBeFalse();
  });

  it('should verify roles with hasRole', () => {
    expect(store.hasRole(['ADMIN'])).toBeFalse();

    store.setSession('token-123', mockUser);
    expect(store.hasRole(['ADMIN'])).toBeTrue();
    expect(store.hasRole(['ADMIN', 'ANALYST'])).toBeTrue();
    expect(store.hasRole(['DISPUTE_OFFICER'])).toBeFalse();
  });
});
