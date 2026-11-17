# Open points

Until they are clarified, sensible assumptions apply and are named with the result.

## Platform

| No. | Question | Remark |
|---|---|---|
| O-01 | **Login:** How do members log in? | E-mail and password, magic link or Keycloak/OIDC. |
| O-02 | **Hosting:** Where does the platform run in production, where does the preview for verification run? | |
| O-03 | **Data protection:** Which data is stored, who sees what (nDSG)? | For now the app is meant for adults. If minors are added, reassess. |
| O-04 | **Multi-tenancy:** Are several clubs needed in one installation? | Several teams within the club are part of the core. |
| O-05 | ~~**Notifications:** E-mail, push via the PWA or both?~~ | Decided: both (E-71, F-19). |
| O-06 | **Size:** Roughly how many teams and members? | |
| O-07 | ~~**Backend language:** Java or Kotlin?~~ | Decided: Java 21 (E-57). |
| O-08 | ~~**Migrations:** Flyway or Liquibase?~~ | Decided: Flyway (E-57). |

## Core

| No. | Question | Remark |
|---|---|---|
| O-10 | Which team roles are needed besides player and coach? | E.g. assistant coach, team manager. See [PTF-06](requirements/01-people-teams-roles.md). |
| O-11 | By default, how many hours before the event is the response deadline? | See [TER-09](requirements/03-events.md). |
| O-12 | May responses still be changed after the deadline? | Assumption: yes, with a notice to the coach ([ANM-09](requirements/04-responses.md)). |
| O-13 | Do players see the status of the others? | Assumption: yes, but no comments ([ANM-25](requirements/04-responses.md)). |
| O-14 | Does a match day always belong to exactly one team? | Assumption: yes ([AUF-03](requirements/05-match-days-call-up.md)). |
| O-15 | Does the user interface say «Spielerin» or «Spieler/in»? | «Spielerinnen» was used while working out the requirements, «Spieler/innen» in older documents. |
| O-16 | Colours and symbols for the four response statuses | Proposal in [design-principles.md](design-principles.md#not-yet-defined). Implemented with F-04 as an assumption: attending `primary` with a check mark, declined `coral` with a cross, maybe `mustard` with a question mark, pending `muted` with a dashed circle, always also as text. |
| O-17 | Who is notified of a late cancellation if an event has several teams? | Assumption: only the coaches of the teams in which the person is a player ([BEN-02](requirements/08-notifications.md)). |
| O-18 | When is the reminder sent? | Assumption: 12 hours before the response deadline ([states.md](states.md#event)). |
| O-19 | What happens to series events that fall into a new series exception? | Assumption: removed if they have no responses, cancelled if they have responses ([states.md](states.md#series)). |
| O-34 | Does a newly created person automatically get an account? | Assumption F-01: no. The account is created with the login method (O-01). New people cannot log in in test mode. |
| O-35 | May old seasons be corrected afterwards? | Assumption F-01: no, seasons before the current one are read-only (SAI-02). A planned season after the current one can be changed. |
| O-36 | How is the first current season created in a fresh installation? | Without a current season the app shows everyone, admins included, only the notice `/keine-saison` (follow-up fix #13). The season administration is then not reachable. Assumption: the first season is created during setup (seed or migration at production start). Alternative: admins see a link to `/verwaltung/saisons` on the notice. |
| O-37 | May two lines of a team have the same name? | Assumption F-02: no, names are unique per team (case-insensitive), so that the selection field is unambiguous (409, LIN-09). |
| O-38 | Can the goalies be renamed? | Assumption F-02: no, «Goalies» is fixed (409, LIN-05). |
| O-39 | Who may create an event for several teams? | Assumption F-03: a coach in at least one of the teams (or an admin). This way Petra (coach Team 1) can create the joint practice with Team 2, as the acceptance criterion requires. The contract previously said «coach in every team». The form offers the user's own teams. |
| O-40 | Are past dates also created when a series is set up? | Assumption F-03: no, only events that have not started yet. The seed, however, contains all events since the start of the series. |
| O-42 | May admins without a team respond for themselves? | Assumption F-04: no, only someone in a participating team responds for themselves (403). Admins may respond on behalf of any member of the participating teams, coaches included (RR-04). |
| O-43 | How long does a response count as «late»? | Assumption F-04: the mark is set when changing away from «dabei» after the deadline, stays when changing between «nicht dabei» and «vielleicht», and goes away with «dabei». A first «nicht dabei» after the deadline (previously «offen») is not late. |
| O-44 | Who sees who entered a response on behalf? | Assumption F-04: everyone who sees the event. It is not a comment (ANM-07 does not apply), but makes the response traceable (ANM-12). |
| O-45 | Do players see that a draft of the call-up exists? | Assumption F-05: no. Before publishing, the API shows people without coach permission `callUpStatus: NONE` and `myCallUp: NO_CALL_UP_YET` (AUF-23). |
| O-46 | Can an empty call-up be published, and can a publication be withdrawn? | Assumption F-05: no and no. Only a draft with at least one player is published (409). After that it stays published; players see changes immediately and those affected are notified (AUF-27, F-06). |
| O-47 | When can the result of a match be entered? | Assumption F-05: at any time, also after the match day, just not for a cancelled match day. Format home:away (e.g. 5:3). For matches from swiss unihockey only the result can be changed; the sync (F-07) can overwrite it. |
| O-48 | What happens to the matches when a match day is moved to another day? | Assumption F-05: matches entered by hand move along (same time on the new day). Matches must be on the day of the match day (400). A match day with matches from swiss unihockey can only be moved within the same day (422 for the date, SUH-05), because date and time of these matches come from swiss unihockey. The start of the match day remains a field of its own. |
| O-41 | Does changing the time of a series count as moving (notice, notification)? | Assumption F-03: no, only moving a single event sets «verschoben» (moved) and notifies (F-06, TER-12). |
| O-49 | What counts as a called-up player «cancelling» (BEN-04)? | Assumption F-06: after publishing, a change from «dabei» or «offen» to «nicht dabei» or «vielleicht», and from «vielleicht» to «nicht dabei». If it is also a late cancellation, the coaches receive only one notification. |
| O-50 | Is the person who triggered something notified themselves? | Assumption F-06: no. Whoever cancels, moves, publishes or responds on behalf receives no notification about it. All other recipients remain. |
| O-51 | Where is the number of unread notifications shown? | Assumption F-06: in the main navigation as an entry of its own «Benachrichtigungen», outside the list of main entries. On mobile as a round bell at the top right in a fixed header under which the content scrolls, so that the bell never covers controls (the bottom navigation stays readable with five entries, follow-up fix #15/#23); from 1024px as an entry above «Start». No counter without unread notifications. |
| O-53 | What does «System» show, and does it stay selectable? | Cyrille kept «Hell», «Düne» and «GitHub Dark» (E-67). Assumption: «System» stays as the fourth entry and as the default (NF-06) and shows «Hell» in light mode and «GitHub Dark» in dark mode. Open: should «Düne» apply in light mode instead, because «Hell» is green? Should «System» be dropped entirely? Also an assumption: the choice is stored per browser, not per account. |
| O-52 | Is anyone notified when a series exception or a change to the series removes an event **without** responses? | Assumption F-06: no. According to TER-07 such an event is removed instead of cancelled, so there is no cancellation in the sense of BEN-07. Only events with responses are cancelled and notify all members of the participating teams. |
| O-55 | What exactly may coaches do in teams other than their own (F-12, E-69)? | Assumptions F-12, to confirm: coaches see the comments of all teams; removing a person from a team counts as deleting (admins only); cancelling an event does not; removing a match from a match day counts as editing the match day (coaches may); coaches may create and rename teams (not listed as admin-only); on «Termine» coaches and admins choose from all teams in «Team» and then see the events of that team without being a member, without a filter only their own teams (TER-13); «Meine Teams» still lists only the own teams, other teams are reached via «Verwaltung» → «Teams verwalten» → «Team ansehen». |

## Pipeline

| No. | Question | Remark |
|---|---|---|
| O-30 | Preview URL for trying things out | Depends on hosting (O-02). Until then via a cloud session on the PR branch. |
| O-31 | ~~Java and migration tool for F-00~~ | Decided: Java 21 and Flyway (E-57). |
| O-32 | Contrast of the active navigation entry | `primary` on `primary-soft` only reaches 3.97:1 in light mode (WCAG AA requires 4.5:1 for small text). Assumption F-00: icon in `primary`, text in `text`. Alternative: make `primary` slightly darker in light mode. |
| O-33 | How long does a session last? | Assumption F-00: until logout, without expiry. Depends on the login method (O-01). |
| O-54 | ~~The status page (`status/index.html`) detects agent comments and follow-up titles partly by German words: «gemergt» (merge icon), «startet»/«gestartet»/«beansprucht» (start comments), the title prefix «Nachbesserung · ».~~ | Resolved in F-11 (E-68): the status page also detects `✅ merged`, «started»/«claimed» and the prefix `Follow-up fix · `. The German words still match older comments and titles. |

## swiss unihockey

| No. | Question | Remark |
|---|---|---|
| O-20 | Does the interface need a key or a registration? | As of F-07 (research in public clients, not checked live because the cloud environment cannot reach swiss unihockey): `api-v2.swissunihockey.ch` now requires credentials (partners with key and secret). `https://wc.swissunihockey.ch` is free; it is the interface of the official swiss unihockey web components, with the same response format (paths without `/api`, e.g. `/games`). The app takes the address from `STV_SU_URL` (default according to issue #8: `https://api-v2.swissunihockey.ch/api`). **Decision needed:** set `STV_SU_URL=https://wc.swissunihockey.ch` for operation or apply for partner access. Without access the sync reports «nicht erreichbar» (not reachable) and changes nothing (SUH-09). |
| O-21 | Which fields exactly does the match list return, and how are match days in tournament format represented? | As of F-07: the match list (`/games?mode=team&team_id=…&season=…`) is a table; one match per row, match ID in the row's link. Columns: date and time (for nearby matches «heute», «gestern», «morgen»), hall and place with map link, home team, away team, result. Tournaments appear as single matches, the interface provides no grouping; the app groups by day and hall (SUH-04, assumption). The test data in `backend/src/test/resources/swissunihockey` is modelled on this, not recorded. Open: record a real response and base the test on it. |
| O-22 | Club ID of STV Beromünster and team IDs | As of F-07: not determined without access to the interface. The team ID is in the address of the team page on swissunihockey.ch; admins enter it under `/verwaltung/teams/<Team>`. The app does not need the club ID. |
| O-23 | Do the terms of use allow regular fetching? | As of F-07: no terms of use found. The free interface is meant for club websites (official web components). The app fetches once a day per linked team (plus manual syncs). Open: ask swiss unihockey. |
| O-24 | Is there a newer interface than `api-v2`? | As of F-07: yes, see O-20. `wc.swissunihockey.ch` (free) and the partner interface with credentials. The response format is the same, switching is only configuration. |
