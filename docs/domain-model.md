# Domain model

The terms of the app and how they relate. This is a domain model, not a database schema. Code, API and documents use the English terms from [glossary.md](glossary.md); the user interface uses the German terms.

## Terms

| Term | Meaning |
|---|---|
| **Account** | A login. Today each account belongs to exactly one person. Later an account can respond for several people (parents for children). |
| **Person** | A human being in the club. Each person exists exactly once, no matter how many teams they are in. |
| **Team** | A team of the club. Exists across seasons; the name applies per season, so that the archive keeps the name of the time (SAI-02). |
| **Season** | Period for which team memberships and lines apply. |
| **Team role** | What a person is in a team: at least «Spielerin» (player) or «Coach». |
| **Team membership** | Link between person, team and team role in a season. There is exactly one team role per person and team. It stores the day of joining; statistics count a player only from that day on (STA-07). |
| **Line** | Group of players within a team (Linie 1, Linie 2, …). |
| **Goalies** | A group of its own per team, next to the lines. |
| **No line** | Display group for players who are assigned neither to a line nor to the goalies («Ohne Linie»). |
| **Event** | Something people respond to. Has a type: practice, match day or other event. Belongs to one or more teams. Can be pinned («angeheftet», TER-26): then it shows on the start page of the members of its teams until it is over. |
| **Series** | Rule that creates recurring events («jeden Dienstag 18:30», every Tuesday 18:30). |
| **Match day** | Event of type match day. Because of the tournament format it includes several matches in the same hall. |
| **Match** | A single game within a match day, with opponent and start time. |
| **Response** | A person's answer for an event: attending, declined or maybe. Without a response the status is pending («offen»). |
| **Call-up** | Selection of the players who play on a match day. Created and published by the coach. |
| **Attendance check** | Optional for each event or series, off by default: after the start the coach records who was really there (PRS, E-70). |
| **Attendance** | The fact recorded by the coach per player: present («anwesend»), absent («abwesend») or excused («entschuldigt»). Separate from the response, which is the intention. |
| **Warning** | Computed note for coaches and admins when an upcoming practice or match day has a gap: too few goalies or field players attending (or called up), or a line with too few players (F-16). Not stored. |
| **Warning minimums** | Setting of a team: below these numbers a warning appears (defaults 1 goalie, 10 field players for match days, 6 for practices, 3 per line). |
| **Meeting point** | Time and place where the team meets before a match day. |
| **Driver list** | Per match day: the cars with driver and free seats, the passengers, and who still needs a seat (DRV, F-20). |
| **Car** | A driver on a match day with a number of free seats (1 to 8) and her passengers. A person drives or sits in at most one car per match day. |
| **Route** | Distance and driving time from the meeting point to the venue, calculated once per match day and again when the places change; or kilometres entered by hand. |
| **Credit** | Kilometres credited to a driver for a match day: twice the distance of the route; none for a cancelled match day. |
| **Event variant** | How a kind of event looks and behaves in a team: name, colour, call-up or responses only, sections of the event page and their order, automatic departure, defaults for new events (VAR). Every team has four standard variants and any number of its own; every event has exactly one. |
| **Special line-up** | Named line-up of a team in a season for a situation such as power play or penalty kill: 1 to 10 places, each with an optional position and at most one player (LIN-13). Exists next to the lines. |
| **Participation** | Per team of an event: expected, or invited without obligation («freiwillig»). A person is invited if all her teams of the event are (TER-15). |
| **Driving settings** | Per person: can drive, and the places of her car including herself (default yes, 5). Basis of the proposed driver list (DRV-18, DRV-19). |
| **Individual travel** | Mark per person and match day: she travels on her own and needs no seat (DRV-20). |
| **Statistics** | Read-only numbers per player of a team over a period: responses and, where recorded, attendance of the past events (STA, F-15). |
| **Absence** | Longer time a person is away (holidays, injury, other), first and last day inclusive. Declines her upcoming practices in this time with the reason as comment; the declined responses remember the absence and follow it when it changes (ABS, E-93). Private to the person. |
| **Calendar view** | Month calendar in the app with the events of chosen teams and the own absences (KAL, E-93). |
| **Calendar subscription** | Personal address of a person with a random token. Behind it is an iCalendar feed with the events of her teams, which calendar apps fetch without login. Renewing replaces the token (CAL, F-18). |
| **Notification** | Message to one person about a change (BEN-01 to BEN-07). Always in the list in the app; also by e-mail and push (F-19). |
| **Channel** | Way a notification reaches the person: the app (always), e-mail, push (BEN-10, BEN-11). |
| **Push device** | A browser on a device where the person allowed push (push subscription). A person can have several (F-19). |
| **Delivery** | One e-mail or one push of a notification, with state and attempts. Recorded with the change and sent after it; failed ones are retried (F-19). |
| **Tile** | Self-contained feature module of the platform, appears on the dashboard. |

## Relationships

```mermaid
erDiagram
    ACCOUNT ||--|{ PERSON : "responds for"
    PERSON ||--o{ TEAM_MEMBERSHIP : has
    TEAM ||--o{ TEAM_MEMBERSHIP : has
    SEASON ||--o{ TEAM_MEMBERSHIP : "applies in"
    TEAM ||--o{ GROUP : "has lines and goalies"
    GROUP |o--o{ TEAM_MEMBERSHIP : "contains players"
    SERIES ||--o{ EVENT : creates
    EVENT }o--|{ TEAM : "belongs to"
    EVENT ||--o{ RESPONSE : receives
    PERSON ||--o{ RESPONSE : gives
    PERSON ||--o{ ABSENCE : "is away"
    ABSENCE |o--o{ RESPONSE : "declined"
    EVENT ||--o{ MATCH : "includes (match day only)"
    EVENT ||--o| CALL_UP : "has (match day only)"
    CALL_UP ||--o{ CALL_UP_ENTRY : includes
    PERSON ||--o{ CALL_UP_ENTRY : "is called up"
    TEAM ||--|{ EVENT_VARIANT : "has (4 standard + own)"
    EVENT }o--o| EVENT_VARIANT : "shown as"
    TEAM ||--o{ SPECIAL_LINEUP : "has (per season)"
    SPECIAL_LINEUP ||--|{ LINEUP_PLACE : "has 1 to 10"
    PERSON |o--o{ LINEUP_PLACE : "is set on"
    EVENT ||--o{ INDIVIDUAL_TRAVEL : "has (match day only)"
    PERSON ||--o{ INDIVIDUAL_TRAVEL : "travels on her own"
    EVENT ||--o{ CAR : "has (match day only)"
    PERSON ||--o{ CAR : drives
    CAR ||--o{ CAR_PASSENGER : carries
    PERSON ||--o{ CAR_PASSENGER : "rides along"
    EVENT ||--o| ROUTE : "has (match day only)"
    EVENT ||--o| ATTENDANCE_RECORD : "has (check on)"
    ATTENDANCE_RECORD ||--o{ ATTENDANCE_ENTRY : includes
    PERSON ||--o{ ATTENDANCE_ENTRY : "is recorded"
    PERSON ||--o{ NOTIFICATION : receives
    NOTIFICATION ||--o{ DELIVERY : "sent as e-mail or push"
    PERSON ||--o{ PUSH_DEVICE : "allows push on"
    PUSH_DEVICE ||--o{ DELIVERY : receives
```

`GROUP` stands for a line or the goalie group of a team.

## The most important rules

1. **One team role per team.** A person can be coach in Team 1 and player in Team 2, but not both in the same team.
2. **At most one line per team.** Per team a player is in at most one group (line or goalies). In two teams she can have two different lines. The limit of one line is a rule, not a fixed structure: it should be possible to lift it later without rebuilding the model.
3. **One response per person and event.** Even if the event belongs to several teams and the person is in several of them.
4. **Coaches are not players.** They respond the same way, but are listed in a block of their own and do not count towards the player total.
5. **Lines are standard lines.** There are no different lines for single events. Special line-ups (power play, penalty kill) exist next to them and do not group the responses.
6. **One match day, one call-up.** All matches of a match day share one response and one call-up. Whether a call-up is made at all is set by the event variant of the team; without one the responses are enough (VAR-06).
7. **Intention and fact stay separate.** A response is never turned into attendance and attendance never changes a response. Attendance is only recorded where the check is switched on (PRS, E-70).
8. **One car per person and match day.** A person drives one car or sits in one car, never both, never two. A car never has more passengers than seats (DRV-02, DRV-03).
9. **Every event has one variant.** The variant decides how the event looks (name, colour, sections) and what it offers (call-up, automatic departure); it never changes the data of the event (VAR-10).
10. **Expected or invited.** Every team of an event is one or the other, and at least one is expected. Invited people are never reminded and never counted as «offen» (TER-15, TER-16).
