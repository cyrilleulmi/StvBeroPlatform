# Test data (seed)

The seed is the fixed starting state for development, automated tests and the test agent. `POST /api/test/reset` restores exactly this state.

The data is chosen so that the core cases become visible: shared practice of two teams, dual roles, lines, goalies, «Ohne Linie», all four response states, match day with call-up.

**Rules**

- IDs are fixed. Tests and the test agent may use them, e.g. for deep links.
- Whoever changes the seed updates this file in the same PR. Existing IDs and counter values only change for a good reason, because tests rely on them.
- Names are made up.

## Clock

| | |
|---|---|
| Default "now" after reset | **Monday, 16.11.2026, 06:00** (Europe/Zurich) |
| Time zone | Europe/Zurich |
| Default response deadline | 24 hours before start (assumption for O-11) |
| Reminder | 12 hours before the response deadline (assumption) |

## ID scheme

All IDs are UUIDs. The first digit shows the type, the last two the number.

| Type | Pattern |
|---|---|
| Team | `10000000-0000-4000-8000-0000000000NN` |
| Person | `20000000-0000-4000-8000-0000000000NN` |
| Group (line, goalies) | `30000000-0000-4000-8000-0000000000NN` |
| Event | `40000000-0000-4000-8000-0000000000NN` |
| Match | `50000000-0000-4000-8000-0000000000NN` |
| Season | `60000000-0000-4000-8000-0000000000NN` |
| Series | `70000000-0000-4000-8000-0000000000NN` |
| Account | `80000000-0000-4000-8000-0000000000NN` (same number as the person) |
| Notification | `90000000-0000-4000-8000-0000000000NN` |

## Seed contents

The seed grows with the features. What is in the seed today:

| Feature | Content |
|---|---|
| F-00 | Seasons, all people with account and key, clock at the default "now". E-mail of every person: `<schluessel>@example.ch` |
| F-01 | Teams 01–03 (season 2026/27), Team 01 also in season 2025/26, team memberships with team role as in the table "People", still **without lines** (all players «ohne Linie»). Vera: player in Team 1, only season 2025/26. Alex is in no team. The team name applies per season: if Team 1 is renamed, 2025/26 still shows «Team 1» (SAI-02, follow-up fix #21) |
| F-02 | Groups 01–08 and line assignments as in the tables "Groups" and "People". Kim has no line. In addition group 09: goalies of Team 1 in season 2025/26 (empty, Vera has no line there) |
| F-03 | Series 01–03 with all their events within the validity period (including past ones), exception «Weihnachtsferien» for series 01, events 01–08 as in the table "Events with fixed IDs". Event 03 cancelled («Halle besetzt»). Match days 04 and 05 with venue, Google Maps link and meeting point, still without matches and call-up. Response deadline 24 hours everywhere |
| F-04 | Responses to events 01, 02, 04 and 05 as in the tables below (event 02: Anna «dabei»), all entered on 15.11.2026 20:00 by the person themselves, none late. Other events without responses |
| F-05 | Matches 01–03 and the call-ups of events 04 (draft) and 05 (published on 15.11.2026 21:00) as in the tables below. Matches 01 and 02 come from swiss unihockey (SU match ID `5000001` and `5000002`), match 03 was entered manually. No results yet |
| F-06 | Notifications 01–18 as in the table "Notifications" below. No reminders yet |
| F-07 | No new records. Team 1 is linked with team ID `900001`; after reset the simulator delivers exactly matches 01 and 02 (see [Simulated swiss unihockey data](#simulated-swiss-unihockey-data)) |

Series events without a fixed ID have the ID `UUID.nameUUIDFromBytes("termin-<Serie>-<Datum>")`, e.g. for series 02 on 26.11.2026 `termin-2-2026-11-26`. They are called «Training», location as in the series.

In the `dev` profile the seed is loaded when the backend starts. Every restart (including live reload) resets the data.

## Seasons

| No. | Season | Period | Status |
|---|---|---|---|
| 01 | 2025/26 | 01.07.2025–30.06.2026 | archive |
| 02 | 2026/27 | 01.07.2026–30.06.2027 | **current** |

## Teams (season 2026/27)

| No. | Team | swiss unihockey |
|---|---|---|
| 01 | Team 1 | linked with team ID `900001` (simulated) |
| 02 | Team 2 | not linked |
| 03 | Team 3 | not linked |

## Groups

| No. | Team | Group | Order |
|---|---|---|---|
| 01 | Team 1 | Linie 1 | 1 |
| 02 | Team 1 | Linie 2 | 2 |
| 03 | Team 1 | Goalies | – |
| 04 | Team 2 | Linie 1 | 1 |
| 05 | Team 2 | Linie 2 | 2 |
| 06 | Team 2 | Goalies | – |
| 07 | Team 3 | Linie 1 | 1 |
| 08 | Team 3 | Goalies (empty) | – |
| 09 | Team 1, season 2025/26 | Goalies (empty, archive) | – |

## People

The **key** is used for `/dev-login` and `POST /api/test/anmelden`.

| No. | Key | Name | Team 1 | Team 2 | Team 3 | Admin |
|---|---|---|---|---|---|---|
| 01 | `alex` | Alex Admin | – | – | – | ✓ |
| 02 | `petra` | Petra Keller | **Coach** | Player · Linie 1 | – | – |
| 03 | `marc` | Marc Huber | – | **Coach** | – | – |
| 04 | `ruth` | Ruth Meier | – | – | **Coach** | – |
| 05 | `anna` | Anna Muster | Player · Linie 1 | – | – | – |
| 06 | `lea` | Lea Graf | Player · Linie 1 | Player · Linie 2 | – | – |
| 07 | `mia` | Mia Frei | Player · Linie 1 | – | – | – |
| 08 | `sara` | Sara Brun | Player · Linie 2 | – | – | – |
| 09 | `nina` | Nina Roth | Player · Linie 2 | – | – | – |
| 10 | `jana` | Jana Wyss | Player · Goalies | – | – | – |
| 11 | `kim` | Kim Baumann | Player · no line | – | – | – |
| 12 | `eva` | Eva Steiner | – | Player · Linie 1 | – | – |
| 13 | `lara` | Lara Suter | – | Player · Linie 1 | – | – |
| 14 | `zoe` | Zoe Kaufmann | – | Player · Linie 2 | – | – |
| 15 | `ella` | Ella Fischer | – | Player · Goalies | – | – |
| 16 | `tina` | Tina Bucher | – | – | Player · Linie 1 | – |
| 17 | `vera` | Vera Studer | only season 2025/26: player | – | – | – |

Special cases:

- **Petra** has a dual role: coach in Team 1, player in Team 2.
- **Lea** is a player in two teams, with a different line in each.
- **Kim** is not assigned to any line.
- **Vera** exists only in the archive.

## Series

| No. | Series | Teams | Period | Exception |
|---|---|---|---|---|
| 01 | «Training» Tue 18:30–20:00, Sporthalle Nord | Team 1 + Team 2 | 18.08.2026–27.04.2027 | «Weihnachtsferien» 21.12.2026–03.01.2027 |
| 02 | «Training» Thu 19:00–20:30, Sporthalle Nord | Team 1 | 20.08.2026–29.04.2027 | – |
| 03 | «Training» Wed 18:00–19:30, Turnhalle Dorf | Team 3 | 19.08.2026–28.04.2027 | – |

## Events with fixed IDs

The series generate further events. These ones have fixed IDs and defined responses.

| No. | Event | Teams | Special |
|---|---|---|---|
| 01 | Practice Tue 17.11.2026 18:30 (series 01) | Team 1 + Team 2 | **Main example**, all response states |
| 02 | Practice Thu 19.11.2026 19:00 (series 02) | Team 1 | Only Anna has responded |
| 03 | Practice Tue 24.11.2026 18:30 (series 01) | Team 1 + Team 2 | **cancelled** («Halle besetzt») |
| 04 | Match day Sun 22.11.2026 09:30–13:00 | Team 1 | From swiss unihockey, 2 matches, call-up in **draft** |
| 05 | Match day Sat 28.11.2026 14:00–16:00 | Team 2 | Entered manually, 1 match, call-up **published** |
| 06 | Other event Fri 11.12.2026 19:00–22:00 «Teamessen» | Team 1 + 2 + 3 | Nobody has responded, no location |
| 07 | Practice Tue 10.11.2026 18:30 (series 01) | Team 1 + Team 2 | Past |
| 08 | Practice Tue 12.05.2026 18:30–20:00 | Team 1 (season 2025/26) | Archive, with Vera |

The start of a match day is the start time of the first match (assumption F-03).

### Event 01: Practice Tue 17.11.2026, Team 1 + Team 2

Responses:

| Person | Response | Comment |
|---|---|---|
| Anna | «dabei» | |
| Lea | «dabei» | |
| Mia | «nicht dabei» | «Krank» |
| Sara | «dabei» | |
| Nina | «offen» | |
| Jana | «dabei» | |
| Kim | «dabei» | |
| Petra | «dabei» | |
| Eva | «vielleicht» | «Komme evtl. später» |
| Lara | «dabei» | |
| Zoe | «nicht dabei» | |
| Ella | «offen» | |
| Marc | «nicht dabei» | «Ferien» |

**Expected overview** (this is the most important check of the app; UI text in German):

```
Training Di 17.11. 18:30 · Team 1 + Team 2     7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen

Team 1                                         5 dabei · 1 nicht dabei · 0 vielleicht · 1 offen
  Linie 1      Anna ✓   Lea ✓   Mia ✗
  Linie 2      Sara ✓   Nina ?
  Goalies      Jana ✓
  Ohne Linie   Kim ✓
Team 2                                         3 dabei · 1 nicht dabei · 1 vielleicht · 1 offen
  Linie 1      Petra ✓  Eva ~   Lara ✓
  Linie 2      Lea ✓    Zoe ✗
  Goalies      Ella ?

Coaches        Petra ✓ (Team 1)   Marc ✗ (Team 2)
```

- Total: 12 different players. Lea counts in both teams, once in the overall total ([ANM-32](requirements/04-responses.md)).
- Petra appears in the coach block (Team 1) and as a player in Team 2, with the same response ([ANM-30, ANM-31](requirements/04-responses.md)).
- Coaches do not count as players ([PTF-07](requirements/01-people-teams-roles.md)).
- Within a group, people are sorted by last name.

### Event 04: Match day Sun 22.11.2026, Team 1

| | |
|---|---|
| Source | swiss unihockey (simulated) |
| Venue | Sporthalle Seefeld, Musterdorf, with Google Maps link |
| Meeting point | 08:00, Parkplatz Sporthalle Nord |
| Notes | «Weisse Trikots mitnehmen» |
| Match 01 | 09:30 · Team 1 – UHC Musterdorf (home), SU match ID `5000001` |
| Match 02 | 11:45 · Unihockey Beispielstadt – Team 1 (away), SU match ID `5000002` |

Responses: Anna «dabei», Lea «dabei», Mia «vielleicht» («Muss evtl. arbeiten»), Sara «dabei», Nina «offen», Jana «dabei», Kim «nicht dabei», Petra (coach) «dabei».

Call-up in draft: Anna, Lea, Mia, Sara, Jana. Players see «noch kein Aufgebot».

**Expected call-up screen** (`/termine/40000000-0000-4000-8000-000000000004/aufgebot` as `petra`):

```
Entwurf · 5 aufgeboten
Linie 1      ☑ Mia ~ «Muss evtl. arbeiten»   ☑ Lea ✓   ☑ Anna ✓
Linie 2      ☑ Sara ✓   ☐ Nina ?
Goalies      ☑ Jana ✓
Ohne Linie   ☐ Kim ✗
```

Petra (coach) is not in the selection (AUF-28).

### Event 05: Match day Sat 28.11.2026, Team 2

| | |
|---|---|
| Source | entered manually |
| Venue | Turnhalle Weid, Beispielstadt, with Google Maps link |
| Meeting point | 12:30, Sporthalle Nord |
| Match 03 | 14:00 · Team 2 – UH Testingen (home) |

Responses: Petra «dabei», Lea «dabei», Eva «dabei», Lara «dabei», Zoe «nicht dabei», Ella «dabei», Marc (coach) «dabei».

Call-up published on Sat 15.11.2026 21:00: Petra, Eva, Lara, Ella. Lea is «nicht aufgeboten».

## Notifications

Texts as in production. `GET /api/test/benachrichtigungen` returns them too; newly created ones have IDs that do not start with `90000000`.

| No. | Kind | Event | Recipients | Created | Read |
|---|---|---|---|---|---|
| 01–12 | `TERMIN_ABGESAGT` «Training Di 24.11. ist abgesagt: Halle besetzt» | 03 | Marc, Anna, Lea, Mia, Sara, Nina, Jana, Kim, Eva, Lara, Zoe, Ella (in this order; all members of Team 1 and 2 except Petra, who cancelled) | Sat 14.11.2026 10:00 | only Mia (no. 04) |
| 13–18 | `AUFGEBOT_VEROEFFENTLICHT` «Das Aufgebot für Spieltag Sa 28.11. ist da: Du bist (nicht) aufgeboten.» | 05 | Petra, Lea (not called up), Eva, Lara, Zoe (not called up), Ella | Sun 15.11.2026 21:00 | only Petra (no. 13) |

Unread after reset: Lea, Eva, Lara, Zoe, Ella 2 each; Marc, Anna, Sara, Nina, Jana, Kim 1 each; Petra, Mia, Alex, Ruth, Tina 0.

## Simulated swiss unihockey data

In the `dev`/`test` profiles the app does not talk to swiss unihockey but to a simulator (F-07). After reset it delivers exactly match 01 and match 02 of event 04 for team ID `900001`, and no matches for all other team IDs. A sync directly after the reset therefore changes nothing («2 unverändert»). With `PUT /api/test/swiss-unihockey/spiele` other data or an outage (`"nichtErreichbar": true`) can be set; `POST /api/test/reset` resets the simulator.

| SU match ID | Start time | Home team | Away team | Hall | Location |
|---|---|---|---|---|---|
| `5000001` | Sun 22.11.2026 09:30 | Team 1 | UHC Musterdorf | Sporthalle Seefeld | Musterdorf |
| `5000002` | Sun 22.11.2026 11:45 | Unihockey Beispielstadt | Team 1 | Sporthalle Seefeld | Musterdorf |

Both with Google Maps link `https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf`, without result. The simulator calls the own team «Team 1» as in the app; at swiss unihockey it has a different name, and the sync recognizes it because it appears in every match (assumption F-07).

Match days newly created by the sync are called «Spieltag», have the venue from swiss unihockey, start with the first start time and end 75 minutes after the last one.

## Scenarios for tests

Every scenario starts with `POST /api/test/reset`.

| No. | Scenario | Steps | Expected | Reference |
|---|---|---|---|---|
| S1 | Overview of shared practice | As `petra` open event 01 | Overview exactly as above | ANM-20–32 |
| S2 | Response with one tap | As `nina` tap «dabei» for event 01 in the event list | Response saved, Team 1 shows 6 «dabei», 0 «offen» | ANM-08 |
| S3 | Maybe without comment | As `nina` choose «vielleicht» for event 01, leave the comment empty | Saving not possible, error message at the field | ANM-04 |
| S4 | Comments only for coaches | As `anna` open event 01 | No comment visible. As `petra`: «Krank» visible for Mia | ANM-07 |
| S5 | Late cancellation | Clock to 16.11.2026 19:00. As `anna` change to «nicht dabei» for event 01 | Response with `kurzfristig: true`, Petra sees «kurzfristig abgemeldet» for Anna (F-04). Petra (coach Team 1) receives «Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.», Marc does not (assumption O-17) | ANM-10, BEN-02 |
| S6 | Reminder | Clock to 16.11.2026 06:31 | Nina and Ella receive a reminder for event 01, nobody else. Set the clock again (e.g. 07:00): no further one | BEN-01, BEN-13 |
| S7 | Respond on behalf | As `petra` enter «nicht dabei» for Nina for event 01 | Saved, «eingetragen von Petra Keller» is visible for Nina | ANM-11, ANM-12 |
| S8 | No responding on behalf for other teams | As `marc` try to respond for Anna for event 01 | Action not offered, API answers 403 | RR-06 |
| S9 | Publish call-up | As `petra` event 04 → call-up → publish | Anna sees «aufgeboten», Kim «nicht aufgeboten». All 7 players of Team 1 are notified, Kim with «nicht aufgeboten» | AUF-23, AUF-24 |
| S10 | Called-up player withdraws | As `eva` change to «nicht dabei» for event 05 | Marc receives «Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet.» | AUF-26, BEN-04 |
| S32 | Open a notification | As `anna` open `/benachrichtigungen`, tap the entry for event 03 | Event 03 opens, the counter in the navigation disappears, the entry is read | BEN-12 |
| S11 | Call-up only for coaches | As `anna` open `/termine/40000000-0000-4000-8000-000000000004/aufgebot` | Notice «Keine Berechtigung», API 403 | RR-03 |
| S12 | Rescheduling by swiss unihockey | Simulator: match 02 to 12:15. As `alex` at `/verwaltung/teams/<Team 1>` «Jetzt abgleichen» | «Abgleich fertig: 0 neu, 1 geändert, 0 nicht mehr bei swiss unihockey, 1 unverändert.» Match 02 shows 12:15, meeting point, notes, responses and call-up unchanged. All 8 members of Team 1 receive «Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.» | SUH-05, SUH-06, BEN-06 |
| S13 | Cancelled event | As `anna` open event 03 | Recognizable as cancelled, with reason. Responding not possible | TER-06 |
| S14 | Change line | As `petra` assign Kim to Linie 2 in Team 1 | Kim appears in event 01 under Linie 2 of Team 1, «Ohne Linie» is empty and not shown | LIN-07, LIN-06 |
| S15 | One line per team | As `petra` additionally assign Anna to Linie 2 in Team 1 | Anna moves to Linie 2 and is no longer in Linie 1 | LIN-02 |
| S24 | Delete line | As `petra` at `/teams/<Team 1>/linien` confirm «Linie 2 löschen» | Sara and Nina are listed under «Ohne Linie», Linie 1 and Goalies unchanged | LIN-06, LIN-07 |
| S25 | Goalies stay | As `petra` try to delete the goalies of Team 1 (`DELETE /api/teams/<Team 1>/gruppen/<Gruppe 03>`) | UI does not offer it, API 409 with rule LIN-05 | LIN-05 |
| S26 | Lines read-only | As `anna` open `/teams/<Team 1>/linien`; as `marc` the same | Anna sees the assignment without controls. Marc: «Keine Berechtigung», API 403 | LIN-07, RR-07 |
| S16 | Archive | As `alex` at `/verwaltung/saisons` click «Teams ansehen» for 2025/26, open Team 1 | Notice «Archiv: nur lesend», Vera visible as player, no editing possible (API 409). Vera sees event 08 under `/termine?ansicht=vergangen` | SAI-02 |
| S17 | Login in test mode | Open `/profil` without login, choose «Petra Keller» on `/dev-login` | Redirect to `/dev-login?ziel=%2Fprofil`, afterwards back to `/profil` | F-00 |
| S18 | Tiles by role | As `anna` and as `alex` open `/` | Both see the tile «Anwesenheit». Only `alex` has «Verwaltung» in the navigation | NF-12, RR-05 |
| S20 | Dual role | As `petra` open `/teams` | Team 1 «Coach», Team 2 «Spielerin». In Team 2 Petra is listed among the players, in Team 1 among the coaches | PTF-04, PTF-07, PTF-12 |
| S21 | New season | As `alex` at `/verwaltung/saisons` create 2027/28 with carry-over from 2026/27, make it current | Teams and memberships taken over, 2026/27 becomes archive | SAI-04, SAI-06 |
| S22 | One team role per team | As `alex` try to add Petra to Team 1 again | UI: «(schon im Team)», not selectable. API: 409 with rule PTF-04 | PTF-04 |
| S23 | No current season | `DELETE /api/test/aktuelle-saison`, as `anna` open `/teams` | Redirect to `/keine-saison?ziel=%2Fteams` with notice «Noch keine aktuelle Saison», `GET /api/ich` answers 409 with rule SAI-03 | SAI-03 |
| S27 | Event for two teams | As `petra` at `/termine/neu` create an event for Team 1 + Team 2 | Appears for `anna` and `eva`. As `marc` only for Team 1: API 403 | TER-01, TER-11 |
| S28 | Exclude holidays | As `petra` add exception 30.11.–06.12.2026 to series 01 | Practice Tue 01.12. disappears (no responses). With a response: cancelled with the reason of the exception | TER-07 |
| S29 | Reschedule series event | As `petra` move event 02 to Fri 20.11. 19:00 | «verschoben», series 02 unchanged, Thu 26.11. stays | TER-06 |
| S30 | Create match day | As `petra` at `/termine/neu` kind «Spieltag», Team 1, venue with Google Maps link, meeting point, notes, two matches | Event shows matches by start time, meeting point and «In Google Maps öffnen» (new tab) | AUF-10–AUF-16 |
| S31 | Enter result | As `petra` event 04 → «Resultat für Team 1 – UHC Musterdorf erfassen» → 5:3 | Match 01 shows «Resultat 5:3», also for `anna`. Changing the opponent of a match from swiss unihockey: API 409 rule SUH-06 | AUF-15 |
| S33 | Match day from swiss unihockey | Simulator for `900001`: match 01, 02 and two matches on Sun 06.12.2026 10:00 and 12:30 in «Sporthalle Nord», Beromünster. Trigger sync | One new match day Sun 06.12. with both matches, venue with Google Maps link | SUH-04, SUH-07 |
| S34 | Match missing at swiss unihockey | Simulator for `900001` only match 01. Trigger sync | Match 02 stays, marked «swiss unihockey liefert dieses Spiel nicht mehr.», coach can delete it | SUH-06 |
| S35 | swiss unihockey not reachable | Simulator for `900001` with `"nichtErreichbar": true`. Trigger sync | Message «swiss unihockey ist im Moment nicht erreichbar. …», API 502, nothing changed | SUH-09 |
| S19 | Theme | As `anna` at `/profil` choose «GitHub Dark», reload | Stays dark. «System» follows the system setting: light «Hell», dark «GitHub Dark» | NF-06, NF-08 |
| S36 | Theme Düne | As `anna` at `/profil` choose «Düne», go to `/`, reload | `data-theme` on the document is `duene`, the colors also apply when the system is dark, and stay after reload. No contrast finding | NF-08 |
