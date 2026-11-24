import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { NotificationService } from '../../core/notify/notification.service';
import { ReferenceDataService } from '../../core/reference/reference-data.service';
import { FraudRuleResponse, FraudRulesApiService } from './fraud-rules-api.service';
import { FraudRulesListComponent } from './fraud-rules-list.component';

describe('FraudRulesListComponent', () => {
  let component: FraudRulesListComponent;
  let fixture: ComponentFixture<FraudRulesListComponent>;
  let fraudApiSpy: jasmine.SpyObj<FraudRulesApiService>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  const mockRules: FraudRuleResponse[] = [
    {
      id: 'r-1',
      name: 'High Amount Rule',
      type: 'MAX_AMOUNT',
      thresholdValue: 10000000,
      scorePoints: 30,
      action: 'FLAG',
      enabled: true,
      updatedAt: '2026-10-21T07:00:00Z',
      version: 1,
    },
    {
      id: 'r-2',
      name: 'Velocity 5 in 60s',
      type: 'VELOCITY',
      thresholdValue: 5,
      windowSeconds: 60,
      scorePoints: 40,
      action: 'FLAG',
      enabled: false,
      updatedAt: '2026-10-21T07:00:00Z',
      version: 1,
    },
    {
      id: 'r-3',
      name: 'Blocked Casino MCC',
      type: 'BLOCKED_MCC',
      mccCodes: ['7995'],
      scorePoints: 100,
      action: 'DECLINE',
      enabled: true,
      updatedAt: '2026-10-21T07:00:00Z',
      version: 1,
    },
  ];

  beforeEach(async () => {
    fraudApiSpy = jasmine.createSpyObj('FraudRulesApiService', [
      'listRules',
      'enableRule',
      'disableRule',
    ]);
    notifySpy = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
      'info',
      'warning',
    ]);

    fraudApiSpy.listRules.and.returnValue(of(mockRules));

    await TestBed.configureTestingModule({
      imports: [FraudRulesListComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        { provide: FraudRulesApiService, useValue: fraudApiSpy },
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
                disputeReasons: [],
              }),
          },
        },
      ],
    }).compileComponents();

    const authStore = TestBed.inject(AuthStore);
    authStore.setSession('fake-token', {
      id: 'admin-1',
      email: 'admin@minicard.dev',
      displayName: 'System Admin',
      role: 'ADMIN',
    });

    fixture = TestBed.createComponent(FraudRulesListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and list rules', () => {
    expect(component).toBeTruthy();
    expect(fraudApiSpy.listRules).toHaveBeenCalled();
    expect(component.rules().length).toBe(3);
  });

  it('should format parameters correctly for each rule type', () => {
    expect(component.formatParams(mockRules[0])).toContain('Amount >');
    expect(component.formatParams(mockRules[1])).toContain('>5 attempts in 60s');
    expect(component.formatParams(mockRules[2])).toContain('MCCs: 7995');
  });

  it('should toggle rule from enabled to disabled', () => {
    fraudApiSpy.disableRule.and.returnValue(of({ ...mockRules[0], enabled: false }));

    component.toggleRule(mockRules[0], false);

    expect(fraudApiSpy.disableRule).toHaveBeenCalledWith('r-1');
    expect(notifySpy.success).toHaveBeenCalledWith('Rule "High Amount Rule" disabled successfully');
  });

  it('should toggle rule from disabled to enabled', () => {
    fraudApiSpy.enableRule.and.returnValue(of({ ...mockRules[1], enabled: true }));

    component.toggleRule(mockRules[1], true);

    expect(fraudApiSpy.enableRule).toHaveBeenCalledWith('r-2');
    expect(notifySpy.success).toHaveBeenCalledWith('Rule "Velocity 5 in 60s" enabled successfully');
  });

  it('should open create dialog when admin clicks create button', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of({ id: 'new-r' }),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openCreateDialog();
    expect(spy).toHaveBeenCalled();
  });

  it('should open edit dialog when user clicks edit button', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(mockRules[0]),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openEditDialog(mockRules[0]);
    expect(spy).toHaveBeenCalled();
  });
});
