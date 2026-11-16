# Navigation and screens

Map of the UI for humans and agents. The test agent uses it to jump straight to the right place by URL. Whoever changes a route or a fixed test ID updates this file in the same PR.

Status: target state for the core. The column «Feature» states with which feature from the [backlog](../pipeline/backlog.md) a screen arrives. Built are F-00 (foundation: `/anmelden`, `/dev-login`, `/`, `/termine` as empty state, `/profil`), F-01 (`/teams`, `/teams/:id`, `/verwaltung` with people, teams and seasons), F-02 (`/teams/:id/linien`, `/teams/:id` by line), F-03 (`/termine` with filters, `/termine/neu`, `/termine/:id` with details, `/termine/:id/bearbeiten`, `/serien/:id`, `/serien/:id/bearbeiten`), F-04 (responses in `/`, `/termine` and `/termine/:id`, overview by team and line), F-05 (match day in the form with matches, meeting point and Google Maps link; matches, results and call-up status in `/termine/:id`; `/termine/:id/aufgebot`) and F-06 (`/benachrichtigungen`, bell with counter in the navigation).

## Navigation

- **Mobile:** bottom navigation with «Start», «Termine», «Teams», «Profil». Admins also see «Verwaltung».
- **Desktop (from 1024px):** the same entries as a side navigation on the left.
- **Notifications (F-06):** its own link in the navigation, outside the list of main entries. On mobile a round bell at the top right in a fixed header, the content scrolls underneath (name «Benachrichtigungen», label for screen readers only); from 1024px an entry «Benachrichtigungen» above «Start». With unread notifications it shows the number (`benachrichtigungen-zaehler`), and the name then reads e.g. «Benachrichtigungen 2 ungelesen». The counter is reloaded on every page change.
- The active entry has `aria-current="page"`.
- The navigation is a `nav` with `aria-label="Hauptnavigation"`.
- Order: «Start», «Termine», «Teams», «Profil», («Verwaltung»). Page title in the browser: «<Seite> · STV Bero».

```mermaid
flowchart LR
    Login["/anmelden"] --> Start["/ Start"]
    DevLogin["/dev-login (dev/test only)"] --> Start
    Start --> Events["/termine"]
    Start -->|Attendance tile| Events
    Start -->|next event| Detail
    Events --> Detail["/termine/:id"]
    Detail --> CallUp["/termine/:id/aufgebot (coach, match day)"]
    Detail --> EditEvent["/termine/:id/bearbeiten (coach)"]
    Events --> NewEvent["/termine/neu (coach)"]
    EditEvent --> Series["/serien/:id (coach)"]
    Series --> EditSeries["/serien/:id/bearbeiten (coach)"]
    Start --> Teams["/teams"]
    Teams --> Team["/teams/:id"]
    Team --> Lines["/teams/:id/linien (coach)"]
    Start --> Profile["/profil"]
    Start --> Notifications["/benachrichtigungen"]
    Notifications -->|click| Detail
    Start --> Admin["/verwaltung (admin)"]
    Admin --> People["/verwaltung/personen"]
    People --> NewPerson["/verwaltung/personen/neu"]
    People --> PersonDetail["/verwaltung/personen/:id"]
    Admin --> ATeams["/verwaltung/teams"]
    ATeams --> ATeam["/verwaltung/teams/:id"]
    Admin --> Seasons["/verwaltung/saisons"]
    Seasons -->|view teams| ATeams
```

## Routes

| Route | Screen | Who | Content | Feature |
|---|---|---|---|---|
| `/anmelden` | «Anmelden» (login) | everyone | Login (method open, O-01). In test mode link «Zur Anmeldung im Testmodus». `?ziel=` is passed on | F-00 |
| `/dev-login` | «Testmodus-Anmeldung» (test mode login) | only `dev`/`test` (development build) | List of seed persons (`GET /api/test/personen`); one click logs in and leads to `?ziel=` or `/` | F-00 |
| `/` | «Start» | logged in | Greeting, own next event with response buttons, tiles depending on role | F-00, F-04 |
| `/termine` | «Termine» (events) | logged in | Upcoming events of all own teams by month, switch «Kommend»/«Vergangen» (`nav` «Ansicht», `?ansicht=vergangen`), select «Team» (`?team=<id>`), coaches and admins: link «Termin erfassen». From F-04 own response can be changed directly | F-03, F-04 |
| `/termine/:id` | «Termin» (event) | members of the teams involved, admin | Details (date, time, teams, location, meeting point, response deadline, notes, series), status «Abgesagt: <Grund>» or «verschoben», coaches: «Termin bearbeiten», «Serie ansehen». From F-04: own response, overview by team and line, coach block. For a match day from F-05: matches, own call-up status | F-03, F-04, F-05 |
| `/termine/neu` | «Termin erfassen» (create event) | coach, admin | Choice «Einzelner Termin» / «Serie (jede Woche)» (`?art=serie`), type (Training, Spieltag, Anlass), title, date or weekday and period, start, end, teams (checkboxes), location, Google Maps link, notes, response deadline. For «Spieltag»: location is called «Spielort», group «Treffpunkt» (time, place), group «Spiele» with one group «Spiel n» each (opponent, start time, «Heim oder auswärts», «Spiel n entfernen») and «Spiel hinzufügen» | F-03, F-05 |
| `/termine/:id/bearbeiten` | «Termin bearbeiten» (edit event) | coach of a team involved, admin | Change details, postpone (other date or other time), «Termin absagen» (dialog with reason). For series events notice «gilt nur für diesen Termin». Match day: matches as when creating; matches from swiss unihockey read-only; if swiss unihockey no longer delivers a match, it shows «swiss unihockey liefert dieses Spiel nicht mehr.» and the button «Spiel <n> entfernen» (#44) | F-03, F-05 |
| `/termine/:id/aufgebot` | «Aufgebot» (call-up) | coach of the team, admin (otherwise «Keine Berechtigung»; not a match day: «Nur ein Spieltag hat ein Aufgebot.») | Status (`aufgebot-status`, `aufgebot-anzahl`), selection grouped by lines (one group each with the group name, checkbox with the player's name, response status and comment next to it), saves immediately (feedback «<Name> ist aufgeboten.»), «Veröffentlichen» with confirmation. Coaches are not in the selection (AUF-28). Cancelled or past: read-only | F-05 |
| `/serien/:id` | «Serie» (series) | members of the teams of the series (coaches and admins with editing) | Rule, period, teams (all teams of the series, including those one is not in), response deadline, exceptions (list and form «Ausnahme hinzufügen»), link «Serie bearbeiten» | F-03 |
| `/serien/:id/bearbeiten` | «Serie bearbeiten» (edit series) | coach of a team of the series, admin | Like «Termin erfassen» as a series; applies to future events (TER-08) | F-03 |
| `/teams` | «Meine Teams» (my teams) | logged in | Own teams of the current season with own team role (PTF-12), link to `/teams/:id` | F-01 |
| `/teams/:id` | «Team» | members, admin | Coaches and players separate (PTF-07), players by line (lists with the group name, empty groups are missing). Coaches and admins see the link «Linien einteilen». `?saison=<id>` shows another season (archive with notice). Without team membership «Keine Berechtigung» | F-01, F-02 |
| `/teams/:id/linien` | «Linien» (lines) | coach, admin (other members read-only) | Add, rename, sort («<Linie> nach oben/unten»), delete (dialog) lines, assign players by drag and drop or select field «Linie von <Name>»; saves immediately. Without team membership «Keine Berechtigung». Current season | F-02 |
| `/benachrichtigungen` | «Benachrichtigungen» (notifications) | logged in | Own notifications, newest first (list «Benachrichtigungen»): text, time, unread ones with «Neu». A click marks as read and opens `/termine/:id` (BEN-12). Button «Alle als gelesen markieren» (only with unread ones, feedback «Alle sind als gelesen markiert.»). Empty: «Keine Benachrichtigungen.» | F-06 |
| `/profil` | «Profil» (profile) | logged in | Name, theme («Hell», «Düne», «GitHub Dark», «System»; NF-08), log out | F-00 |
| `/verwaltung` | «Verwaltung» (administration) | admin | Entry page: links «Personen», «Teams verwalten», «Saisons». Others see «Keine Berechtigung» | F-00, F-01 |
| `/keine-saison` | «Keine aktuelle Saison» (no current season) | logged in | Notice when `GET /api/ich` responds with 409 (no season set as current, SAI-03). Without navigation, buttons «Nochmals versuchen» (back to `?ziel=`) and «Abmelden». In this state every route redirects here with `?ziel=`. Opened directly: without login to the login page, with a current season straight on to the target | Follow-up fix #13 |
| `/**` | «Nicht gefunden» (not found) | logged in | «Diese Seite gibt es nicht.» with link «Zur Startseite» | F-00 |
| `/verwaltung/personen` | «Personen» (people) | admin | List by last name, search `?suche=`, link «Person erfassen» | F-01 |
| `/verwaltung/personen/neu` | «Person erfassen» (create person) | admin | First name, last name, e-mail, admin. After saving on to `/verwaltung/personen/:id` | F-01 |
| `/verwaltung/personen/:id` | «Person bearbeiten» (edit person) | admin | Change details, team memberships of all seasons | F-01 |
| `/verwaltung/teams` | «Teams verwalten» (manage teams) | admin | Select «Saison» (`?saison=<id>`), teams of the season, «Team anlegen». Archive read-only. Linked teams with chip «swiss unihockey» (`su-verknuepft-<teamId>`) | F-01, F-07 |
| `/verwaltung/teams/:id` | «Team verwalten» (manage team) | admin | Rename, section «swiss unihockey» (field «Team-ID bei swiss unihockey», «Verknüpfen», «Verknüpfung lösen», «Jetzt abgleichen» with result as `role="status"`, failure as `role="alert"`; not in the archive), coaches and players with select «Funktion von <Name>» and «<Name> entfernen», «Person aufnehmen». `?saison=<id>`; archive read-only | F-01, F-07 |
| `/verwaltung/saisons` | «Saisons» (seasons) | admin | Seasons with status (current, archive, planned), «Teams ansehen», «Zur aktuellen Saison machen», form «Neue Saison» with carry-over | F-01 |

Without permission a screen shows the text «Keine Berechtigung» (`role="alert"`) instead of an empty screen. Without login every route redirects to `/anmelden` (or `/dev-login` in test mode) with `?ziel=<original route>` and returns there after login. The frontend checks whether someone is logged in via `GET /api/sitzung` (always 200), so that no errors appear in the console without login.

## Dialogs

| Dialog | Opened from | Name (heading) |
|---|---|---|
| Maybe with comment | Response «vielleicht» (field «Kommentar», required; empty: message at the field) | «Vielleicht – bitte kurz begründen» |
| Reason for declining | Response «nicht dabei», after it is already saved (field «Grund (freiwillig)», buttons «Grund speichern», «Überspringen») | «Nicht dabei» |
| Respond on behalf | Coach in the overview, button next to a person | «Antwort für <Name> eintragen» (select «Antwort» with radio buttons, field «Kommentar (bei «vielleicht» Pflicht)») |
| Cancel event | Edit event, «Termin absagen» | «Termin absagen» (field «Grund», required) |
| Delete line | Lines, «<Linie> löschen» | «<Linie> löschen?» |
| Publish call-up | Call-up, «Veröffentlichen» (buttons «Veröffentlichen», «Abbrechen») | «Aufgebot veröffentlichen?» |
| Enter result | Event (match day), button «Resultat für <Heim> – <Gast> erfassen» (coach, admin) | «Resultat: <Heim> – <Gast>» (field «Resultat (Heim:Gast, z.B. 5:3)», empty removes it, button «Resultat speichern») |

## Test IDs

Fixed test IDs that tests rely on. Scheme and rules: [testability.md](../testability.md#4-test-ids-where-semantics-are-not-enough).

### Global

| Test ID | Element |
|---|---|
| `hauptnavigation` | Navigation (mobile and desktop) |
| `theme-umschalter` | Switch «Darstellung» in the profile: choice (radio) «Hell», «Düne», «GitHub Dark», «System» |
| `testmodus-hinweis` | Banner «Testmodus» (dev/test only) |
| `kachel-<id>` | Tile on the start screen (link), e.g. `kachel-anwesenheit` |
| `dev-login-person-<schluessel>` | Entry of a test person on `/dev-login`, e.g. `dev-login-person-petra` |
| `naechster-termin` | Card «Nächster Termin» on the start screen: next planned event of the own teams (cancelled ones skipped) with link to the event and `meine-antwort`. Without an event: «Keine kommenden Termine» |
| `benachrichtigungen-zaehler` | Number of unread notifications in the navigation (missing when everything is read) |
| `benachrichtigung-<id>` | Entry in `/benachrichtigungen` (`li` with `data-gelesen="true\|false"` and `data-art`, e.g. `ERINNERUNG`) |
| `keine-saison-hinweis` | Notice on `/keine-saison` |

### Teams and administration (F-01)

| Test ID | Element |
|---|---|
| `team-karte-<teamId>` | Entry in «Meine Teams» (`/teams`) |
| `team-coaches` | Section «Coaches» (`/teams/:id`, `/verwaltung/teams/:id`) |
| `team-spielerinnen` | Section «Spielerinnen» (`/teams/:id`, `/verwaltung/teams/:id`) |
| `team-person-<personId>` | Person in `/teams/:id` |
| `mitglied-<personId>` | Person in `/verwaltung/teams/:id` (`li` with the name as `aria-label`) |
| `archiv-hinweis` | Notice «Saison … im Archiv: nur lesend.» |
| `person-<personId>` | Entry in `/verwaltung/personen` |
| `team-<teamId>` | Entry in `/verwaltung/teams` |
| `saison-<saisonId>` | Entry in `/verwaltung/saisons` (`li` with the season name as `aria-label`) |
| `saison-status` | Status of a season: «aktuell», «Archiv», «geplant» |
| `swiss-unihockey` | Section «swiss unihockey» in `/verwaltung/teams/:id` (`section`, name «swiss unihockey») |
| `su-status` | «Verknüpft mit Team-ID <ID>.» or «Nicht verknüpft.» |
| `su-verknuepft-<teamId>` | Chip «swiss unihockey» next to a linked team in `/verwaltung/teams` |

Success messages have `role="status"`, errors `role="alert"`. In «Person aufnehmen», people already in the team are marked with «(schon im Team)» and cannot be selected (PTF-04).

### Events

| Test ID | Element |
|---|---|
| `termin-karte-<terminId>` | Entry in the event list (`li` with `aria-label` «<Titel> <Datum> <Beginn>», e.g. «Training Di 17.11. 18:30», and `data-status`) |
| `meine-antwort` | Response buttons of the logged-in person (in card, detail and start), `role="group"` with the name «Meine Antwort» or «Meine Antwort für <Titel> <Datum> <Beginn>» and `data-antwort`. Feedback «Gespeichert: dabei.» (`role="status"`). Cancelled: buttons `disabled`, explanation via `aria-describedby` |
| `antwort-dabei`, `antwort-nicht-dabei`, `antwort-vielleicht` | The three response buttons inside `meine-antwort` (names «Dabei», «Nicht dabei», «Vielleicht», selected: `aria-pressed="true"`) |
| `termin-zaehler` | Total counter in the card of the event list (not for cancelled events) |
| `termin-status` | Notice «abgesagt» or «verschoben» (as a chip in the card, as a notice with reason in the detail) |
| `antwortfrist` | Response deadline in the detail of an event, e.g. «Mo 16.11. 18:30 (24 Stunden vor Beginn)» |
| `ausnahmen` | Section «Ausnahmen» on `/serien/:id` |
| `uebersicht` | Entire overview of an event |
| `uebersicht-total` | Total counter |
| `uebersicht-team-<teamId>` | Section of a team |
| `uebersicht-team-<teamId>-zaehler` | Counter of a team |
| `uebersicht-gruppe-<gruppeId>` | Line or goalies within a team |
| `uebersicht-ohne-linie-<teamId>` | Group «Ohne Linie» of a team (only if not empty) |
| `uebersicht-person-<personId>` | Entry of a person (`li`), with `data-antwort`, status as text, comment «…» only for those permitted, «eingetragen von …», «kurzfristig abgemeldet». Coaches (or admins) see the button «Antwort für <Name> eintragen». The same person can appear in several teams and in the coach block: narrow down via the section |
| `coach-block` | Block of the coaches |

Counter elements carry `data-zaehler="dabei|nicht-dabei|vielleicht|offen"`.

### Match day and call-up

| Test ID | Element |
|---|---|
| `spiele` | List of the matches of a match day (`ol`, by start time). Without matches: text «Für diesen Spieltag sind noch keine Spiele erfasst.» |
| `spiel-<spielId>` | One match (`li` with `aria-label` «<Anspielzeit> <Heim> – <Gast>», e.g. «09:30 Team 1 – UHC Musterdorf»), plus «Heim»/«Auswärts» and «Resultat 5:3» |
| `treffpunkt` | Meeting point (time and place), e.g. «08:00 · Parkplatz Sporthalle Nord» |
| `spielort-karte` | Link «In Google Maps öffnen» (`target="_blank"`) |
| `mein-aufgebot` | Own status in the event, only for players of the team: «aufgeboten», «nicht aufgeboten», «noch kein Aufgebot». Coaches and admins see the status and the link «Aufgebot bearbeiten» instead |
| `aufgebot-auswahl` | Selection list in the call-up screen |
| `aufgebot-person-<personId>` | Entry in the selection (`li` with `data-antwort` and `data-aufgeboten`, checkbox with the name, response status as text next to it) |
| `aufgebot-veroeffentlichen` | Button «Veröffentlichen» (only in draft; without selection `disabled` with explanation) |
| `aufgebot-status` | «Entwurf», «Entwurf: noch niemand ausgewählt» or «veröffentlicht am Mo 16.11. 06:00» |
| `aufgebot-anzahl` | Number of called-up players, e.g. «5 aufgeboten» |

### Lines

| Test ID | Element |
|---|---|
| `linien-verwaltung` | Lines screen of a team |
| `gruppe-<gruppeId>` | A line or goalies (`section`, named after the heading) |
| `gruppe-ohne-linie` | Group «Ohne Linie» in the lines screen (always visible, when empty with the text «Alle Spielerinnen sind eingeteilt.») |
| `spielerin-<personId>` | Player in the lines screen (`li` with the name as `aria-label`, draggable for coaches) |
| `linie-hinzufuegen` | Button «Linie hinzufügen» |
| `zuweisung-<personId>` | Select field «Linie von <Name>» of a player (alternative to drag and drop). Values: group IDs, empty = «Ohne Linie» |

## Display rules that affect tests

- Empty groups («Ohne Linie», empty line, goalies without a player) are not shown in the overview of an event. In the lines screen they are shown.
- Order in the overview: teams by name, within them lines by order, then goalies, then «Ohne Linie». People by last name, then first name.
- Dates in the format «Di 17.11.», times «18:30». Full date with year only if it is not the current year.
