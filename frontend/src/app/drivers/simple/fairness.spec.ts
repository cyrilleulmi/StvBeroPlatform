import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../../core/auth.service';
import { Fairness } from './fairness';
import { PlanStore } from './plan-store';
import { plan, setting, share } from './plan.testing';

function setup(inputs: Record<string, unknown> = {}) {
  const me = signal<{ id: string; now: string } | null>({
    id: 'cleo',
    now: '2026-11-24T10:00:00Z',
  });
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      PlanStore,
      { provide: AuthService, useValue: { me } },
    ],
  });
  const store = TestBed.inject(PlanStore);
  const fixture: ComponentFixture<Fairness> = TestBed.createComponent(Fairness);
  for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
  const render = () => fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const rows = () =>
    Array.from(el.querySelectorAll('li.fair-row')).map((li) => li.getAttribute('data-testid'));
  return { store, fixture, el, render, rows };
}

const samplePlan = () =>
  plan({
    drivers: [
      setting('anna'),
      setting('berta', { canDrive: false }),
      setting('cleo', { canChange: true }),
      setting('dora'),
    ],
    shares: [
      share('anna', { trips: 3, plannedTrips: 1, kilometres: 100, plannedKilometres: 20 }),
      share('cleo', { trips: 1, plannedTrips: 0, kilometres: 40 }),
      share('dora', { trips: 1, plannedTrips: 0, kilometres: 10 }),
    ],
  });

describe('Fairness', () => {
  it('lists the members by name (order of the drivers) by default, without a rank', () => {
    const { store, render, rows, el } = setup();
    store.plan.set(samplePlan());
    render();
    expect(rows()).toEqual(['fair-anna', 'fair-berta', 'fair-cleo', 'fair-dora']);
    expect(el.querySelector('.rank')).toBeNull();
  });

  it('lists the queue with ranks, the next in line first and those who do not drive last with «–»', () => {
    const { store, render, rows, el } = setup({ order: 'queue' });
    store.plan.set(samplePlan());
    render();
    // fewest trips first, ties by kilometres: dora (1, 10 km), cleo (1, 40 km), anna (4), then berta
    expect(rows()).toEqual(['fair-dora', 'fair-cleo', 'fair-anna', 'fair-berta']);
    expect(Array.from(el.querySelectorAll('.rank')).map((r) => r.textContent)).toEqual([
      '1',
      '2',
      '3',
      '–',
    ]);
  });

  it('shows one filled dot per done trip and one outlined dot per planned trip', () => {
    const { store, render, el } = setup();
    store.plan.set(samplePlan());
    render();
    const anna = el.querySelector('[data-testid="fair-anna"]')!;
    expect(anna.querySelectorAll('.dot.done')).toHaveLength(3);
    expect(anna.querySelectorAll('.dot.planned')).toHaveLength(1);
    expect(anna.getAttribute('data-trips')).toBe('4');
  });

  it('marks the signed-in person and shows «no» for those who do not drive', () => {
    const { store, render, el } = setup();
    store.plan.set(samplePlan());
    render();
    expect(el.querySelector('[data-testid="fair-cleo"]')?.getAttribute('data-me')).toBe('true');
    expect(el.querySelector('[data-testid="fair-anna"]')?.hasAttribute('data-me')).toBe(false);
    const berta = el.querySelector('[data-testid="fair-berta"]')!;
    expect(berta.getAttribute('data-can-drive')).toBe('false');
    expect(berta.querySelector('.dot')).toBeNull();
  });

  it('offers no «Anpassen» if nobody can be changed', () => {
    const none = setup();
    none.store.plan.set(plan({ drivers: [setting('anna')], shares: [] }));
    none.render();
    expect(none.el.querySelector('[data-testid="fairness-edit"]')).toBeNull();
  });

  it('switches into edit mode with a checkbox and a stepper for drivers who can be changed', () => {
    const { store, render, el, fixture } = setup();
    store.plan.set(samplePlan());
    render();
    (el.querySelector('[data-testid="fairness-edit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelectorAll('input[type="checkbox"]')).toHaveLength(1);
    expect(el.querySelector('[data-testid="fair-cleo"] app-stepper')).not.toBeNull();
    expect(el.querySelector('[data-testid="fair-anna"] input')).toBeNull();
  });

  it('shows a member without a share with zero trips', () => {
    const { store, render, el } = setup();
    store.plan.set(plan({ drivers: [setting('anna')], shares: [] }));
    render();
    expect(el.querySelector('[data-testid="fair-anna"]')?.getAttribute('data-trips')).toBe('0');
  });
});
