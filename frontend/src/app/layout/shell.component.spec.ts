import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { AuthStore } from '../core/auth/auth.store';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let component: ShellComponent;
  let fixture: ComponentFixture<ShellComponent>;
  let store: AuthStore;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    authServiceSpy.logout.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [ShellComponent, NoopAnimationsModule],
      providers: [provideRouter([]), AuthStore, { provide: AuthService, useValue: authServiceSpy }],
    }).compileComponents();

    store = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    store.setSession('token', {
      id: '1',
      email: 'admin@minicard.local',
      displayName: 'Admin User',
      role: 'ADMIN',
    });

    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render all nav items for ADMIN', () => {
    expect(component.visibleItems().length).toBe(10);
    expect(component.adminNavItems().length).toBe(3);
  });

  it('should filter nav items for DISPUTE_OFFICER', () => {
    store.setSession('token', {
      id: '2',
      email: 'officer@minicard.local',
      displayName: 'Dispute Officer',
      role: 'DISPUTE_OFFICER',
    });
    fixture.detectChanges();

    const labels = component.visibleItems().map((i) => i.label);
    expect(labels).toContain('Cards');
    expect(labels).toContain('Transactions');
    expect(labels).toContain('Disputes');
    expect(labels).not.toContain('Dashboard');
    expect(labels).not.toContain('Payment Simulator');
    expect(component.adminNavItems().length).toBe(0);
  });

  it('should call authService.logout on logout', () => {
    component.logout();
    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
