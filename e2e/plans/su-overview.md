# Test plan SUH-16 · swiss unihockey opens the team overview

Requirement SUH-16, decision E-116 · Tests: `../tests/su-overview.spec.ts`

Seed (docs/test-data.md): Team 1 is linked (`900001`) and has league data, Team 2 and Team 3 are not linked. `anna` plays in Team 1, `eva` in
Team 2, `petra` coaches Team 1 and plays in Team 2, `marc` coaches Team 2, `alex` is admin without a team. Views: 375×812 and 1280×800, light and dark.

| No. | Case | Steps | Expected |
|---|---|---|---|
| S1 | Overview of the own team | `anna` opens `/swissunihockey` | League and group as heading, `league-table`, `league-upcoming`, `league-results`, link `su-open-at-su-900001` to `https://www.swissunihockey.ch/de/team-detail/?team_id=900001`; no `su-team-select` (one team), no `su-manage` |
| S2 | Choice in the URL | `alex` opens the page, chooses «Team 2», reloads, chooses «Team 1» | First team by name at the start; `?team=<id>` follows the choice and survives the reload; Team 2 shows `su-not-linked`, Team 1 the overview and `su-manage` |
| S3 | Fallback | `/swissunihockey?team=does-not-exist` as `alex`; `anna` with `?team=<Team 2>` | The current team is shown (Team 1) |
| S4 | Current team | `petra` opens the page | Team 2 (player before coach), `su-not-linked` |
| S5 | Player, not linked | `eva` | Card «Noch nicht mit swiss unihockey verbunden» with three steps and «Frag deinen Coach oder einen Admin.»; no button «Team verknüpfen», no `su-manage` |
| S6 | Coach links | `marc` → `su-link-now` → dialog, ID `900002` by hand → «Verknüpfen» | The card disappears, the overview (`league-info`) and `su-open-at-su-900002` show; «Verbindung verwalten» (`su-manage`) lists the link |
| S7 | No team | A person without a team (not coach or admin) | `su-page-no-team` «Du bist in dieser Saison in keinem Team.» |
| S8 | Liga page | `/teams/<Team 1>/liga` | Unchanged, see `f17-league-overview.md` |
