import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusChipComponent } from './status-chip.component';

@Component({
  standalone: true,
  imports: [StatusChipComponent],
  template: `<mc-status-chip [status]="status" [customLabel]="customLabel"></mc-status-chip>`,
})
class TestHostComponent {
  status = 'ACTIVE';
  customLabel?: string;
}

describe('StatusChipComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render status chip with active class and label', () => {
    const el = fixture.nativeElement as HTMLElement;
    const chip = el.querySelector('.status-chip');
    expect(chip).toBeTruthy();
    expect(chip?.classList.contains('chip-success')).toBeTrue();
    expect(chip?.textContent?.trim()).toBe('ACTIVE');
  });

  it('should format status code into human label if no custom label provided', () => {
    host.status = 'EVIDENCE_REQUESTED';
    fixture.detectChanges();

    const chip = fixture.nativeElement.querySelector('.status-chip');
    expect(chip?.classList.contains('chip-warning')).toBeTrue();
    expect(chip?.textContent?.trim()).toBe('EVIDENCE REQUESTED');
  });

  it('should display custom label when provided', () => {
    host.status = 'BLOCKED';
    host.customLabel = 'Fraud Block';
    fixture.detectChanges();

    const chip = fixture.nativeElement.querySelector('.status-chip');
    expect(chip?.classList.contains('chip-danger')).toBeTrue();
    expect(chip?.textContent?.trim()).toBe('Fraud Block');
  });
});
