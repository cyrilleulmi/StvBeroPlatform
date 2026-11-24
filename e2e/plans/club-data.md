# Test plan · Batch «club data» (E-85)

Executable tests: `../tests/club-data.spec.ts`. All scenarios run against the **test data set** with the simulators (docs/test-data.md), on mobile and desktop. The club data set itself talks to the real swiss unihockey and is not part of the automated tests; it is tried by hand (see «By hand» below).

Default "now": Mon 16.11.2026 06:00. Seed: match day 04 of Team 1 (matches 09:30 and 11:45, meeting point 08:00 «Parkplatz Sporthalle Nord», route 23.4 km and 26 minutes, Petra's full car, Sara's empty car).

## Test mode login

| Scenario | Steps | Expected |
|---|---|---|
| Grouped by team | Open `/dev-login` | Card «Datensatz: Testdaten»; groups «Admins», «Team 1», «Team 2», «Team 3», «Ohne Team»; in «Team 1» Petra is first, marked «Coach», with «auch in Team 2»; in «Team 2» she appears again as a player and Marc is first |
| Search | Type «team 3», then «zzz» | Only Team 3 with Tina; then «Keine Person gefunden.» |
| Login | Click Petra | Start page «Hallo Petra» |

## Linking with swiss unihockey (SUH-02, SUH-11)

| Scenario | Steps | Expected |
|---|---|---|
| Dialog | As `petra` (coach) open `/verwaltung/teams/<Team 2>`, «Team verknüpfen» | Dialog «Team 2 mit swiss unihockey verknüpfen»; «Herren KF 3. Liga (schon verknüpft mit Team 1)» cannot be chosen; without a choice the dialog asks for one |
| Link and unlink | Choose «Damen KF 2. Liga», «Verknüpfen»; then «Verknüpfung lösen» | «Verknüpft mit Damen KF 2. Liga (Team-ID 900002). Die Spiele sind geladen.», link with «Letzter Abgleich: …», button «Jetzt abgleichen»; afterwards «Nicht verknüpft.» |
| Team list unavailable (SUH-09) | Simulator: outage of the team list; open the dialog as `alex` | Message that swiss unihockey cannot be reached; the team ID can be typed; linking works |

## Changed matches (SUH-12)

| Scenario | Steps | Expected |
|---|---|---|
| Warning with before and after | Simulator: match 02 at 12:15; sync as `petra`; open match day 04 | On the match «Von swiss unihockey geändert: Zeit: So 22.11. 11:45 → So 22.11. 12:15»; overview warning «1 Spiel wurde von swiss unihockey geändert» |
| Confirm | Click «Gesehen» | Message «… ist als gesehen markiert.», warning gone |
| Players | Open the match day as `anna` | She sees what changed, but no button «Gesehen» |

## swiss unihockey not reachable (SUH-13)

| Scenario | Steps | Expected |
|---|---|---|
| Failed sync | Simulator: outage for 900001; as `petra` «Jetzt abgleichen» | Error message; the link says «Letzter Abgleich am … fehlgeschlagen.» |
| Hint | Open the start page as `petra`, then as `anna` | Petra: hint «swiss unihockey war beim letzten Abgleich nicht erreichbar (Team 1, …)» with the link «Zur Verknüpfung: Team 1»; Anna: no hint; the app works |

## Timeline (TML)

| Scenario | Steps | Expected |
|---|---|---|
| Whole day (TML-01) | As `anna` open match day 04 | 08:00 Abfahrt, 08:26 Ankunft · 64 Min. vor dem 1. Spiel (one point), Spiel 1 09:30–10:20, Zeit zwischen den Spielen 85 Min., Spiel 2 11:45–12:35, Auslaufen und Duschen 20 Min., 13:21 Rückkehr (ungefähr); hint that the team arrives less than 70 minutes before the first match; no button |
| Take over the suggestion (TML-04) | As `petra` «Vorschlag als Treffpunkt-Zeit übernehmen» | «Vorschlag für die Abfahrt: 07:50» before; afterwards «Die Treffpunkt-Zeit ist jetzt 07:50.», the hint is gone, the meeting point shows 07:50 |
| No meeting time (TML-04) | Automatic departure of «Spieltag» switched off (VAR-08); match day with a meeting point but no time, one match at 10:00 | Meeting point «Zeit offen · …»; departure 08:20 marked «Vorschlag» |
| Settings (TML-02, TML-03) | As `petra` set 60 and 45 minutes under `/verwaltung/teams/<Team 1>` | Invalid numbers are refused; the timeline says «Ein Spiel dauert 60 Minuten. Ankunft 45 Minuten vor dem ersten Spiel.» and match 1 ends at 10:30 |

## Driver plan of the season (DRV-14, DRV-17)

| Scenario | Steps | Expected |
|---|---|---|
| Plan for oneself | Two more match days; as `kim` open «Fahrerliste der Saison» from the team page; «Alle ohne Auto wählen», 4 seats, «Eintragen» | Match day 04 with its cars; no choice of the driver; «Kim Baumann fährt an 2 Spieltagen.»; her cars on both match days; «Verteilung der Fahrten» shows 2 planned trips; she can take herself off again |
| Plan for someone else | As `petra` choose «Kim Baumann», two match days, «Eintragen» | Kim's car on both; the match day shows it and links back to the plan |
| No permission | As `eva` (Team 2) open the plan of Team 1 | Error, no plan |

## By hand (club data set)

Start the app (`scripts/start.sh`), open `/dev-login`: card «Datensatz: Vereinsdaten».

1. As `petra`: next match day of «Damen KF 1. Liga» shows a changed match, a missing match, the timeline with a suggested departure and a driver list with a real route.
2. As `reto`: `/verwaltung/teams` → «Herren KF 3. Liga» → «Jetzt abgleichen» reports «1 geändert»; the match day then shows the warning.
3. As `ruth`: link «Junioren B» in the dialog; its match days appear (two cancelled matches at swiss unihockey are left out).
4. As `reto`: «Herren KF 4. Liga II» → «Fahrerliste der Saison» with eight hand-entered match days, past trips and the shared trips.
5. Without internet: the app starts, the linked teams have no match days and the start page of a coach says that swiss unihockey could not be reached.
