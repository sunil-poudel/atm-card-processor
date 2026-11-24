import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { UsersApiService } from '../users-api.service';
import { UserDialogComponent, UserDialogData } from './user-dialog.component';

describe('UserDialogComponent', () => {
  let component: UserDialogComponent;
  let fixture: ComponentFixture<UserDialogComponent>;
  let usersApiSpy: jasmine.SpyObj<UsersApiService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<UserDialogComponent>>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  const createData: UserDialogData = {
    mode: 'create',
  };

  beforeEach(async () => {
    usersApiSpy = jasmine.createSpyObj<UsersApiService>('UsersApiService', [
      'createUser',
      'updateUser',
    ]);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<UserDialogComponent>>('MatDialogRef', [
      'close',
    ]);
    notifySpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'success',
      'error',
    ]);

    await TestBed.configureTestingModule({
      imports: [UserDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: UsersApiService, useValue: usersApiSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: createData },
        { provide: NotificationService, useValue: notifySpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize create form with validators', () => {
    expect(component.isEdit).toBe(false);
    expect(component.form.valid).toBe(false);
    expect(component.form.get('email')).toBeTruthy();
    expect(component.form.get('password')).toBeTruthy();
  });

  it('should submit valid create user form', () => {
    component.form.patchValue({
      email: 'newuser@minicard.local',
      displayName: 'New User',
      role: 'ANALYST',
      password: 'StrongPassword123!',
    });
    expect(component.form.valid).toBe(true);

    const created = { id: 'u3', displayName: 'New User' };
    usersApiSpy.createUser.and.returnValue(of(created));

    component.onSubmit();

    expect(usersApiSpy.createUser).toHaveBeenCalledWith({
      email: 'newuser@minicard.local',
      displayName: 'New User',
      role: 'ANALYST',
      password: 'StrongPassword123!',
    });
    expect(notifySpy.success).toHaveBeenCalled();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(created);
  });

  it('should handle EMAIL_ALREADY_USED error gracefully', () => {
    component.form.patchValue({
      email: 'duplicate@minicard.local',
      displayName: 'New User',
      role: 'ANALYST',
      password: 'StrongPassword123!',
    });

    const apiError = new ApiError({
      code: 'EMAIL_ALREADY_USED',
      status: 409,
      title: 'Conflict',
      type: 'urn:minicard:problem:conflict',
      detail: 'Email is already in use: duplicate@minicard.local',
      requestId: 'req-1',
    });
    usersApiSpy.createUser.and.returnValue(throwError(() => apiError));

    component.onSubmit();

    expect(component.serverError()).toContain('already in use');
    expect(component.form.get('email')?.hasError('serverError')).toBe(true);
  });
});
