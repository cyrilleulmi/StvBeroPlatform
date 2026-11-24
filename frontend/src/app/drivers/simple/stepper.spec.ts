import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Stepper } from './stepper';

function setup(inputs: Record<string, unknown>) {
  const fixture: ComponentFixture<Stepper> = TestBed.createComponent(Stepper);
  fixture.componentRef.setInput('label', 'Plätze');
  fixture.componentRef.setInput('value', 4);
  for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
  const emitted: number[] = [];
  fixture.componentInstance.changed.subscribe((v) => emitted.push(v));
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const [less, more] = Array.from(el.querySelectorAll('button'));
  return { fixture, el, less, more, emitted };
}

describe('Stepper', () => {
  it('shows the value and the label', () => {
    const { el } = setup({ value: 4, testId: 'seats' });
    expect(el.querySelector('[data-testid="seats"]')?.textContent).toBe('4');
    expect(el.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Plätze');
  });

  it('emits value - 1 and value + 1', () => {
    const { less, more, emitted } = setup({ value: 4 });
    less.click();
    more.click();
    expect(emitted).toEqual([3, 5]);
  });

  it('disables «−» at the minimum (default 1) and not before', () => {
    expect(setup({ value: 1 }).less.disabled).toBe(true);
    expect(setup({ value: 2 }).less.disabled).toBe(false);
  });

  it('disables «+» at the maximum (default 60) and not before', () => {
    expect(setup({ value: 60 }).more.disabled).toBe(true);
    expect(setup({ value: 59 }).more.disabled).toBe(false);
  });

  it('respects custom bounds, also if the value is already outside', () => {
    expect(setup({ value: 2, min: 2, max: 9 }).less.disabled).toBe(true);
    expect(setup({ value: 9, min: 2, max: 9 }).more.disabled).toBe(true);
    expect(setup({ value: 0, min: 2, max: 9 }).less.disabled).toBe(true);
    expect(setup({ value: 12, min: 2, max: 9 }).more.disabled).toBe(true);
  });

  it('disables both buttons when disabled, and emits nothing', () => {
    const { less, more, emitted } = setup({ value: 4, disabled: true });
    expect(less.disabled).toBe(true);
    expect(more.disabled).toBe(true);
    less.click();
    more.click();
    expect(emitted).toEqual([]);
  });

  it('names the buttons for screen readers with the label', () => {
    const { less, more } = setup({ label: 'Plätze von Petra' });
    expect(less.getAttribute('aria-label')).toContain('Plätze von Petra');
    expect(more.getAttribute('aria-label')).toContain('Plätze von Petra');
    expect(less.getAttribute('aria-label')).not.toBe(more.getAttribute('aria-label'));
  });
});
