export interface SuTeamRef {
  id: string;
  name: string;
}

interface MembershipRef {
  teamRole: string;
  team: { id: string };
}

/**
 * The current team of the page «swiss unihockey» (SUH-16, E-116): the first own team as a player, then as a
 * coach (the rule of `/fahrerliste`, DRV-24), but only among the `teams` the person may choose from. Without an
 * own team the first team by name; without any team `null`.
 */
export function defaultSuTeamId(teams: SuTeamRef[], memberships: MembershipRef[]): string | null {
  const known = new Set(teams.map((t) => t.id));
  const own = memberships.filter((m) => known.has(m.team.id));
  const first = own.find((m) => m.teamRole === 'PLAYER') ?? own[0];
  if (first) return first.team.id;
  const byName = [...teams].sort((a, b) => a.name.localeCompare(b.name, 'de'));
  return byName[0]?.id ?? null;
}

/** The team shown: the requested one (`?team=<id>`) if the person may choose it, otherwise the current team. */
export function chosenSuTeamId(
  teams: SuTeamRef[],
  memberships: MembershipRef[],
  requested: string | null | undefined,
): string | null {
  if (requested && teams.some((t) => t.id === requested)) return requested;
  return defaultSuTeamId(teams, memberships);
}
