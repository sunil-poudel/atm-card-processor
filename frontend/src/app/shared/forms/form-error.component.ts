import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

@Component({
  selector: 'mc-form-error',
  standalone: true,
  template: `
    @if (control() && control()!.invalid && (control()!.dirty || control()!.touched)) {
      <div class="mc-error-text" role="alert">
        {{ errorMessage }}
      </div>
    }
  `,
  styles: [
    `
      .mc-error-text {
        color: var(--mc-danger);
        font-size: 0.8125rem;
        margin-top: 4px;
        line-height: 1.25;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormErrorComponent {
  readonly control = input<AbstractControl | null>(null);
  readonly customMessages = input<Record<string, string>>({});

  get errorMessage(): string {
    const ctrl = this.control();
    if (!ctrl || !ctrl.errors) {
      return '';
    }

    const errors = ctrl.errors;
    const custom = this.customMessages();

    if (errors['serverError']) {
      return String(errors['serverError']);
    }
    if (errors['required']) {
      return custom['required'] || 'This field is required';
    }
    if (errors['email']) {
      return custom['email'] || 'Please enter a valid email address';
    }
    if (errors['minlength']) {
      return (
        custom['minlength'] || `Must be at least ${errors['minlength'].requiredLength} characters`
      );
    }
    if (errors['maxlength']) {
      return (
        custom['maxlength'] || `Must not exceed ${errors['maxlength'].requiredLength} characters`
      );
    }
    if (errors['min']) {
      return custom['min'] || `Minimum value is ${errors['min'].min}`;
    }
    if (errors['max']) {
      return custom['max'] || `Maximum value is ${errors['max'].max}`;
    }
    if (errors['pattern']) {
      return custom['pattern'] || 'Invalid format';
    }

    const firstKey = Object.keys(errors)[0];
    return custom[firstKey] || 'Field contains invalid data';
  }
}
