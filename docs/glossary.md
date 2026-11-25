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
| Kalender abonnieren, Kalenderabo | calendar subscription | F-18, CAL |
| Kalenderadresse | calendar address, feed path | personal address of the iCalendar feed with the token |
| Kalender-Feed | calendar feed | the iCalendar file behind the address |
| Adresse erneuern | renew (the address) | new token, the old address answers 404 |
| Fahrerliste | driver list | per match day (F-20, DRV) |
| Auto | car | a driver with free seats on a match day |
| Fahrerin, Fahrer | driver | |
| Mitfahrende, mitfahren | passenger, ride along | |
| Platz, freie Plätze | seat, seats | |
| Ohne Platz | without seat | players who still need a seat (DRV-04) |
| Strecke | route | from the meeting point to the venue, status `CALCULATED`, `NOT_FOUND`, `UNAVAILABLE`, `MANUAL` |
| Gutschrift | credit | kilometres credited to a driver: twice the distance (DRV-05) |
| Fahrten | trips | season summary per person (DRV-09) |
| Routendienst | routing service | OpenRouteService in production, simulator in dev/test (DRV-06) |
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
| Kanal (E-Mail, Push) | channel | `NotificationChannel` `EMAIL`, `PUSH`; the app is always there (F-19) |
| Push, Push-Mitteilung | push | Web Push through the service worker of the PWA |
| Gerät (mit Push) | push device, push subscription | `push_subscription`, `PushDevice` in the API |
| Zustellung | delivery | one e-mail or push of a notification (`delivery`, outbox) |
| Erinnerung | reminder | |
| Kachel | tile | |
| Dashboard / Start | dashboard / start page | |
| angeheftet, anheften (Termin) | pinned, pin | an event shown on the start page of the members of its teams until it is over (TER-26); `pinned`, `POST /api/events/{id}/pin` |
| Verwaltung | administration | |
| Admin | admin | |
| Rolle (Rechte) | role | permission role, not the team role |
| Recht | permission | |
| Archiv | archive | past seasons |
| Abgleich (swiss unihockey) | sync | |
| Verknüpfung (swiss unihockey) | link | a team at swiss unihockey linked with a team of the app for one season (SUH-11); `su_link`, `SuLink` |
| Zeitplan (Spieltag) | timeline | from departure to return (TML) |
| Terminvariante | event variant | how a kind of event looks and behaves in a team: name, colour, call-up or responses only, sections, automatic departure (VAR); four standard variants per team plus the team's own |
| Abschnitt (Terminseite) | section | part of the event page a variant shows and orders: `RESPONSES`, `MATCHES`, `CALL_UP`, `TIMELINE`, `DRIVER_LIST`, `LINEUPS`, `ATTENDANCE`, `DETAILS` (VAR-07) |
| Abfahrtszeit automatisch setzen | automatic departure | the meeting time follows the suggested departure until someone enters one by hand (TML-09) |
| Spezialaufstellung | special line-up | named line-up for a situation such as «Überzahl» (power play) or «Unterzahl» (penalty kill), 1 to 10 places (LIN-13); not a line |
| erwartet / freiwillig | expected / optional (invited) | per team of an event; invited people are not reminded and not counted as «offen» (TER-15); `EXPECTED`, `OPTIONAL` |
| Kann fahren, Plätze im Auto | driving settings | per person: can drive, places of the car including herself (DRV-18) |
| Vorschlag (Fahrerliste) | proposal | driver list worked out by the app; changes nothing until applied (DRV-19) |
| Reist individuell an | travels on her own (individual travel) | mark per person and match day: needs no seat (DRV-20, ANM-13) |
| Fahrerliste der Saison | driver plan | all match days of a team in a season with their cars (DRV-14) |
| Verteilung der Fahrten | shares | trips per member in the season plan (DRV-17) |
| Üblicher Treffpunkt | usual meeting point | per team, start value of new match days (AUF-32) |
| Datensatz | data set | test data set or club data set in dev (docs/test-data.md) |
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
