import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '../../../core/notify/notification.service';
import {
  ReferenceDataResponse,
  ReferenceDataService,
} from '../../../core/reference/reference-data.service';
import { CardResponse, CardsApiService } from '../cards-api.service';
import { CardIssueDialogComponent } from './card-issue-dialog.component';

describe('CardIssueDialogComponent', () => {
  let component: CardIssueDialogComponent;
  let fixture: ComponentFixture<CardIssueDialogComponent>;
  let cardsApiSpy: jasmine.SpyObj<CardsApiService>;
  let refDataSpy: jasmine.SpyObj<ReferenceDataService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<CardIssueDialogComponent>>;
  let router: Router;

  const mockRefData: ReferenceDataResponse = {
    currencies: ['NPR', 'USD'],
    mccs: { '5411': 'Grocery Stores', '7995': 'Betting and Casino' },
    responseCodes: {},
    declineReasons: {},
    disputeReasons: [],
  };

  const mockCreatedCardWithPan: CardResponse = {
    id: 'new-card-1',
    maskedPan: '411111******1111',
    holderName: 'Bob Smith',
    currency: 'NPR',
    status: 'INACTIVE',
    balanceMinor: 0,
    pan: '4111111111111111',
  };

  beforeEach(async () => {
    cardsApiSpy = jasmine.createSpyObj<CardsApiService>('CardsApiService', ['issueCard']);
    refDataSpy = jasmine.createSpyObj<ReferenceDataService>('ReferenceDataService', [
      'getReferenceData',
    ]);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<CardIssueDialogComponent>>('MatDialogRef', [
      'close',
    ]);

    refDataSpy.getReferenceData.and.returnValue(of(mockRefData));

    await TestBed.configureTestingModule({
      imports: [CardIssueDialogComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: CardsApiService, useValue: cardsApiSpy },
        { provide: ReferenceDataService, useValue: refDataSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['success', 'error']),
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(CardIssueDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should validate form and reject dailyLimit less than perTxLimit', () => {
    component.form.patchValue({
      holderName: 'Alice',
      perTransactionLimit: '500.00',
      dailyLimit: '200.00',
    });

    component.onSubmit();

    expect(cardsApiSpy.issueCard).not.toHaveBeenCalled();
    expect(component.form.controls.dailyLimit.errors).toBeTruthy();
  });

  it('should issue card and show one-time reveal when pan is returned', () => {
    cardsApiSpy.issueCard.and.returnValue(of(mockCreatedCardWithPan));

    component.form.patchValue({
      holderName: 'Bob Smith',
      currency: 'NPR',
      validityYears: 3,
      perTransactionLimit: '500.00',
      dailyLimit: '1000.00',
      initialBalance: '0.00',
    });

    component.onSubmit();

    expect(cardsApiSpy.issueCard).toHaveBeenCalled();
    expect(component.issuedCard()).toEqual(mockCreatedCardWithPan);

    // One-time reveal completed
    component.panAcknowledged.set(true);
    component.onFinishedReveal();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(mockCreatedCardWithPan);
    expect(router.navigate).toHaveBeenCalledWith(['/cards', 'new-card-1']);
  });
});
