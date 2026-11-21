# Frontend testability

The test agent of the [pipeline](pipeline/README.md) uses the app like a human, but through the browser's **accessibility tree** (Playwright MCP). It sees no pixels, only roles, names and states. Whatever does not show up there, it can neither find nor check.

These rules are binding for every feature. The review agent checks them, the test agent depends on them.

## Why

- Playwright MCP hands the agent a text snapshot of the accessibility tree. Clicks and inputs use references from this snapshot, not coordinates.
- For locators, Playwright recommends the order **role → label → text → test ID → CSS** (last resort).
- Pure layout `div`s drop out of the accessibility tree or appear as nameless `generic` nodes, even if they have a `data-testid`. Groups must therefore be marked up semantically.
- Good accessibility and good testability are the same thing. What a screen reader understands, the agent understands too.

## Rules

### 1. Semantics first

| Rule | Example |
|---|---|
| Use landmarks: `header`, `nav`, `main`, `footer`. Exactly one `main` per page. | `<nav aria-label="Hauptnavigation">` |
| Every page has exactly one `h1` that matches the page title. | `<h1>Training Di 18:30</h1>` |
| Groups are a `section` with a heading or `aria-labelledby`. | `<section aria-labelledby="team-1-titel">` |
| Lists are real lists (`ul`/`ol`/`li`) or have `role="list"` with a name. | `<ul aria-label="Linie 1">` |
| Tables only for tabular data, with `th` and `scope`. | Season statistics |
| Anything clickable is a `button` or `a`, never a `div` with a click handler. | – |
| Links lead to a URL, buttons trigger an action. | – |

### 2. Every control has a name

- Buttons have visible text. Icon buttons have an `aria-label` (e.g. «Menü öffnen»).
- Input fields have a `<label>`. A placeholder is not a label.
- Identical actions in lists are told apart by their container, not by their name. The container has a name (e.g. `li` with `aria-label="Anna Muster"`), or the button name contains the context («Anna aufbieten»).
- Texts come from one central place (constants or i18n file), so tests and docs use the same words.

### 3. States are machine-readable, not just colored

Color alone carries no information (see also [design-principles.md](design-principles.md#not-yet-defined)).

| State | Markup |
|---|---|
| Selected response | Response buttons as a group with `role="radiogroup"` or `aria-pressed="true"` |
| A person's response in lists | Visible text or `aria-label` («dabei», «nicht dabei», «vielleicht», «offen») **and** `data-response="ATTENDING"` (the status from the API) |
| Active navigation item | `aria-current="page"` |
| Called up | Checkbox with `aria-checked` or the text «aufgeboten» |
| Loading | Container with `aria-busy="true"`, removed afterwards |
| Action running | Clicked button `disabled` and `aria-busy="true"`, removed afterwards |
| Disabled | `disabled` or `aria-disabled="true"` with an explanation nearby |
| Form error | `aria-invalid="true"` and error message via `aria-describedby` |

Counters («dabei»/«nicht dabei»/«vielleicht»/«offen») also have `data-counter="attending"` (`declined`, `maybe`, `pending`) and a text that contains the number and the word («10 dabei»).

### 4. Test IDs where semantics are not enough

`data-testid` is the supplement, not the replacement. It is set on:

- repeated containers that represent an entity (cards, list entries),
- groupings in the overview (team, line, coach block),
- counters and status displays,
- everything listed as a fixed test ID in [ui/navigation.md](ui/navigation.md#test-ids).

**Naming scheme:** `kebab-case`, English (glossary terms, NF-40), `<area>-<element>[-<key>]`. Keys that come from the API or the seed keep their value (e.g. `tile-anwesenheit`, `dev-login-person-petra`).

| Pattern | Example |
|---|---|
| Entity with ID | `event-card-40000000-0000-4000-8000-000000000001` |
| Entity with a domain key | `overview-team-team-1`, `overview-group-line-1` |
| Single element | `my-response`, `coach-block`, `call-up-publish` |

Test IDs are part of the interface: whoever renames one updates the tests and [navigation.md](ui/navigation.md) in the same PR.

### 5. Every state has a URL

- Every screen can be reached directly by URL (deep link), also after a reload.
- Filters and tabs are in the URL (`/termine?team=…&ansicht=vergangen`).
- Dialogs that belong to a screen may have no URL. Standalone forms (create event, call-up) have their own route.

This way the test agent jumps straight to the right place instead of clicking its way there.

### 6. Feedback can be found

- Success messages (toasts) have `role="status"`, error messages `role="alert"`. They stay visible for at least 5 seconds and do not close on the first focus change.
- Dialogs are `role="dialog"` (or `<dialog>`) with `aria-modal="true"` and a name from their heading.
- Empty states have a text («Keine kommenden Termine»), never an empty area.
- Errors from the backend are shown visibly, not only written to the console.

### 7. Usable without hover and without drag and drop

- No action is reachable only by hover.
- Drag and drop (lines) always has an equivalent alternative with buttons or a select field («Linie wählen»).
- Everything is reachable by keyboard, with visible focus.

### 8. Deterministic

- Lists have a fixed sort order (e.g. people by last name, then first name).
- Animations respect `prefers-reduced-motion` and are then off. The test agent sets reduced motion.
- Time-dependent texts («in 2 Tagen») are derived from the backend clock, which can be fixed in the test profile (see below).
- No random IDs in the DOM that change on every render.

## Test helpers in the app

These helpers exist only in the Quarkus profiles `dev` and `test`. In the `prod` profile the endpoints do not exist (not just hidden). The review agent checks this.

| Helper | Purpose | Interface |
|---|---|---|
| **Reset seed** | Bring the database into the defined initial state: the test data set with simulators and the fixed clock. `?dataset=CLUB` loads the club data set with live data instead ([test-data.md](test-data.md#data-sets)) | `POST /api/test/reset`, `GET /api/test/dataset` |
| **Fixed clock** | Set «now» to check response deadlines, late cancellations and reminders | `PUT /api/test/clock`, `DELETE /api/test/clock` |
| **No current season** | Create the state «no season set as current» (notice `/keine-saison`). Reset restores the seed | `DELETE /api/test/current-season` |
| **Log in as** | Log in as any seed person without a password | `POST /api/test/login`, page `/dev-login` |
| **Read notifications** | Check who was notified when and about what, with the e-mails and pushes of each notification (channel, address, subject, status, attempts; F-19) | `GET /api/test/notifications` |
| **Simulate e-mail and push** | Nothing goes out in `dev`/`test`. Let a channel fail (`failing`) or let the push service report devices as gone (`expiredEndpoints`) | `PUT /api/test/delivery` |
| **Simulate swiss unihockey** | Set the match data that the sync receives «from swiss unihockey», the league view of the league page (`league`, F-17) or an outage | `PUT /api/test/swiss-unihockey/matches` |

Details in the API contract: [api/openapi.yaml](api/openapi.yaml) (tag «Test»). The seed data: [test-data.md](test-data.md).

Push in the browser: E2E tests cannot reach a real push service. They replace the browser permission and `PushManager` with an init script (`fakePush` in `e2e/tests/f19-notification-channels.spec.ts`); the service worker stays real, and a push can be dispatched in it with `PushEvent`.

The page `/dev-login` lists all seed persons grouped by team, coaches first and marked «Coach»; a person in several teams appears in each of them. One click logs in. At the top it clearly shows «Testmodus», which data set is loaded, and buttons to load the other one.

## Tools

| Tool | Used for | Where |
|---|---|---|
| Playwright Test | Automated end-to-end tests, run in CI and by every agent | `e2e/` |
| Playwright MCP | Exploratory tests by the test agent | Configuration in [.mcp.json](../.mcp.json) |
| axe-core (`@axe-core/playwright`) | Automated accessibility check per screen in the E2E tests | `e2e/` |

Conventions for E2E tests:

- Locators in the order `getByRole` → `getByLabel` → `getByText` → `getByTestId`. No CSS or XPath selectors.
- **Only exception:** document elements without a role that do not appear in the accessibility tree (`<html>`, e.g. for `data-theme`, and `<link>` in the head, e.g. the manifest). For these, `e2e/tests/helpers.ts` has the helpers `documentRoot(page)` and `headLink(page, rel)`; only there is the CSS selector written. Tests use these helpers and write none themselves. If another such element is needed, a helper is added there.
- `e2e/tests/nb17-css-selectors.spec.ts` checks this statically: `locator()`, `$()`, `$$()`, `$eval()`, `waitForSelector()` as well as `css=`/`xpath=` outside `helpers.ts` make the test fail.
- No `waitForTimeout`. Wait for states (`expect(...).toBeVisible()` etc.).
- Every test starts with `POST /api/test/reset` and logs in via `POST /api/test/login`.
- Every test runs in two projects: `mobile` (375×812, touch) and `desktop` (1280×800).
- The test name states the requirement ID: `test('ANM-04: «vielleicht» requires a comment', …)`. Titles are English; UI text quoted in them stays German in «…».
- `e2e/tests/seed.spec.ts` is the entry test (reset, login, dashboard visible). It also serves as the template when tests are generated with the Playwright test agents (planner/generator/healer).
