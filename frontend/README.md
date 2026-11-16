# Frontend

Angular (standalone components, signals). Every tile is a lazy-loaded feature (`src/app/<tile>/`).

```bash
npm ci
npm start          # http://localhost:4200, /api is proxied to the backend on :8080
npm run lint
npm run build
npm run api-typen  # generate TypeScript types from docs/api/openapi.yaml (runs before start/build)
```

- Design tokens and themes (Hell, Düne, GitHub Dark): `src/styles.css` (docs/design-principles.md). Selection in the profile: `src/app/kern/theme.service.ts`
- UI texts: `src/app/kern/texte.ts`
- Icons (Lucide style): `src/app/kern/icon.ts`
- Test mode (`/dev-login`): only in the development build (`src/umgebung/umgebung.ts`)

Start the whole app: `scripts/start.sh` in the repository root.
