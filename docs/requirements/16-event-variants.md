# 16 · Event variants

Teams work differently: the first men's team picks a call-up for every match day, a junior team only wants to know who comes. An **event variant** («Terminvariante») says per team how a kind of event looks and behaves: its name, its colour in the event list, whether there is a call-up, which sections the event page shows and in which order (batch «event variants», decision E-86).

Status legend: see [README](../../README.md#requirement-status).

## Variants

| ID | Requirement | Status |
|---|---|---|
| VAR-01 | Every team has four standard variants that cannot be deleted: «Training» (practice), «Spieltag» (match day entered by hand), «swiss unihockey Spieltag» (match day whose matches come from the sync, [SUH](06-swiss-unihockey.md)) and «Anlass» (other event). They exist from the moment the team is created. | Decided |
| VAR-02 | Coaches (of any team, E-69) and admins add variants of their own to a team («Variante hinzufügen»), e.g. «Cupspiel» or «Trainingslager». A variant of one's own has a name and one of the three event types (TER-02), which decides what the event can do (matches, call-up, timeline and driver list only for match days). At most 12 variants per team. Deleting a variant of one's own leaves its events in place; they fall back to the standard variant of their type. | Decided (configurable), Assumption (limit, fallback) |
| VAR-03 | An event has exactly one variant. The form for a single event offers the variants of the first team of the event that fit the event type («Variante»). Without a choice the standard variant applies: for a match day with at least one imported match «swiss unihockey Spieltag», otherwise the one of the event type. For an event of several teams the variants of the team that comes first by name apply. Events of a series use the standard variant of their type. | Assumption |
| VAR-04 | Every variant has a colour from a fixed palette of nine: «Grün», «Türkis», «Blau», «Violett», «Pink», «Rot», «Orange», «Gelb», «Grau». Defaults: «Training» green, «Spieltag» violet, «swiss unihockey Spieltag» blue, «Anlass» pink, variants of one's own grey. Each colour is a pair of design tokens per theme (text colour and soft background) with a contrast of at least 4.5:1, so that a chosen colour works in «Hell» and «Dunkel». Free colour values are not possible. | Decided (configurable, defaults), Assumption (palette instead of free colours) |
| VAR-05 | The event list and the event page show the variant of every event: the date box in the colour of the variant and a chip with its name. The colour is never the only sign, the name is always there ([TER-14](03-events.md)). | Decided |
| VAR-06 | **Call-up or responses only.** A variant of the type match day says whether a call-up is made («Aufgebot machen»). Without it, the match day works with responses alone: no section «Aufgebot», no call-up status, the call-up API answers 409, and the driver list counts who responded «dabei» (DRV-04). Default: **off**. A call-up that exists when the setting is switched off is kept and shown again when it is switched on. | Decided |
| VAR-07 | **Sections of the event page.** A variant lists the sections the event page shows and their order: «Anmeldungen» (overview of the responses), «Spiele», «Aufgebot», «Zeitplan», «Fahrerliste», «Aufstellungen» (special line-ups, [LIN-13](02-lines.md)), «Präsenz» and «Details». Coaches move sections up and down and hide them. «Details» cannot be hidden (it holds the link to edit the event). Sections that the event type does not have are not offered. The own response («Meine Antwort») always stays on top. Default order: «Anmeldungen» first, then «Spiele», «Aufgebot», «Zeitplan», «Fahrerliste», «Aufstellungen», «Präsenz», «Details». | Decided (configurable, responses first), Assumption (the rest of the order, «Details» fixed) |
| VAR-08 | **Automatic departure.** A variant of the type match day can set the meeting time by itself («Abfahrtszeit automatisch setzen»): as long as nobody has entered a meeting time by hand, the meeting time is the suggested departure of the timeline ([TML-09](15-timeline.md)). Default: **on**. | Decided |
| VAR-09 | A variant carries the defaults for new events: the response deadline in hours (TER-10) and whether the attendance check is on (PRS-01). The form fills them in when the variant is chosen; match days created by the sync start with those of «swiss unihockey Spieltag». | Assumption |
| VAR-10 | Variants are managed under `/verwaltung/teams/:id` in the section «Terminvarianten»: a list with the colour and the main settings of each variant, and a dialog to change one. Variants apply to the team in every season. Changing a variant changes all its events at once, also past ones (they are settings of the display and of what is offered, not data of the event). | Assumption |
| VAR-11 | Defaults per team: a new team makes no call-up («nur An- und Abmeldungen»). In the club data set «Herren KF 3. Liga» (the first men's team) makes a call-up for both match day variants; all other teams do not. The teams of the test data set make a call-up, so that the automated tests of the call-up keep running ([test-data.md](../test-data.md)). | Decided (first men's team, default off), Assumption (test data set) |
| VAR-12 | **Reason with «nicht dabei».** A variant says whether «nicht dabei» needs a reason («Grund bei «nicht dabei» verlangen», [ANM-05](04-responses.md)). Default: **off**. It applies at once to all events of the variant; responses already given stay as they are. The list of variants shows «Grund bei Abmeldung» for variants with the setting. | Decided (per variant, E-95), Assumption (default off) |

## Standard variants

| Variant | Type | Colour | Call-up | Automatic departure | Sections |
|---|---|---|---|---|---|
| «Training» | practice | «Grün» | – | – | Anmeldungen, Präsenz, Details |
| «Spieltag» | match day | «Violett» | off | on | Anmeldungen, Spiele, Aufgebot, Zeitplan, Fahrerliste, Aufstellungen, Präsenz, Details |
| «swiss unihockey Spieltag» | match day | «Blau» | off | on | same as «Spieltag» |
| «Anlass» | other event | «Pink» | – | – | Anmeldungen, Präsenz, Details |

The section «Aufgebot» only appears where a call-up is made (VAR-06), «Präsenz» only where the attendance check is on (PRS-02).

## Notes

- The API is `GET`/`POST /api/teams/{teamId}/event-variants` and `PUT`/`DELETE /api/teams/{teamId}/event-variants/{variantId}`; events carry `variant` (read) and take `variantId` (write).
- Out of scope: variants for series, own event types beyond the three of TER-02, free colour values.
