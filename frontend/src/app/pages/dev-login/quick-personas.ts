import type { TestPerson } from '../../api/types';
import { TEXTS } from '../../core/texts';

/** The roles of the tiles «Oft gebraucht» (E-112): part of the test ID `dev-login-quick-<role>`. */
export type QuickRole = 'admin' | 'coach' | 'player' | 'coach-player' | 'no-team';

export interface QuickPersona {
  role: QuickRole;
  person: TestPerson;
  /** Why this person, e.g. «Coach Team 1, Spielerin Team 2». */
  why: string;
}

/** Order of the API (last name, first name), so that the same people come on every load. */
function byName(a: TestPerson, b: TestPerson): number {
  return (
    a.lastName.localeCompare(b.lastName, 'de-CH') ||
    a.firstName.localeCompare(b.firstName, 'de-CH') ||
    a.loginKey.localeCompare(b.loginKey)
  );
}

const coaches = (p: TestPerson) => p.teams.filter((m) => m.teamRole === 'COACH');
const plays = (p: TestPerson) => p.teams.filter((m) => m.teamRole === 'PLAYER');

/** Teams and roles of a person in one line: «Admin», «Coach Team 1, Spielerin Team 2», «Ohne Team». */
export function rolesLine(person: TestPerson): string {
  const parts = person.teams.map((m) => `${TEXTS.teamRole[m.teamRole]} ${m.teamName}`);
  if (person.admin) parts.unshift(TEXTS.devLogin.admin);
  return parts.length > 0 ? parts.join(', ') : TEXTS.devLogin.noTeam;
}

/**
 * The people most often needed for trying things out by hand (E-112), chosen by role so that it works in both
 * data sets: an admin, a coach who only coaches, a player of that coach's team (or any player), a person who is
 * coach in one team and player in another, and a person without a team. A role nobody has is left out. Among
 * several candidates the first by name wins, so the choice is the same on every load.
 */
export function quickPersonas(people: readonly TestPerson[]): QuickPersona[] {
  const sorted = [...people].sort(byName);
  const admin = sorted.find((p) => p.admin);
  const coach = sorted.find((p) => !p.admin && coaches(p).length > 0 && plays(p).length === 0);
  const coachTeams = new Set(coach ? coaches(coach).map((m) => m.teamId) : []);
  const players = sorted.filter((p) => !p.admin && p.teams.length > 0 && coaches(p).length === 0);
  const player = players.find((p) => p.teams.some((m) => coachTeams.has(m.teamId))) ?? players[0];
  const dual = sorted.find((p) => !p.admin && coaches(p).length > 0 && plays(p).length > 0);
  const noTeam = sorted.find((p) => !p.admin && p.teams.length === 0);
  const chosen: [QuickRole, TestPerson | undefined][] = [
    ['admin', admin],
    ['coach', coach],
    ['player', player],
    ['coach-player', dual],
    ['no-team', noTeam],
  ];
  return chosen
    .filter((entry): entry is [QuickRole, TestPerson] => entry[1] !== undefined)
    .map(([role, person]) => ({ role, person, why: rolesLine(person) }));
}

/** Everybody once, by name, filtered by the search (first name, last name or key; case does not matter). */
export function searchPeople(people: readonly TestPerson[], term: string): TestPerson[] {
  const words = term.trim().toLowerCase().split(/\s+/).filter((w) => w.length > 0);
  return [...people]
    .sort(byName)
    .filter((p) => {
      const text = `${p.firstName} ${p.lastName} ${p.loginKey}`.toLowerCase();
      return words.every((w) => text.includes(w));
    });
}
