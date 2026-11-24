import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuditApiService } from './audit-api.service';
import { AuditLogComponent } from './audit-log.component';

describe('AuditLogComponent', () => {
  let component: AuditLogComponent;
  let fixture: ComponentFixture<AuditLogComponent>;
  let auditApiSpy: jasmine.SpyObj<AuditApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockEvents = [
    {
      id: 'ae1',
      occurredAt: '2026-01-01T10:00:00Z',
      actorType: 'USER' as const,
      actorLabel: 'admin@minicard.local',
      action: 'CARD_ISSUED',
      entityType: 'Card',
      entityId: '11111111-1111-1111-1111-111111111111',
      details: 'holderName=Alice,currency=NPR',
      requestId: 'req-12345678',
    },
  ];

  beforeEach(async () => {
    auditApiSpy = jasmine.createSpyObj<AuditApiService>('AuditApiService', ['listAuditEvents']);
    auditApiSpy.listAuditEvents.and.returnValue(
      of({
        content: mockEvents,
        page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
      }),
    );

    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuditApiService, useValue: auditApiSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParams: {} },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditLogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load events on init', () => {
    expect(component).toBeTruthy();
    expect(auditApiSpy.listAuditEvents).toHaveBeenCalled();
    expect(component.events().length).toBe(1);
    expect(component.loading()).toBe(false);
  });

  it('should render event action, actor, and entity type', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('CARD_ISSUED');
    expect(el.textContent).toContain('admin@minicard.local');
    expect(el.textContent).toContain('Card');
    expect(el.textContent).toContain('holderName=Alice,currency=NPR');
  });

  it('should apply filters and update router query params', () => {
    component.filterForm.patchValue({ action: 'USER_CREATED' });
    component.applyFilters();

    expect(routerSpy.navigate).toHaveBeenCalled();
    expect(auditApiSpy.listAuditEvents).toHaveBeenCalled();
  });

  it('should clear filters when clear button is clicked', () => {
    component.filterForm.patchValue({ action: 'USER_CREATED', entityType: 'AppUser' });
    component.clearFilters();

    expect(component.filterForm.value.action).toBe('');
    expect(component.filterForm.value.entityType).toBe('');
    expect(routerSpy.navigate).toHaveBeenCalled();
  });

  it('should display error state when API fails', () => {
    auditApiSpy.listAuditEvents.and.returnValue(throwError(() => new Error('Error')));
    component.loadEvents();
    fixture.detectChanges();

    expect(component.error()).toBeTruthy();
  });
});
