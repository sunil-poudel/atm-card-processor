import { FormControl, FormGroup } from '@angular/forms';
import { ApiError } from '../../core/api/api-error';
import { applyServerErrors } from './apply-server-errors';

describe('applyServerErrors', () => {
  let form: FormGroup;

  beforeEach(() => {
    form = new FormGroup({
      holderName: new FormControl(''),
      dailyLimit: new FormControl(''),
    });
  });

  it('should apply server errors to matching form controls and mark them touched', () => {
    const error = new ApiError({
      type: 'urn:minicard:problem:validation-error',
      title: 'Bad Request',
      status: 400,
      detail: 'Validation failed',
      code: 'VALIDATION_FAILED',
      errors: [
        { field: 'holderName', message: 'Name must not be empty' },
        { field: 'dailyLimit', message: 'Must be greater than 0' },
      ],
    });

    applyServerErrors(form, error);

    expect(form.get('holderName')?.errors).toEqual({
      serverError: 'Name must not be empty',
    });
    expect(form.get('holderName')?.touched).toBeTrue();

    expect(form.get('dailyLimit')?.errors).toEqual({
      serverError: 'Must be greater than 0',
    });
    expect(form.get('dailyLimit')?.touched).toBeTrue();
  });

  it('should ignore errors for non-existent fields without failing', () => {
    const error = new ApiError({
      type: 'urn:minicard:problem:validation-error',
      title: 'Bad Request',
      status: 400,
      detail: 'Validation failed',
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'nonExistentField', message: 'Unknown' }],
    });

    expect(() => applyServerErrors(form, error)).not.toThrow();
  });

  it('should do nothing if error is not an ApiError or has no field errors', () => {
    applyServerErrors(form, new Error('Generic error'));
    expect(form.get('holderName')?.errors).toBeNull();

    const emptyApiError = new ApiError({
      type: 'urn:minicard:problem:not-found',
      title: 'Not Found',
      status: 404,
      detail: 'Card not found',
      code: 'CARD_NOT_FOUND',
    });
    applyServerErrors(form, emptyApiError);
    expect(form.get('holderName')?.errors).toBeNull();
  });
});
