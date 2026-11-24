import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResponseCounts, Scoreboard } from './scoreboard';

function setup(counts: ResponseCounts): ComponentFixture<Scoreboard> {
  const fixture = TestBed.createComponent(Scoreboard);
  fixture.componentRef.setInput('counts', counts);
  fixture.detectChanges();
  return fixture;
}

const cells = (fixture: ComponentFixture<Scoreboard>) =>
  Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.score')).map((el) => ({
    key: el.getAttribute('data-counter'),
    number: el.querySelector('.score-number')?.textContent,
    label: el.querySelector('.score-label')?.textContent,
  }));

describe('Scoreboard', () => {
  it('shows number and word of the four counters in order', () => {
    const fixture = setup({ attending: 8, declined: 3, maybe: 1, pending: 2 });
    expect(cells(fixture)).toEqual([
      { key: 'attending', number: '8', label: ' dabei' },
      { key: 'declined', number: '3', label: ' nicht dabei' },
      { key: 'maybe', number: '1', label: ' vielleicht' },
      { key: 'pending', number: '2', label: ' offen' },
    ]);
  });

  it('keeps the text readable for screen readers as «8 dabei · 3 nicht dabei · …»', () => {
    const fixture = setup({ attending: 8, declined: 3, maybe: 1, pending: 2 });
    const text = (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ').trim();
    expect(text).toBe('8 dabei · 3 nicht dabei · 1 vielleicht · 2 offen');
  });

  it('splits the label correctly for multi-digit and zero counts', () => {
    const fixture = setup({ attending: 12, declined: 0, maybe: 100, pending: 7 });
    expect(cells(fixture).map((c) => [c.number, c.label])).toEqual([
      ['12', ' dabei'],
      ['0', ' nicht dabei'],
      ['100', ' vielleicht'],
      ['7', ' offen'],
    ]);
  });

  it('renders a separator between the counters, but not after the last one', () => {
    const fixture = setup({ attending: 1, declined: 1, maybe: 1, pending: 1 });
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.separator')).toHaveLength(3);
  });

  it('updates when the counts change', () => {
    const fixture = setup({ attending: 1, declined: 0, maybe: 0, pending: 0 });
    fixture.componentRef.setInput('counts', { attending: 2, declined: 0, maybe: 0, pending: 0 });
    fixture.detectChanges();
    expect(cells(fixture)[0].number).toBe('2');
  });

  it('marks itself live only after the first render', async () => {
    const fixture = setup({ attending: 1, declined: 0, maybe: 0, pending: 0 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).classList.contains('live')).toBe(true);
  });
});
