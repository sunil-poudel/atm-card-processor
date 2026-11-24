import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { CardResponse, CardsApiService, PageResponseCardResponse } from './cards-api.service';
import { CardsListComponent } from './cards-list.component';

describe('CardsListComponent', () => {
  let component: CardsListComponent;
  let fixture: ComponentFixture<CardsListComponent>;
  let cardsApiSpy: jasmine.SpyObj<CardsApiService>;
  let authStore: AuthStore;
  let router: Router;

  const mockCard: CardResponse = {
    id: 'card-1',
    maskedPan: '411111******1111',
    holderName: 'John Doe',
    currency: 'NPR',
    status: 'ACTIVE',
    balanceMinor: 500000,
    expiryMonth: 12,
    expiryYear: 2028,
    expired: false,
    createdAt: '2026-01-01T00:00:00Z',
  };

  const mockPageResponse: PageResponseCardResponse = {
    content: [mockCard],
    page: {
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    },
  };

  beforeEach(async () => {
    cardsApiSpy = jasmine.createSpyObj<CardsApiService>('CardsApiService', [
      'listCards',
      'issueCard',
    ]);
    cardsApiSpy.listCards.and.returnValue(of(mockPageResponse));

    await TestBed.configureTestingModule({
      imports: [CardsListComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        AuthStore,
        { provide: CardsApiService, useValue: cardsApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (_k: string) => null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    authStore = TestBed.inject(AuthStore);
    authStore.setSession('token', {
      id: 'admin-1',
      email: 'admin@minicard.local',
      displayName: 'Admin User',
      role: 'ADMIN',
    });

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(CardsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load cards on initialization', () => {
    expect(cardsApiSpy.listCards).toHaveBeenCalled();
    expect(component.cards().length).toBe(1);
    expect(component.cards()[0].maskedPan).toBe('411111******1111');
  });

  it('should navigate to card details on row click', () => {
    component.onRowClick(mockCard);
    expect(router.navigate).toHaveBeenCalledWith(['/cards', 'card-1']);
  });

  it('should format expiry as MM/YY', () => {
    expect(component.formatExpiry(5, 2027)).toBe('05/27');
    expect(component.formatExpiry(12, 2030)).toBe('12/30');
    expect(component.formatExpiry(undefined, undefined)).toBe('-');
  });

  it('should open issue dialog when openIssueDialog is invoked', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(mockCard),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openIssueDialog();
    expect(dialog.open).toHaveBeenCalled();
  });

  it('should clear filters and reload', () => {
    component.filterForm.patchValue({ q: 'Search query', status: 'ACTIVE' });
    expect(component.isDefaultFilters()).toBeFalse();

    component.clearFilters();
    expect(component.filterForm.value.q).toBe('');
    expect(component.filterForm.value.status).toBe('');
    expect(component.isDefaultFilters()).toBeTrue();
  });
});
