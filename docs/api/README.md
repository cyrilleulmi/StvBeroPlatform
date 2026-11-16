# API

The contract is in [openapi.yaml](openapi.yaml) (OpenAPI 3.1, 53 operations). It describes the target state for the core.

## Way of working: contract first

1. If a feature changes the API, `openapi.yaml` is updated **first**, in the same PR as the code.
2. The backend implements the contract. Quarkus (`quarkus-smallrye-openapi`) generates `/q/openapi` from it at runtime.
3. A backend test compares the generated specification with `openapi.yaml` (paths, methods, status codes, schemas). Differences make the build fail. The test comes with F-00.
4. The frontend generates its TypeScript types from `openapi.yaml` (e.g. with `openapi-typescript`) instead of writing them by hand.

Check:

```bash
npx -y @redocly/cli lint docs/api/openapi.yaml        # 0 errors, 0 warnings
npx -y @redocly/cli preview-docs docs/api/openapi.yaml # readable docs in the browser
```

Running locally: Swagger UI at `http://localhost:8080/q/swagger-ui`.

## Conventions

| Topic | Rule |
|---|---|
| Language | Paths, fields and enum values in German, from the [domain model](../domain-model.md). Umlauts written out (`zugehoerigkeiten`, `veroeffentlichen`). |
| Paths | Lowercase, plural for collections (`/api/termine`), actions as a verb sub-path (`/absagen`, `/veroeffentlichen`). |
| Fields | `camelCase`. Enum values `UPPER_WITH_UNDERSCORE`. |
| IDs | UUID. Seed IDs are fixed, see [test-data.md](../test-data.md#id-scheme). |
| Time | ISO 8601 with offset (`2026-11-17T18:30:00+01:00`). Time zone of the domain logic: Europe/Zurich. Dates without time as `date`. |
| «Now» | Always comes from the backend clock (can be fixed in the test profile), never from the browser. |
| Errors | RFC 9457 Problem Details (`application/problem+json`). `detail` is a German sentence for direct display, `regel` names the requirement ID. |
| Status codes | 400 formally invalid · 401 not logged in · 403 no permission · 404 not found · 409 conflicts with the state · 422 domain rule violated · 502 swiss unihockey unavailable |
| Permissions | Checked in the backend (RR-07). The UI only hides what the API refuses anyway. Helper field: `darfBearbeiten`. |
| Visibility | Data someone may not see (comments, ANM-07) is missing from the response. It is not just hidden in the frontend. |
| Sorting | The API returns data in display order. The frontend does not re-sort. |
| Login | Session cookie `stv_sitzung`. The production method is open (O-01). |

## Test endpoints

Everything under `/api/test/` exists only in the `dev` and `test` profiles. In the `prod` profile these paths return 404 because the resources are not registered (e.g. via `@IfBuildProfile`). Purpose and use: [testability.md](../testability.md#test-helpers-in-the-app).

## Assumptions in the contract

These points are not stated explicitly in the requirements and are implemented in the contract as an assumption:

| Point | Assumption |
|---|---|
| Notifications | There is always a list in the app (`/api/benachrichtigungen`). E-mail or push come as an additional channel (BEN-10, BEN-11). |
| Match gone from swiss unihockey | Is marked (`beiSuNichtMehrVorhanden`), not deleted. The coach decides. |
| Changing team role | If a player becomes a coach, she loses her line assignment in this team. |
| Late cancellation | The coaches of the teams in which the person is a player for the event are notified (O-17). |
