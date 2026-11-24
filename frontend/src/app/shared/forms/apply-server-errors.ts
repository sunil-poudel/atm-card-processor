import { FormGroup } from '@angular/forms';
import { ApiError } from '../../core/api/api-error';

export function applyServerErrors(form: FormGroup, error: unknown): void {
  if (!(error instanceof ApiError) || !error.errors || error.errors.length === 0) {
    return;
  }

  for (const fieldErr of error.errors) {
    const control = form.get(fieldErr.field);
    if (control) {
      control.setErrors({
        serverError: fieldErr.message,
      });
      control.markAsTouched();
    }
  }
}
