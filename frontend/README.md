# Frontend

Angular (standalone components, signals). Every tile is a lazy-loaded feature (`src/app/<tile>/`).

```bash
npm ci
npm start          # http://localhost:4200, /api is proxied to the backend on :8080
npm run lint
npm run build
npm run api-types  # generate TypeScript types from docs/api/openapi.yaml (runs before start/build)
```

- Design tokens and themes (Hell, Düne, GitHub Dark; stored values `light`, `dune`, `github-dark`, `system`): `src/styles.css` (docs/design-principles.md). Selection in the profile: `src/app/core/theme.service.ts`
- UI texts: `src/app/core/texts.ts` (`TEXTS`; English keys, German values)
- Icons (Lucide style): `src/app/core/icon.ts`
- Test mode (`/dev-login`): only in the development build (`src/environments/environment.ts`, `environment.testMode`)

Start the whole app: `scripts/start.sh` in the repository root.
