# Frontend

Angular (standalone components, signals). Every tile is a lazy-loaded feature folder in `src/app/`.

```bash
npm ci
npm start          # http://localhost:4200, /api is proxied to the backend on :8080
npm run lint       # ESLint: style, module boundaries, size limits
npm test           # unit tests (Vitest via ng test), single run, for CI
npm run test:watch # unit tests in watch mode
npm run build
npm run api-types  # generate TypeScript types from docs/api/openapi.yaml (runs before start/build)
```

## Folders by module

The modules are defined in `../modules.json` (explained in `docs/architecture.md`). Folders in `src/app/`:

| Module | Folders / files | May import |
| --- | --- | --- |
| core | `core`, `api`, `shell` | nothing |
| notification | `notifications` | core |
| attendance | `attendance` | core, driver |
| swissunihockey | `swissunihockey` | core |
| driver | `drivers` | core |
| statistics | `statistics` | core, attendance |
| app (composition) | `pages`, `app.ts`, `app.config.ts`, `app.routes.ts` | everything |

## Import rule

Imports are relative, so the rule is enforced by ESLint (`no-restricted-imports`, generated from `modules.json` in `eslint.config.js`). Feature folders never import from `pages` or `shell`; `core`/`api`/`shell` never import from a feature or from `pages`. Pages that combine several modules belong in `pages`.

ESLint also limits files to 500 lines (not `core/texts.ts`, not specs) and a complexity of 20. Files that were above the limits at the time are in the commented «frozen» list in `eslint.config.js`; entries may only be lowered or removed.

## Unit tests

Colocated `*.spec.ts` next to the code (Vitest, jsdom). Test builders that are not specs are named `*.testing.ts` and are excluded from the app build. New components, services and pure helpers get a spec file.

## Other

- Design tokens and themes: `src/styles.css` (docs/design-principles.md); selection in `src/app/core/theme.service.ts`
- UI texts: `src/app/core/texts.ts` (`TEXTS`; English keys, German values)
- Icons (Lucide style): `src/app/core/icon.ts`
- Test mode (`/dev-login`): only in the development build (`src/environments/environment.ts`, `environment.testMode`)

Start the whole app: `scripts/start.sh` in the repository root.
