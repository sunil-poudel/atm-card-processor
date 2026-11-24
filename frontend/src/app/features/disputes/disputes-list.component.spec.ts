import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { NotificationService } from '../../core/notify/notification.service';
import { ReferenceDataService } from '../../core/reference/reference-data.service';
import { DisputeResponse, DisputesApiService } from './disputes-api.service';
import { DisputesListComponent } from './disputes-list.component';

describe('DisputesListComponent', () => {
  let component: DisputesListComponent;
  let fixture: ComponentFixture<DisputesListComponent>;
  let disputesApiSpy: jasmine.SpyObj<DisputesApiService>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  const mockDisputes: DisputeResponse[] = [
    {
      id: 'd-1',
      transactionId: 'tx-1',
      cardId: 'c-1',
      cardLast4: '1234',
      reason: 'UNAUTHORIZED_TRANSACTION',
      status: 'OPENED',
      amountMinor: 50000,
      currency: 'NPR',
      openedBy: 'analyst@minicard.dev',
      openedAt: '2026-10-21T07:00:00Z',
      evidenceDueAt: '2099-11-04T07:00:00Z',
    },
    {
      id: 'd-2',
      transactionId: 'tx-2',
      cardId: 'c-2',
      cardLast4: '5678',
      reason: 'DUPLICATE_CHARGE',
      status: 'EVIDENCE_REQUESTED',
      amountMinor: 20000,
      currency: 'NPR',
      openedBy: 'officer@minicard.dev',
      openedAt: '2026-10-01T07:00:00Z',
      evidenceDueAt: '2026-10-15T07:00:00Z', // past due date
    },
  ];

  beforeEach(async () => {
    disputesApiSpy = jasmine.createSpyObj('DisputesApiService', ['listDisputes']);
    notifySpy = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
      'info',
      'warning',
    ]);

    disputesApiSpy.listDisputes.and.returnValue(
      of({
        content: mockDisputes,
        page: {
          number: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        },
      }),
    );

    await TestBed.configureTestingModule({
      imports: [DisputesListComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        { provide: DisputesApiService, useValue: disputesApiSpy },
        { provide: NotificationService, useValue: notifySpy },
        {
          provide: ReferenceDataService,
          useValue: {
            getReferenceData: () =>
              of({
                currencies: ['NPR'],
                mccs: {},
                responseCodes: {},
                declineReasons: {},
                disputeReasons: ['UNAUTHORIZED_TRANSACTION', 'DUPLICATE_CHARGE'],
              }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DisputesListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and list disputes with pagination', () => {
    expect(component).toBeTruthy();
    expect(disputesApiSpy.listDisputes).toHaveBeenCalled();
    expect(component.disputes().length).toBe(2);
    expect(component.totalElements()).toBe(2);
  });

  it('should detect overdue disputes correctly', () => {
    expect(component.isOverdue(mockDisputes[0].evidenceDueAt)).toBeFalse();
    expect(component.isOverdue(mockDisputes[1].evidenceDueAt)).toBeTrue();
  });

  it('should format reason strings neatly', () => {
    expect(component.formatReason('UNAUTHORIZED_TRANSACTION')).toBe('Unauthorized Transaction');
    expect(component.formatReason(undefined)).toBe('-');
  });

  it('should apply filters and reset pageIndex to 0', () => {
    component.filterForm.patchValue({ status: 'OPENED' });
    component.applyFilters();

    expect(component.pageIndex()).toBe(0);
    expect(disputesApiSpy.listDisputes).toHaveBeenCalledWith(
      jasmine.objectContaining({ status: 'OPENED', page: 0 }),
    );
  });

  it('should reset filters to default and reload', () => {
    component.filterForm.patchValue({ status: 'OPENED', overdue: true });
    component.resetFilters();

    expect(component.filterForm.value.status).toBe('');
    expect(component.filterForm.value.overdue).toBeFalse();
    expect(component.pageIndex()).toBe(0);
  });

  it('should handle API errors gracefully', () => {
    disputesApiSpy.listDisputes.and.returnValue(
      throwError(() => ({ detail: 'Network error loading disputes' })),
    );

    component.loadDisputes();

    expect(component.loading()).toBeFalse();
    expect(component.error()).toBe('Network error loading disputes');
  });
});
