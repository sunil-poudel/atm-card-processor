import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { NotificationService } from '../../core/notify/notification.service';
import { CardDetailComponent } from './card-detail.component';
import { CardResponse, CardsApiService } from './cards-api.service';

describe('CardDetailComponent', () => {
  let component: CardDetailComponent;
  let fixture: ComponentFixture<CardDetailComponent>;
  let cardsApiSpy: jasmine.SpyObj<CardsApiService>;
  let authStore: AuthStore;
  let dialog: MatDialog;

  const mockCard: CardResponse = {
    id: 'card-1',
    maskedPan: '411111******1111',
    holderName: 'Alice Doe',
    currency: 'NPR',
    status: 'INACTIVE',
    balanceMinor: 0,
    perTransactionLimitMinor: 5000000,
    dailyLimitMinor: 10000000,
    blockedMccs: ['7995'],
    expiryMonth: 6,
    expiryYear: 2028,
    expired: false,
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    cardsApiSpy = jasmine.createSpyObj<CardsApiService>('CardsApiService', [
      'getCard',
      'getCardLedger',
      'getCardTransactions',
      'activateCard',
      'unblockCard',
      'closeCard',
    ]);

    cardsApiSpy.getCard.and.returnValue(of(mockCard));
    cardsApiSpy.getCardLedger.and.returnValue(of({ content: [], page: { totalElements: 0 } }));
    cardsApiSpy.getCardTransactions.and.returnValue(
      of({ content: [], page: { totalElements: 0 } }),
    );

    await TestBed.configureTestingModule({
      imports: [CardDetailComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CardsApiService, useValue: cardsApiSpy },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['success', 'error']),
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (k: string) => (k === 'id' ? 'card-1' : null),
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

    fixture = TestBed.createComponent(CardDetailComponent);
    component = fixture.componentInstance;
    dialog = fixture.debugElement.injector.get(MatDialog);
    fixture.detectChanges();
  });

  it('should load card details, ledger, and transactions on init', () => {
    expect(cardsApiSpy.getCard).toHaveBeenCalledWith('card-1');
    expect(cardsApiSpy.getCardLedger).toHaveBeenCalled();
    expect(cardsApiSpy.getCardTransactions).toHaveBeenCalled();
    expect(component.card()).toEqual(mockCard);
  });

  it('should activate an INACTIVE card after confirm', () => {
    spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(true),
    } as unknown as ReturnType<typeof dialog.open>);

    const activeCard: CardResponse = { ...mockCard, status: 'ACTIVE' };
    cardsApiSpy.activateCard.and.returnValue(of(activeCard));

    component.activateCard();

    expect(dialog.open).toHaveBeenCalled();
    expect(cardsApiSpy.activateCard).toHaveBeenCalledWith('card-1');
    expect(component.card()?.status).toBe('ACTIVE');
  });

  it('should close card when confirmed and balance is 0', () => {
    spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(true),
    } as unknown as ReturnType<typeof dialog.open>);

    const closedCard: CardResponse = { ...mockCard, status: 'CLOSED' };
    cardsApiSpy.closeCard.and.returnValue(of(closedCard));

    component.closeCard();

    expect(dialog.open).toHaveBeenCalled();
    expect(cardsApiSpy.closeCard).toHaveBeenCalledWith('card-1');
    expect(component.card()?.status).toBe('CLOSED');
  });
});
