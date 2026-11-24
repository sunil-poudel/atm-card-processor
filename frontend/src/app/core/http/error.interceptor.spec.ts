import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from '../api/api-error';
import { NotificationService } from '../notify/notification.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    notifySpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'error',
      'success',
      'info',
      'warning',
    ]);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notifySpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should convert ProblemDetails response into ApiError', (done) => {
    const problemBody = {
      type: 'urn:minicard:problem:insufficient-funds',
      title: 'Insufficient Funds',
      status: 422,
      detail: 'Balance is less than transaction amount',
      code: 'INSUFFICIENT_FUNDS',
      requestId: 'req-12345',
    };

    http.get('/api/test').subscribe({
      next: () => done.fail('Should have failed'),
      error: (err: unknown) => {
        expect(err instanceof ApiError).toBeTrue();
        const apiError = err as ApiError;
        expect(apiError.code).toBe('INSUFFICIENT_FUNDS');
        expect(apiError.status).toBe(422);
        expect(apiError.detail).toBe('Balance is less than transaction amount');
        done();
      },
    });

    const req = httpTesting.expectOne('/api/test');
    req.flush(problemBody, {
      status: 422,
      statusText: 'Unprocessable Entity',
      headers: { 'Content-Type': 'application/problem+json' },
    });
  });

  it('should notify on 403 Forbidden', (done) => {
    http.get('/api/forbidden').subscribe({
      next: () => done.fail('Should fail'),
      error: () => {
        expect(notifySpy.error).toHaveBeenCalledWith(jasmine.stringMatching(/Access denied/i));
        done();
      },
    });

    const req = httpTesting.expectOne('/api/forbidden');
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
  });

  it('should notify with requestId on 500 Server Error', (done) => {
    const errorBody = {
      type: 'urn:minicard:problem:internal-error',
      title: 'Internal Server Error',
      status: 500,
      detail: 'An error occurred',
      requestId: 'req-999',
    };

    http.get('/api/error').subscribe({
      next: () => done.fail('Should fail'),
      error: () => {
        expect(notifySpy.error).toHaveBeenCalledWith(jasmine.stringMatching(/req-999/));
        done();
      },
    });

    const req = httpTesting.expectOne('/api/error');
    req.flush(errorBody, { status: 500, statusText: 'Internal Server Error' });
  });
});
