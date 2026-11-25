import { teamArea, teamAreaLinks } from './team-area';

describe('team areas (E-110)', () => {
  describe('teamArea', () => {
    it('shows «Mitglieder» without a parameter and for unknown values', () => {
      expect(teamArea(undefined, false)).toBe('members');
      expect(teamArea(null, false)).toBe('members');
      expect(teamArea('mitglieder', false)).toBe('members');
      expect(teamArea('irgendwas', false)).toBe('members');
    });

    it('reads «termine» and «einstellungen»', () => {
      expect(teamArea('termine', false)).toBe('events');
      expect(teamArea('einstellungen', false)).toBe('settings');
    });

    it('shows only the members in the archive', () => {
      expect(teamArea('termine', true)).toBe('members');
      expect(teamArea('einstellungen', true)).toBe('members');
    });
  });

  describe('teamAreaLinks', () => {
    it('lists the three areas, the default without a parameter', () => {
      expect(teamAreaLinks(false)).toEqual([
        { area: 'members', text: 'Mitglieder', query: { bereich: null } },
        { area: 'events', text: 'Termine', query: { bereich: 'termine' } },
        { area: 'settings', text: 'Einstellungen', query: { bereich: 'einstellungen' } },
      ]);
    });

    it('has no navigation in the archive', () => {
      expect(teamAreaLinks(true)).toEqual([]);
    });
  });
});
