# Glossary

Binding vocabulary for the repository (NF-40, decision E-68). Code, API, database and documents use the English terms in this table. The user interface stays German and uses the German terms.

The switch to English is complete since F-11 (E-68, E-75): code, tests, test IDs, scripts and configuration all use these terms. Only the exceptions listed in NF-40 stay German.

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
| Feldspielerin | field player | player outside the goalies (F-16) |
| Warnung | warning | types `GOALIES`, `FIELD_PLAYERS`, `LINE` (F-16) |
| Mindestzahl (für Warnungen) | warning minimum | per team (F-16) |
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
| Ort (Termin) | location | place of an event: name, address, map link (`location` in the API) |
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
| Anwesenheit | attendance | tile «Anwesenheit» |
| Präsenzkontrolle, «Präsenz erfassen» | attendance check | switch per event or series, off by default (PRS-01) |
| Präsenz (erfasst) | attendance record | who recorded and when, plus one status per player (PRS-04) |
| anwesend / abwesend / entschuldigt | present / absent / excused | values `PRESENT`, `ABSENT`, `EXCUSED` (PRS-03) |
| Statistik | statistics | per player of a team, per period (STA) |
| Zusagequote | attending rate | share of «dabei» of the events that count (STA-03) |
| Präsenzquote | attendance rate | share of «anwesend» of the recorded values (STA-04) |
| Teamschnitt | team average | mean per player (STA-05) |
| Im Team seit | joined on | day of joining a team in a season (STA-07) |
| Fahrerliste | driver list | |
| Übernahme (neue Saison) | carry-over | copying teams, memberships and lines into a new season |
| Abmelden (Sitzung) | log out | not to be confused with declining an event |
| Hinweise (Termin) | notes | |
| Verschiebung (Termin) | rescheduling | |
| Entwurf | draft | |
| Heim / Auswärts | home / away | |
| Doppelrolle | dual role | |
| Gültigkeitszeitraum | validity period | |

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
| Produktionspipeline | production pipeline | |
| freigeben / Freigabe | release | |
| anstossen | trigger | subcommand `anstossen` stays |
| Übergabe | handover | |
| Abschlusskommentar | final comment | |
| Startkommentar | start comment | |
| Muss / Sollte / Hinweis | Must / Should / Note | severity of review findings |
| Fehler / Beobachtung (Test) | error / observation | test findings |
| Fall / Grenzfall | case / edge case | in test plans |
| Ausgangslage | starting point | in test plans |
| Rundgang | walkthrough | |
| trocken (Probelauf) | dry run | flag `--trocken` stays |
| Leitplanken | guardrails | |

## Status values of requirements

| German | English |
|---|---|
| Entschieden | Decided |
| Angenommen | Accepted |
| Annahme | Assumption |
| Offen | Open |
| Später | Later |

The meaning of each value is explained in the [README](../README.md#requirement-status).
