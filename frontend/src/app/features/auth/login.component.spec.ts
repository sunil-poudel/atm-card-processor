import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApiError } from '../../core/api/api-error';
import { AuthService, LoginResponseDto } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockAdminResponse: LoginResponseDto = {
    accessToken: 'admin-token',
    user: {
      id: 'user-1',
      email: 'admin@minicard.local',
      displayName: 'Admin',
      role: 'ADMIN',
    },
  };

  const mockOfficerResponse: LoginResponseDto = {
    accessToken: 'officer-token',
    user: {
      id: 'user-2',
      email: 'officer@minicard.local',
      displayName: 'Officer',
      role: 'DISPUTE_OFFICER',
    },
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => (key === 'reason' ? null : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    spyOn(router, 'navigateByUrl');

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create login component with invalid form initially', () => {
    expect(component).toBeTruthy();
    expect(component.form.valid).toBeFalse();
  });

  it('should validate email format and password required', () => {
    component.form.controls.email.setValue('invalid-email');
    component.form.controls.password.setValue('');
    expect(component.form.valid).toBeFalse();

    component.form.controls.email.setValue('valid@minicard.local');
    component.form.controls.password.setValue('secret123');
    expect(component.form.valid).toBeTrue();
  });

  it('should call authService.login and navigate to dashboard for ADMIN', () => {
    authServiceSpy.login.and.returnValue(of(mockAdminResponse));

    component.form.setValue({
      email: 'admin@minicard.local',
      password: 'password',
    });

    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: 'admin@minicard.local',
      password: 'password',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should navigate to /disputes for DISPUTE_OFFICER', () => {
    authServiceSpy.login.and.returnValue(of(mockOfficerResponse));

    component.form.setValue({
      email: 'officer@minicard.local',
      password: 'password',
    });

    component.onSubmit();

    expect(router.navigate).toHaveBeenCalledWith(['/disputes']);
  });

  it('should display error message on invalid credentials', () => {
    const error = new ApiError({
      type: 'urn:minicard:problem:invalid-credentials',
      title: 'Unauthorized',
      status: 401,
      detail: 'Invalid credentials',
      code: 'INVALID_CREDENTIALS',
    });
    authServiceSpy.login.and.returnValue(throwError(() => error));

    component.form.setValue({
      email: 'admin@minicard.local',
      password: 'wrongpassword',
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Email or password is incorrect');
  });
});
