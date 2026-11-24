# Test plan · Batch «event variants» (E-86 to E-89)

Executable tests: `../tests/event-variants.spec.ts`. All scenarios run against the **test data set** with the simulators (docs/test-data.md), on mobile and desktop. In the test data set the match day variants of all teams make a call-up; everyone can drive with 5 places; there are no special line-ups and no invited teams.

Default "now": Mon 16.11.2026 06:00. Seed: event 01 is a practice of Team 1 and Team 2; event 04 is a match day of Team 1 from swiss unihockey (matches against UHC Musterdorf and Unihockey Beispielstadt), call-up in draft, Petra's full car, Sara's empty car, Jana «dabei» without a seat, Kim «nicht dabei». League of Team 1: rank 2 of 8, UHC Musterdorf 1st, Unihockey Beispielstadt 3rd.

## Colours and names (VAR-04, VAR-05, TER-14)

| Scenario | Steps | Expected |
|---|---|---|
| Event list | As `anna` open `/termine`, then event 04 | Event 01 with the chip «Training» (`data-tag` `GREEN`), event 04 «swiss unihockey Spieltag» (`BLUE`); the same chip under the title of the event; no axe findings |
| Change a variant | As `petra` `/verwaltung/teams/<Team 1>`, «Variante «Training» bearbeiten», empty the name, then «Teamtraining» and «Orange» | Four variants listed; an empty name is refused; «Variante «Teamtraining» ist gespeichert.»; the event list shows «Teamtraining» in orange |
| Contrast | Nine variants, one per colour | Every colour is listed; no axe findings in the list of variants and in the event list |
| Own variant (VAR-02, VAR-03) | «Variante hinzufügen»: «Cupspiel», «Spieltag», «Rot»; create a match day with «Variante» «Cupspiel»; delete the variant | Five variants; the event shows «Cupspiel» in red; after deleting, the event shows «Spieltag» |
| No permission (RR-07) | As `anna` open `/verwaltung/teams/<Team 1>` | «Keine Berechtigung», no variants |

## Call-up and sections (VAR-06, VAR-07)

| Scenario | Steps | Expected |
|---|---|---|
| Responses only | As `petra` switch «Aufgebot machen» off for «swiss unihockey Spieltag»; open event 04 as `petra` and as `anna` | The variant says «nur An- und Abmeldungen»; event 04 has «Spiele» but no section «Aufgebot»; the driver list counts «Spielerinnen mit «dabei»»; Anna sees no call-up status |
| Order of the sections | Event 04 as `petra`; then move «Fahrerliste» to the top and hide «Zeitplan» | Before: «Meine Antwort», «Anmeldungen», «Spiele», «Aufgebot», «Zeitplan», «Fahrerliste», «Angaben». «Angaben» cannot be hidden. After: «Meine Antwort», «Fahrerliste», «Anmeldungen», «Spiele», «Aufgebot», «Angaben» |

## Automatic departure and ranks (TML-09, AUF-34)

| Scenario | Steps | Expected |
|---|---|---|
| Automatic departure | Match day with a meeting point without a time and a match at 10:00 | Meeting point «08:20 · Parkplatz Sporthalle Nord»; the departure of the timeline is marked «automatisch gesetzt» |
| Ranks | Event 04 as `petra`; follow «Tabelle ansehen»; open the call-up | «2. Rang von 8 · Herren Kleinfeld 3. Liga Gruppe 12»; «Gegner: 1. Rang», «Gegner: 3. Rang»; the link opens the league page; the call-up shows the card «Tabelle» |

The suggestion without the automatic departure (TML-04) is covered in `club-data.spec.ts`, with the automatic switched off for «Spieltag».

## Driver list (DRV-18 to DRV-20, ANM-13)

| Scenario | Steps | Expected |
|---|---|---|
| Proposal | As `petra` «Vorschlag erarbeiten» on event 04, «Vorschlag übernehmen», then again | Sara's car «schon eingetragen» with Jana «neu»; «Der Vorschlag ist übernommen.»; nobody without a seat; the second proposal says there is nothing to propose |
| Players | As `anna` open event 04 | No button «Vorschlag erarbeiten» |
| Driving settings | As `anna` `/profil`: 1 place, then 7; open event 04; as `petra` the season plan of Team 1 | «Bitte eine Zahl von 2 bis 9 eingeben.»; saved; «Freie Plätze in deinem Auto» starts at 6; Petra sees 7 places for Anna and switches «kann fahren» off |
| With the response | As `jana` tick «Ich reise individuell an …» on event 04; take it back in the driver list | «Du reist individuell an und brauchst keinen Platz.»; Jana under «Reist individuell an», nobody without a seat, chip in the overview; afterwards she is without a seat again |
| By a coach | As `petra` «Jana Wyss reist individuell an»; open event 01 | Jana under «Reist individuell an»; a practice offers no such choice |

## Special line-ups (LIN-13 to LIN-16)

| Scenario | Steps | Expected |
|---|---|---|
| Set up and use | As `petra` `/teams/<Team 1>/linien`: «Aufstellung hinzufügen», name «Überzahl», 2 places, «Center» with Anna, Anna twice, then Kim; open event 04; edit to 3 places with Lea; delete | A player twice is refused; saved and listed; event 04 shows «Aufstellungen» with Anna «dabei» and Kim «fehlt»; three places after the change; deleted |
| Players | As `anna` open the lines page | She sees the line-up with an empty place «frei» and no buttons |

## Expected or invited (TER-15 to TER-17)

| Scenario | Steps | Expected |
|---|---|---|
| Practice with an invited team | As `petra` create «Donnerstagstraining» for Team 1 and Team 2; both «freiwillig», then Team 1 «erwartet» | «Mindestens ein Team muss erwartet sein.»; saved; teams «Team 1 + Team 2 (freiwillig)», chip «freiwillig» at Team 2 in the overview; no mark for Petra |
| Marked and hidden | As `eva` (Team 2 only) `/termine`; untick «Freiwillige Termine anzeigen», reload, tick again; open the event and respond | Chip «Freiwillig»; `?freiwillig=aus` hides the entry and stays after a reload, «1 freiwilliger Termin ist ausgeblendet.»; the event says «Für dich freiwillig»; responding works |
| Not for expected people | As `anna` `/termine` | No chip, no checkbox |
| Counts | Practice with Team 2 invited, as `petra` | «7 offen» in total; Team 2 «0 offen» |

Reminders, statistics and warnings of invited teams are covered by backend tests (`OptionalTeamsTest`).

## By hand (club data set)

Start the app (`scripts/start.sh`), open `/dev-login`: card «Datensatz: Vereinsdaten».

1. As `monika` (coach of «Damen KF 3. Liga II»): `/termine` shows the Thursday practices with «Freiwillig»; the checkbox hides them.
2. As `petra`: the next match day of «Damen KF 1. Liga» has no «Aufgebot», shows ranks, an automatic departure, «Aufstellungen» and «Vorschlag erarbeiten».
3. As `reto`: a match day of «Herren KF 3. Liga» has the section «Aufgebot»; `/verwaltung/teams/<Herren KF 3. Liga>` → «Terminvarianten».
4. As `reto`: the next Tuesday practice lists «Herren KF 4. Liga II (freiwillig)».
5. As a junior coach (`marc`): «Fahrerliste der Saison» of «Junioren A» lists the players as «fährt nicht».
