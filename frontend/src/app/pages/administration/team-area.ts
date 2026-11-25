import { TEXTS } from '../../core/texts';

/** The three areas of `/verwaltung/teams/:teamId` (E-110). */
export type TeamArea = 'members' | 'events' | 'settings';

/** Value of `?bereich=` per area; the browser URL stays German (NF-40). «Mitglieder» is the default and not in the URL. */
const PARAM: Record<TeamArea, string | null> = {
  members: null,
  events: 'termine',
  settings: 'einstellungen',
};

export interface TeamAreaLink {
  area: TeamArea;
  text: string;
  /** Query parameters of the link; merged with the others (`?saison=` stays). */
  query: { bereich: string | null };
}

/**
 * The area that `?bereich=` asks for. Unknown values show «Mitglieder»; so does the archive (read-only), where only
 * the members exist.
 */
export function teamArea(param: string | null | undefined, readOnly: boolean): TeamArea {
  if (readOnly) return 'members';
  if (param === PARAM.events) return 'events';
  if (param === PARAM.settings) return 'settings';
  return 'members';
}

/** The links of the in-page navigation «Bereich»; none in the archive, where there is only one area. */
export function teamAreaLinks(readOnly: boolean): TeamAreaLink[] {
  if (readOnly) return [];
  const t = TEXTS.teamAdmin;
  const text: Record<TeamArea, string> = {
    members: t.areaMembers,
    events: t.areaEvents,
    settings: t.areaSettings,
  };
  return (['members', 'events', 'settings'] as const).map((area) => ({
    area,
    text: text[area],
    query: { bereich: PARAM[area] },
  }));
}
