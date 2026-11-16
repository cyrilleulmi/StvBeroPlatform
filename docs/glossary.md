# Glossary

Binding vocabulary for the repository (NF-40, decision E-68). Code, API, database and documents use the English terms in this table. The user interface stays German and uses the German terms.

If a term is missing, add it here in the same style before using it.

## Domain terms

| German | English | Notes |
|---|---|---|
| Konto | account | one login; today exactly one person per account |
| Person | person | |
| Team | team | |
| Saison | season | |
| Funktion (im Team) | team role | values `PLAYER`, `COACH` |
| Teamzugehörigkeit | team membership | |
| Spielerin | player | |
| Coach | coach | |
| Linie | line | |
| Gruppe (Linie oder Goalies) | group | |
| Goalies | goalies | |
| Ohne Linie | no line | display group for players without a group |
| Termin | event | types `PRACTICE`, `MATCH_DAY`, `OTHER` |
| Training | practice | |
| Spieltag | match day | |
| Anlass | other event | |
| Spiel | match | |
| Gegner | opponent | |
| Anspielzeit | start time | of a match |
| Heimspiel | home match | |
| Serie | series | |
| Ausnahme (einer Serie) | series exception | |
| Antwort | response | |
| dabei / nicht dabei / vielleicht / offen | `ATTENDING` / `DECLINED` / `MAYBE` / `PENDING` | |
| Antwortfrist | response deadline | |
| kurzfristige Absage | late cancellation | |
| stellvertretend antworten | respond on behalf | |
| Absage (eines Termins) | cancellation | |
| Aufgebot | call-up | states `NONE`, `DRAFT`, `PUBLISHED` |
| Aufgebotseintrag | call-up entry | one person in a call-up |
| aufgeboten | called up | |
| Treffpunkt | meeting point | |
| Spielort | venue | |
| Halle | hall | |
| Resultat | result | |
| Benachrichtigung | notification | |
| Erinnerung | reminder | |
| Kachel | tile | |
| Dashboard / Start | dashboard / start page | |
| Verwaltung | administration | |
| Admin | admin | |
| Rolle (Rechte) | role | permission role, not the team role |
| Recht | permission | |
| Archiv | archive | past seasons |
| Abgleich (swiss unihockey) | sync | |
| swiss unihockey | swiss unihockey | proper name, also «SU» |
| Darstellung | theme | |
| Übersicht (Anwesenheit) | overview | |

## Project and pipeline terms

| German | English | Notes |
|---|---|---|
| Anforderung | requirement | |
| Entscheid | decision | |
| Offener Punkt | open point | |
| Annahme | assumption | |
| Akzeptanzkriterium | acceptance criterion | |
| Voraussetzung | prerequisite | keyword `Voraussetzung:` in issue bodies stays German |
| Nachbesserung | follow-up fix | label stays `typ:nachbesserung` |
| Nacharbeit | rework | label stays `pipeline:nacharbeit` |
| Runde | round | one rework cycle |
| Befund | finding | |
| Sperre | lock | label `agent:aktiv` |
| Warteschlange | queue | |
| Wächter | watchdog | skill name stays `pipeline-waechter` |
| Statusseite | status page | |
| Fachmodell | domain model | |
| Testdaten / Seed | test data / seed | |
| Szenario | scenario | `S1`, `S2`, … |
| Testplan | test plan | `e2e/plans/` |
| Grundgerüst | foundation | F-00 |

## Status values of requirements

| German | English |
|---|---|
| Entschieden | Decided |
| Angenommen | Accepted |
| Annahme | Assumption |
| Offen | Open |
| Später | Later |

The meaning of each value is explained in the [README](../README.md#requirement-status).
