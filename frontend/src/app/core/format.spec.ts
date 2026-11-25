import { date, dateTime, name, seasonStatus, teamRoleText } from './format';
import type { Season } from '../api/types';

const season = (over: Partial<Season>): Season => ({
  id: 's1',
  name: '2026/27',
  startDate: '2026-07-01',
  endDate: '2027-06-30',
  current: false,
  archived: false,
  ...over,
});

describe('format', () => {
  it('joins first and last name', () => {
    expect(name({ firstName: 'Petra', lastName: 'Keller' })).toBe('Petra Keller');
  });

  describe('teamRoleText', () => {
    it('translates known roles', () => {
      expect(teamRoleText('PLAYER')).toBe('Spielerin');
      expect(teamRoleText('COACH')).toBe('Coach');
    });

    it('falls back to «Mitglied» for unknown, empty and missing roles (PTF-06)', () => {
      expect(teamRoleText('GOALIE_COACH')).toBe('Mitglied');
      expect(teamRoleText('player')).toBe('Mitglied');
      expect(teamRoleText('')).toBe('Mitglied');
      expect(teamRoleText(null)).toBe('Mitglied');
      expect(teamRoleText(undefined)).toBe('Mitglied');
    });
  });

  it('writes an ISO date as day.month.year', () => {
    expect(date('2026-07-01')).toBe('01.07.2026');
  });

  describe('dateTime', () => {
    it('shows Swiss winter time', () => {
      expect(dateTime('2026-11-21T14:03:00+01:00')).toBe('21.11.2026, 14:03');
    });

    it('converts other offsets to Swiss time (summer)', () => {
      expect(dateTime('2026-07-15T12:05:00Z')).toBe('15.07.2026, 14:05');
    });

    it('rolls over midnight and year in Swiss time', () => {
      expect(dateTime('2026-12-31T23:30:00Z')).toBe('01.01.2027, 00:30');
    });
  });

  describe('seasonStatus', () => {
    it('is «aktuell» for the current season', () => {
      expect(seasonStatus(season({ current: true }))).toBe('aktuell');
    });

    it('is «Archiv» for an archived season', () => {
      expect(seasonStatus(season({ archived: true }))).toBe('Archiv');
    });

    it('is «geplant» otherwise', () => {
      expect(seasonStatus(season({}))).toBe('geplant');
    });

    it('prefers «aktuell» over «Archiv» if both flags are set', () => {
      expect(seasonStatus(season({ current: true, archived: true }))).toBe('aktuell');
    });
  });
});
