import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { NotificationService } from '../../../core/notify/notification.service';
import { ApiClientsApiService } from '../api-clients-api.service';
import { CreateApiClientDialogComponent } from './create-api-client-dialog.component';

describe('CreateApiClientDialogComponent', () => {
  let component: CreateApiClientDialogComponent;
  let fixture: ComponentFixture<CreateApiClientDialogComponent>;
  let apiClientsApiSpy: jasmine.SpyObj<ApiClientsApiService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<CreateApiClientDialogComponent>>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  beforeEach(async () => {
    apiClientsApiSpy = jasmine.createSpyObj<ApiClientsApiService>('ApiClientsApiService', [
      'createClient',
    ]);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<CreateApiClientDialogComponent>>(
      'MatDialogRef',
      ['close'],
    );
    notifySpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'success',
      'error',
    ]);

    await TestBed.configureTestingModule({
      imports: [CreateApiClientDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ApiClientsApiService, useValue: apiClientsApiSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: NotificationService, useValue: notifySpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateApiClientDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize creation form as invalid initially', () => {
    expect(component.form.valid).toBe(false);
    expect(component.createdClient()).toBeNull();
  });

  it('should create client and switch to one-time reveal state', () => {
    component.form.patchValue({ name: 'Acquirer System' });
    expect(component.form.valid).toBe(true);

    const created = {
      id: 'c1',
      name: 'Acquirer System',
      keyPrefix: 'mck_12345678',
      apiKey: 'mck_12345678_secret123',
      status: 'ACTIVE' as const,
    };
    apiClientsApiSpy.createClient.and.returnValue(of(created));

    component.onSubmit();
    fixture.detectChanges();

    expect(apiClientsApiSpy.createClient).toHaveBeenCalledWith({ name: 'Acquirer System' });
    expect(component.createdClient()).toEqual(created);
    expect(notifySpy.success).toHaveBeenCalled();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Save this API key now');
    expect(el.textContent).toContain('mck_12345678_secret123');
  });

  it('should close dialog when acknowledged and onDone is clicked', () => {
    const created = {
      id: 'c1',
      name: 'Acquirer System',
      keyPrefix: 'mck_12345678',
      apiKey: 'mck_12345678_secret123',
      status: 'ACTIVE' as const,
    };
    component.createdClient.set(created);
    component.hasAcknowledged = true;

    component.onDone();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(created);
  });

  it('should display server error if client creation fails', () => {
    component.form.patchValue({ name: 'Invalid' });
    apiClientsApiSpy.createClient.and.returnValue(
      throwError(() => new Error('Server error occurred')),
    );

    component.onSubmit();
    fixture.detectChanges();

    expect(component.serverError()).toBeTruthy();
    expect(component.createdClient()).toBeNull();
  });
});
