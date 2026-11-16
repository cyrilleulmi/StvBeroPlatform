# Frontend

Angular (Standalone Components, Signals). Jede Kachel ist ein lazy geladenes Feature (`src/app/<kachel>/`).

```bash
npm ci
npm start          # http://localhost:4200, /api wird an das Backend auf :8080 weitergeleitet
npm run lint
npm run build
npm run api-typen  # TypeScript-Typen aus docs/api/openapi.yaml erzeugen (läuft vor start/build)
```

- Design-Tokens und Darstellungen (Hell, Düne, GitHub Dark): `src/styles.css` (docs/design-principles.md). Auswahl im Profil: `src/app/kern/theme.service.ts`
- Texte der Oberfläche: `src/app/kern/texte.ts`
- Icons (Lucide-Stil): `src/app/kern/icon.ts`
- Testmodus (`/dev-login`): nur im Development-Build (`src/umgebung/umgebung.ts`)

Ganze App starten: `scripts/start.sh` im Repo-Wurzelverzeichnis.
