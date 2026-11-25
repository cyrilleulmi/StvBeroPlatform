/**
 * Team chips of the event list (E-111): which teams are on, and how this is written into the URL. The own teams
 * are on by default (TER-13) and then the URL carries nothing; any other choice is `?teams=<id>,<id>`. The old
 * `?team=<id>` of earlier links (team page, «Verwaltung», shared links) still works as a choice of one team.
 */

export interface TeamOption {
  id: string;
  name: string;
}

/** The URL parameters of the team choice; `null` removes a parameter. */
export interface TeamParams {
  teams: string | null;
  team: null;
}

function split(value: string | null | undefined): string[] {
  return [
    ...new Set(
      (value ?? '')
        .split(',')
        .map((id) => id.trim())
        .filter((id) => id.length > 0),
    ),
  ];
}

/** Teams that are on: `?teams=` if given, otherwise the old `?team=`, otherwise the defaults (the own teams). */
export function selectedTeams(
  teams: string | null | undefined,
  team: string | null | undefined,
  defaults: readonly string[],
): string[] {
  const chosen = split(teams);
  if (chosen.length > 0) return chosen;
  const old = split(team);
  if (old.length > 0) return old;
  return [...defaults];
}

/** The same teams as the defaults, in any order. */
export function isDefault(selected: readonly string[], defaults: readonly string[]): boolean {
  const set = new Set(defaults);
  return selected.length === set.size && selected.every((id) => set.has(id));
}

/** Teams to ask the API for: none means «the own teams», which is the default. */
export function apiTeams(selected: readonly string[], defaults: readonly string[]): string[] {
  return isDefault(selected, defaults) ? [] : [...selected];
}

/**
 * The chip that cannot be switched off: the last team that is on, so that the list never ends up empty by
 * accident. Not for people without own teams (an admin), whose default is no team at all.
 */
export function lockedTeam(selected: readonly string[], defaults: readonly string[]): string | null {
  return selected.length === 1 && defaults.length > 0 ? selected[0] : null;
}

/**
 * The URL parameters after switching one team on or off, in the order of the chips. `null` if nothing changes
 * (the last team cannot be switched off, see {@link lockedTeam}).
 */
export function toggleTeam(
  selected: readonly string[],
  id: string,
  on: boolean,
  defaults: readonly string[],
  order: readonly string[],
): TeamParams | null {
  const next = new Set(selected);
  if (on) next.add(id);
  else next.delete(id);
  if (next.size === 0 && defaults.length > 0) return null;
  const ids = [...order.filter((x) => next.has(x)), ...[...next].filter((x) => !order.includes(x))];
  return { teams: isDefault(ids, defaults) ? null : ids.join(','), team: null };
}

/**
 * The chips, by name: the teams to choose from (all teams of the season for coaches and admins, E-69, otherwise
 * the own ones), plus a chosen team that is not among them (a shared link), named as the events name it.
 */
export function teamOptions(
  choices: readonly TeamOption[],
  selected: readonly string[],
  known: readonly TeamOption[],
  fallbackName: string,
): TeamOption[] {
  const sorted = [...choices].sort((a, b) => a.name.localeCompare(b.name, 'de-CH'));
  const extra = selected
    .filter((id) => !sorted.some((t) => t.id === id))
    .map((id) => ({ id, name: known.find((t) => t.id === id)?.name ?? fallbackName }));
  return [...sorted, ...extra];
}
