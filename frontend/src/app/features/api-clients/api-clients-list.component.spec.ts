import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { NotificationService } from '../../core/notify/notification.service';
import { ApiClientsApiService } from './api-clients-api.service';
import { ApiClientsListComponent } from './api-clients-list.component';

describe('ApiClientsListComponent', () => {
  let component: ApiClientsListComponent;
  let fixture: ComponentFixture<ApiClientsListComponent>;
  let apiClientsApiSpy: jasmine.SpyObj<ApiClientsApiService>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  const mockClients = [
    {
      id: 'c1',
      name: 'Simulator Acquirer',
      keyPrefix: 'mck_12345678',
      status: 'ACTIVE' as const,
      createdAt: '2026-01-01T00:00:00Z',
      lastUsedAt: '2026-01-02T10:00:00Z',
    },
    {
      id: 'c2',
      name: 'Legacy Terminal',
      keyPrefix: 'mck_87654321',
      status: 'REVOKED' as const,
      createdAt: '2025-12-01T00:00:00Z',
      revokedAt: '2026-01-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    apiClientsApiSpy = jasmine.createSpyObj<ApiClientsApiService>('ApiClientsApiService', [
      'listClients',
      'revokeClient',
    ]);
    apiClientsApiSpy.listClients.and.returnValue(
      of({
        content: mockClients,
        page: { number: 0, size: 20, totalElements: 2, totalPages: 1 },
      }),
    );

    notifySpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'success',
      'error',
    ]);

    await TestBed.configureTestingModule({
      imports: [ApiClientsListComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ApiClientsApiService, useValue: apiClientsApiSpy },
        { provide: NotificationService, useValue: notifySpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ApiClientsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load clients on init', () => {
    expect(component).toBeTruthy();
    expect(apiClientsApiSpy.listClients).toHaveBeenCalledWith(0, 20, 'createdAt,desc');
    expect(component.clients().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('should render client name, key prefix, and status', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Simulator Acquirer');
    expect(el.textContent).toContain('mck_12345678...');
    expect(el.textContent).toContain('Legacy Terminal');
  });

  it('should open create dialog when Create Client button is clicked', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(null),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openCreateDialog();
    expect(spy).toHaveBeenCalled();
  });

  it('should handle revoke confirmation and reload clients', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(true),
    } as unknown as ReturnType<typeof dialog.open>);

    apiClientsApiSpy.revokeClient.and.returnValue(of({ ...mockClients[0], status: 'REVOKED' }));

    component.confirmRevoke(mockClients[0]);

    expect(dialog.open).toHaveBeenCalled();
    expect(apiClientsApiSpy.revokeClient).toHaveBeenCalledWith('c1');
    expect(notifySpy.success).toHaveBeenCalled();
  });

  it('should display error state when list fails', () => {
    apiClientsApiSpy.listClients.and.returnValue(throwError(() => new Error('Error')));
    component.loadClients();
    fixture.detectChanges();

    expect(component.error()).toBeTruthy();
  });
});
