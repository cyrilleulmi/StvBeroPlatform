# 15 · Timeline of a match day

A match day shows its day at a glance: when the team leaves, when it arrives, how much time there is before the first match, the matches with the time between them, and roughly when everyone is back (batch «club data», decision E-85).

Status legend: see [README](../../README.md#requirement-status).

## Timeline

| ID | Requirement | Status |
|---|---|---|
| TML-01 | Every match day has the section «Zeitplan» in `/termine/:id`, for everyone who may see the match day. It lists in order of time: departure, arrival, time before the first match, each match, the time between two matches, cool down and the approximate return. Other events have no timeline (API 409). | Decided |
| TML-02 | A match lasts as long as the team says: «Dauer eines Spiels», 10 to 240 minutes, default **50**. The same duration sets the end of a match day created by the sync (last start plus this duration). | Decided |
| TML-03 | Each team says how long before the first match it wants to arrive at the venue: «Ankunft vor dem ersten Spiel», 0 to 240 minutes, default **70**. | Decided |
| TML-04 | Suggested departure: first match minus the arrival lead minus the driving time, rounded down to five minutes. While a match day has no meeting time, the timeline shows the suggestion, marked «Vorschlag». Coaches of the team and admins take it over as meeting time with one click («Vorschlag als Treffpunkt-Zeit übernehmen»). With a meeting time set, the timeline starts there and still names the suggestion to coaches if it differs. | Decided |
| TML-05 | After the last match, 20 minutes are planned to cool down and shower. | Decided |
| TML-06 | The approximate return is the end of the cool down plus the driving time back to the meeting point. | Decided |
| TML-07 | The driving time is the one of the driver list (DRV-05). Without a meeting point, or without a driving time (no route found, routing unavailable, kilometres entered by hand), departure suggestion, arrival and return are missing and the timeline says why. If the set meeting time leaves less than the arrival lead, the timeline says so. | Assumption |
| TML-08 | Match duration, arrival lead and the usual meeting point (AUF-32) are settings of the team, changed by coaches (of any team, E-69) and admins under `/verwaltung/teams/:id`. They apply to the team, not per season, and to every match day from then on. With several teams on one match day the longest duration and the longest lead apply. | Assumption |

## Notes

- Matches that swiss unihockey no longer delivers are left out of the timeline.
- The API is `GET /api/events/{eventId}/timeline` and `POST /api/events/{eventId}/timeline/apply-departure`; the settings are `PUT /api/teams/{teamId}/match-day-settings`.
- Times are shown to the minute; only the suggested departure is rounded.
