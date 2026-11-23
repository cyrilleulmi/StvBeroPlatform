# Technology

Technology decisions and guardrails for the architecture. The foundation (F-00) is implemented.

## Conventions from the foundation (F-00)

| Topic | Convention |
|---|---|
| Versions | Quarkus 3.40 (Maven), Angular 22, Playwright 1.65 |
| Backend packages | `ch.stvbero.platform.core.*` (clock, person, season, team, login, tile, errors, test helpers), `ch.stvbero.platform.<tile>` per tile (e.g. `anwesenheit`) |
| Registering a tile | Backend: CDI bean that implements `TileDefinition` (metadata, `visibleFor`). Frontend: route `loadChildren` in `app.routes.ts`, icon in `core/icon.ts` |
| «Now» | Inject `java.time.Clock`. In the dev/test profile controllable via `PUT /api/test/clock` |
| Test helpers | Build property `stv.test-support` (`true` only in `%dev`/`%test`). Resources with `@IfBuildProperty`, not present in the prod profile |
| Login | Cookie `stv_session` (HttpOnly, SameSite=Lax), sessions in the database. Resources without `@Public` require a session (401) |
| Errors | `DomainError` → Problem Details (`application/problem+json`), validation → 400 with `fields` |
| Domain rules | One class `…Rules` per area without database access (e.g. `TeamRules`, `SeasonRules`), with unit tests. Services load, rules decide, resources stay thin (F-01) |
| Season parameter | Endpoints related to a season take `?season=<id>`; without it the current season applies. Seasons before the current one are read-only (409, SAI-02) |
| Contract | `OpenApiContractTest` compares `/q/openapi` with `docs/api/openapi.yaml` (paths, methods, operationId, status codes, schemas) |
| Frontend types | `npm run api-types` generates `src/app/api/schema.d.ts` from the contract (runs before `start` and `build`) |
| Texts | `frontend/src/app/core/texts.ts` |
| Font | Plus Jakarta Sans served locally via `@fontsource` (no requests to Google Fonts) |
| PWA | `public/manifest.webmanifest`, own lean service worker `public/sw.js` (offline page, no data caches) |

## Decided

| Area | Decision |
|---|---|
| Frontend | Angular in the current version, standalone components, signals. |
| Frontend structure | Each tile is a lazy-loaded feature with its own route. The dashboard shows the tiles depending on the role. |
| Backend | Quarkus with REST API, Java 21 (LTS). |
| Backend structure | One package structure per tile with clear boundaries. |
| Database | PostgreSQL. Schema migrations versioned with Flyway. |
| Roles | At least player, coach, helper/parent, admin. Tiles and actions are visible based on role. |
| Tile concept | A tile provides metadata (title, icon, route, required role) and registers itself centrally. The dashboard renders the overview from it. |
| Delivery | Responsive web app, installable as a PWA. |
| Hosting | Hosted test environments on Fly.io with a Neon database, one container (Dockerfile) built with profile `preview`. See [deployment.md](deployment.md) (E-83). |
| Notifications outside the app | E-mail over SMTP with `quarkus-mailer`; push with Web Push (VAPID) through the service worker of the PWA, encryption (RFC 8291) and signature (RFC 8292) with the JDK, without a library. In `dev`/`test` a simulator replaces both (F-19). |
| Design | Tokens as CSS custom properties, see [design-principles.md](design-principles.md). |

## Still open

| Area | Question |
|---|---|
| Login | E-mail and password, magic link or Keycloak/OIDC. |
| Hosting | Where production with real data runs (test environment and previews: E-83). |
| Notifications | E-mail, push via the PWA, or both. |
| Multi-tenancy | Are several clubs needed in one installation? |

All open points collected: [open-points.md](open-points.md).

## Guardrails from the requirements

These points follow from the domain requirements and should shape the architecture from the start.

1. **Core as a shared foundation.** People, teams, team roles, seasons and roles are needed by all tiles. They belong in a shared core module, not in the attendance tile.
2. **Separate account and person.** The login account is not the person ([PTF-09](requirements/01-people-teams-roles.md)).
3. **Permissions per team.** Permission depends on the team role in the respective team, not on a global role. It is checked in the backend ([07](requirements/07-roles-permissions.md)).
4. **Rules instead of structure.** «At most one line per team» is a checkable rule that can be relaxed later ([LIN-04](requirements/02-lines.md)).
5. **Event to team is n:m.** An event can belong to several teams. The response belongs to the person and the event, not to the team ([ANM-02](requirements/04-responses.md)).
6. **Display is derived.** The grouping by team and line is derived from the event, team memberships and lines. It is not stored per event.
7. **swiss unihockey behind its own layer.** The interface delivers generic tables. A translation layer turns them into match days and matches. If the interface fails, the app keeps running ([06](requirements/06-swiss-unihockey.md)).
8. **Own data stays own data.** Imported fields and self-maintained fields (meeting point, notes, call-up) are kept separate, so the sync overwrites nothing.
9. **Notifications with an exchangeable channel.** Trigger and delivery channel are separate ([08](requirements/08-notifications.md)).
