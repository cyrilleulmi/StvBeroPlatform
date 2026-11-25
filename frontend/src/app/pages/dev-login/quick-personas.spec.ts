import type { TestPerson } from '../../api/types';
import { quickPersonas, rolesLine, searchPeople } from './quick-personas';

const T1 = { teamId: 't1', teamName: 'Team 1' };
const T2 = { teamId: 't2', teamName: 'Team 2' };

function person(
  loginKey: string,
  firstName: string,
  lastName: string,
  teams: TestPerson['teams'] = [],
  admin = false,
): TestPerson {
  return { loginKey, id: loginKey, firstName, lastName, admin, teams };
}

const alex = person('alex', 'Alex', 'Admin', [], true);
const petra = person('petra', 'Petra', 'Keller', [
  { ...T1, teamRole: 'COACH' },
  { ...T2, teamRole: 'PLAYER' },
]);
const marc = person('marc', 'Marc', 'Huber', [{ ...T2, teamRole: 'COACH' }]);
const anna = person('anna', 'Anna', 'Muster', [{ ...T1, teamRole: 'PLAYER' }]);
const eva = person('eva', 'Eva', 'Steiner', [{ ...T2, teamRole: 'PLAYER' }]);
const vera = person('vera', 'Vera', 'Studer');

describe('quickPersonas (E-112)', () => {
  it('picks an admin, a coach, a player of her team, a coach who also plays and a person without a team', () => {
    const quick = quickPersonas([vera, eva, anna, marc, petra, alex]);
    expect(quick.map((q) => [q.role, q.person.loginKey])).toEqual([
      ['admin', 'alex'],
      ['coach', 'marc'],
      ['player', 'eva'],
      ['coach-player', 'petra'],
      ['no-team', 'vera'],
    ]);
    expect(quick.find((q) => q.role === 'coach-player')?.why).toBe('Coach Team 1, Spielerin Team 2');
  });

  it('is the same choice in any order of the people', () => {
    const a = quickPersonas([alex, petra, marc, anna, eva, vera]).map((q) => q.person.loginKey);
    const b = quickPersonas([eva, vera, petra, anna, alex, marc]).map((q) => q.person.loginKey);
    expect(a).toEqual(b);
  });

  it('leaves out a role nobody has, and takes any player if the coach has none', () => {
    const quick = quickPersonas([anna, marc]);
    expect(quick.map((q) => [q.role, q.person.loginKey])).toEqual([
      ['coach', 'marc'],
      ['player', 'anna'],
    ]);
  });

  it('shows nothing without people', () => {
    expect(quickPersonas([])).toEqual([]);
  });
});

describe('rolesLine', () => {
  it('names admin, teams with roles, or no team', () => {
    expect(rolesLine(alex)).toBe('Admin');
    expect(rolesLine(anna)).toBe('Spielerin Team 1');
    expect(rolesLine(vera)).toBe('Ohne Team');
  });
});

describe('searchPeople', () => {
  const all = [vera, petra, anna, alex];

  it('lists everybody once by last name without a search', () => {
    expect(searchPeople(all, '').map((p) => p.loginKey)).toEqual(['alex', 'petra', 'anna', 'vera']);
  });

  it('finds by first name, last name or key, case does not matter, every word must match', () => {
    expect(searchPeople(all, 'KEL').map((p) => p.loginKey)).toEqual(['petra']);
    expect(searchPeople(all, 'anna muster').map((p) => p.loginKey)).toEqual(['anna']);
    expect(searchPeople(all, 'anna keller')).toEqual([]);
  });
});
