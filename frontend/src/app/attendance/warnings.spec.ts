import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { Warning } from '../api/types';
import { Warnings, warningText } from './warnings';

const warning = (over: Partial<Warning>): Warning => ({
  type: 'GOALIES',
  team: { id: 't1', name: 'Damen 1. Liga' },
  count: 1,
  minimum: 2,
  ...over,
});

describe('warningText', () => {
  it('words missing goalies, in singular, plural and zero', () => {
    expect(warningText(warning({ type: 'GOALIES', count: 0 }), false, false)).toBe(
      'Kein Goalie angemeldet',
    );
    expect(warningText(warning({ type: 'GOALIES', count: 1 }), false, false)).toBe(
      'Nur 1 Goalie angemeldet',
    );
    expect(warningText(warning({ type: 'GOALIES', count: 2 }), false, false)).toBe(
      'Nur 2 Goalies angemeldet',
    );
  });

  it('words missing field players, in singular, plural and zero', () => {
    expect(warningText(warning({ type: 'FIELD_PLAYERS', count: 0 }), false, false)).toBe(
      'Keine Feldspielerinnen angemeldet',
    );
    expect(warningText(warning({ type: 'FIELD_PLAYERS', count: 1 }), false, false)).toBe(
      'Nur 1 Feldspielerin angemeldet',
    );
    expect(warningText(warning({ type: 'FIELD_PLAYERS', count: 7 }), false, false)).toBe(
      'Nur 7 Feldspielerinnen angemeldet',
    );
  });

  it('says «aufgeboten» instead of «angemeldet» for a call-up', () => {
    expect(warningText(warning({ type: 'GOALIES', count: 1 }), true, false)).toBe(
      'Nur 1 Goalie aufgeboten',
    );
    expect(warningText(warning({ type: 'FIELD_PLAYERS', count: 0 }), true, false)).toBe(
      'Keine Feldspielerinnen aufgeboten',
    );
  });

  it('words an under-staffed line with its name', () => {
    expect(
      warningText(warning({ type: 'LINE', groupName: 'Linie 2', count: 2 }), false, false),
    ).toBe('Linie 2 nur zu 2');
  });

  it('does not crash on a line without a name', () => {
    expect(warningText(warning({ type: 'LINE', count: 2 }), false, false)).toBe(' nur zu 2');
  });

  it('words changed and missing matches in singular and plural', () => {
    expect(warningText(warning({ type: 'MATCH_CHANGED', count: 1 }), false, false)).toBe(
      '1 Spiel wurde von swiss unihockey geändert',
    );
    expect(warningText(warning({ type: 'MATCH_CHANGED', count: 3 }), false, false)).toBe(
      '3 Spiele wurden von swiss unihockey geändert',
    );
    expect(warningText(warning({ type: 'MATCH_MISSING', count: 1 }), false, false)).toBe(
      '1 Spiel gibt es bei swiss unihockey nicht mehr',
    );
    expect(warningText(warning({ type: 'MATCH_MISSING', count: 2 }), false, false)).toBe(
      '2 Spiele gibt es bei swiss unihockey nicht mehr',
    );
  });

  it('prefixes the team name, except for match warnings which belong to the match day (SUH-12)', () => {
    expect(warningText(warning({ type: 'GOALIES', count: 1 }), false, true)).toBe(
      'Damen 1. Liga: Nur 1 Goalie angemeldet',
    );
    expect(
      warningText(warning({ type: 'LINE', groupName: 'Linie 1', count: 3 }), false, true),
    ).toBe('Damen 1. Liga: Linie 1 nur zu 3');
    expect(warningText(warning({ type: 'MATCH_CHANGED', count: 2 }), false, true)).toBe(
      '2 Spiele wurden von swiss unihockey geändert',
    );
  });
});

describe('Warnings component', () => {
  function setup(
    warnings: Warning[],
    inputs: Record<string, unknown> = {},
  ): ComponentFixture<Warnings> {
    const fixture = TestBed.createComponent(Warnings);
    fixture.componentRef.setInput('warnings', warnings);
    fixture.componentRef.setInput('testId', 'warnings');
    for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
    fixture.detectChanges();
    return fixture;
  }
  const root = (f: ComponentFixture<Warnings>) => f.nativeElement as HTMLElement;

  it('keeps the live region but shows nothing without warnings', () => {
    const el = root(setup([]));
    const region = el.querySelector('[data-testid="warnings"]');
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(region?.getAttribute('data-warning-count')).toBe('0');
    expect(el.querySelector('section')).toBeNull();
  });

  it('lists every warning with its type and text', () => {
    const el = root(
      setup([
        warning({ type: 'GOALIES', count: 0 }),
        warning({ type: 'LINE', groupName: 'Linie 2', count: 2, groupId: 'g2' }),
      ]),
    );
    const items = Array.from(el.querySelectorAll('[data-testid="warning"]'));
    expect(items.map((i) => i.getAttribute('data-warning'))).toEqual(['GOALIES', 'LINE']);
    expect(items.map((i) => i.textContent?.trim())).toEqual([
      'Kein Goalie angemeldet',
      'Linie 2 nur zu 2',
    ]);
    expect(el.querySelector('[data-testid="warnings"]')?.getAttribute('data-warning-count')).toBe(
      '2',
    );
  });

  it('uses the call-up wording and the team prefix when asked to', () => {
    const el = root(
      setup([warning({ type: 'GOALIES', count: 1 })], { calledUp: true, withTeam: true }),
    );
    expect(el.querySelector('[data-testid="warning"]')?.textContent?.trim()).toBe(
      'Damen 1. Liga: Nur 1 Goalie aufgeboten',
    );
  });

  it('shows the default title, or a custom one', () => {
    expect(root(setup([warning({})])).querySelector('h3')?.textContent).toBe('Warnungen');
    expect(root(setup([warning({})], { title: 'Hinweise' })).querySelector('h3')?.textContent).toBe(
      'Hinweise',
    );
  });

  it('references its title for accessibility', () => {
    const el = root(setup([warning({})]));
    expect(el.querySelector('h3')?.id).toBe('warnings-title');
    expect(el.querySelector('section')?.getAttribute('aria-labelledby')).toBe('warnings-title');
  });
});
