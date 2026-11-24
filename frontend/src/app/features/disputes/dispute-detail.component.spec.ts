import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { NotificationService } from '../../core/notify/notification.service';
import { DisputeDetailComponent } from './dispute-detail.component';
import { DisputeDetail, DisputesApiService } from './disputes-api.service';

describe('DisputeDetailComponent', () => {
  let component: DisputeDetailComponent;
  let fixture: ComponentFixture<DisputeDetailComponent>;
  let disputesApiSpy: jasmine.SpyObj<DisputesApiService>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  const mockDispute: DisputeDetail = {
    id: 'disp-101',
    transactionId: 'tx-101',
    cardId: 'card-101',
    cardLast4: '4321',
    reason: 'UNAUTHORIZED_TRANSACTION',
    description: 'Unauthorized charge reported by cardholder',
    status: 'OPENED',
    amountMinor: 75000,
    currency: 'NPR',
    openedBy: 'officer@minicard.dev',
    openedAt: '2026-10-21T07:00:00Z',
    evidenceDueAt: '2099-11-04T07:00:00Z',
    allowedActions: ['REQUEST_EVIDENCE'],
    events: [
      {
        id: 'evt-1',
        fromStatus: undefined,
        toStatus: 'OPENED',
        actor: 'officer@minicard.dev',
        note: 'Dispute opened upon customer report',
        createdAt: '2026-10-21T07:00:00Z',
      },
    ],
    transaction: {
      id: 'tx-101',
      merchantName: 'Bhatbhateni Supermarket',
      mcc: '5411',
      createdAt: '2026-10-21T06:30:00Z',
    },
  };

  beforeEach(async () => {
    disputesApiSpy = jasmine.createSpyObj('DisputesApiService', ['getDispute']);
    notifySpy = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
      'info',
      'warning',
    ]);

    disputesApiSpy.getDispute.and.returnValue(of(mockDispute));

    await TestBed.configureTestingModule({
      imports: [DisputeDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        { provide: DisputesApiService, useValue: disputesApiSpy },
        { provide: NotificationService, useValue: notifySpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? 'disp-101' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DisputeDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and load dispute detail', () => {
    expect(component).toBeTruthy();
    expect(disputesApiSpy.getDispute).toHaveBeenCalledWith('disp-101');
    expect(component.dispute()?.id).toBe('disp-101');
    expect(component.dispute()?.allowedActions).toEqual(['REQUEST_EVIDENCE']);
  });

  it('should format reason text cleanly', () => {
    expect(component.formatReason('UNAUTHORIZED_TRANSACTION')).toBe('Unauthorized Transaction');
    expect(component.formatReason(undefined)).toBe('-');
  });

  it('should check overdue evidence correctly', () => {
    expect(component.isOverdue(mockDispute.evidenceDueAt)).toBeFalse();
    expect(component.isOverdue('2020-01-01T00:00:00Z')).toBeTrue();
  });

  it('should open request evidence dialog and update dispute state', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const updatedDispute: DisputeDetail = {
      ...mockDispute,
      status: 'EVIDENCE_REQUESTED',
      allowedActions: ['SUBMIT_EVIDENCE'],
    };

    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(updatedDispute),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openRequestEvidenceDialog('disp-101');
    expect(spy).toHaveBeenCalled();
    expect(component.dispute()?.status).toBe('EVIDENCE_REQUESTED');
  });

  it('should open submit evidence dialog and update dispute state', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const updatedDispute: DisputeDetail = {
      ...mockDispute,
      status: 'UNDER_REVIEW',
      allowedActions: ['RESOLVE_WON', 'RESOLVE_LOST'],
    };

    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(updatedDispute),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openSubmitEvidenceDialog('disp-101');
    expect(spy).toHaveBeenCalled();
    expect(component.dispute()?.status).toBe('UNDER_REVIEW');
  });

  it('should open resolve dispute dialog and update dispute state', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const updatedDispute: DisputeDetail = {
      ...mockDispute,
      status: 'RESOLVED_WON',
      allowedActions: [],
    };

    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(updatedDispute),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openResolveDialog('disp-101', 'WON', 75000, 'NPR');
    expect(spy).toHaveBeenCalled();
    expect(component.dispute()?.status).toBe('RESOLVED_WON');
  });

  it('should handle load error gracefully', () => {
    disputesApiSpy.getDispute.and.returnValue(throwError(() => ({ detail: 'Dispute not found' })));

    component.loadDispute();

    expect(component.loading()).toBeFalse();
    expect(component.error()).toBe('Dispute not found');
  });
});
