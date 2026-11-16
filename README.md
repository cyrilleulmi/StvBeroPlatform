# StvBeroPlatform

Zentrale Web-Plattform für das Unihockey des STV Beromünster. Sie bündelt die organisatorischen Werkzeuge, die heute verstreut sind (Listen, Chats, Tabellen), an einem Ort – einfach genug, dass Spielerinnen, Coaches und Helfer sie ohne Anleitung nutzen können.

> **Stand 15.11.2026:** Anforderungen und [Produktionspipeline](docs/pipeline/README.md) stehen. Das Grundgerüst (F-00) ist gebaut. Weitere Features entstehen über die Pipeline, sobald ein Issue `status:bereit` bekommt.

## Starten und testen

```bash
scripts/start.sh   # Datenbank, Backend (Quarkus, Profil dev) und Frontend starten
scripts/test.sh    # API-Lint, Backend-Tests, Frontend-Lint/Build, E2E-Tests (mobil + desktop)
```

Danach: http://localhost:4200 – im Testmodus über http://localhost:4200/dev-login als Seed-Person anmelden ([test-data.md](docs/test-data.md)). Swagger UI: http://localhost:8080/q/swagger-ui.

Voraussetzungen: Java 21, Maven, Node 22, PostgreSQL-Binaries (oder eine Datenbank `stv` auf `localhost:5432`, Benutzer/Passwort `stv`), Google Chrome für die E2E-Tests.

| Ordner | Inhalt |
|---|---|
| `backend/` | Quarkus, Java 21. Package pro Kachel unter `ch.stvbero.platform.<kachel>`, gemeinsamer Kern unter `ch.stvbero.platform.kern`. Migrationen: `src/main/resources/db/migration` (Flyway) |
| `frontend/` | Angular (Standalone, Signals). Eine Kachel = ein lazy geladenes Feature |
| `e2e/` | Playwright Test (`tests/`, `plans/`), Projekte `mobil` und `desktop` |
| `scripts/` | `db-start.sh`, `start.sh`, `test.sh`, `pipeline.sh` |

## Warum eine eigene App?

Standard-Apps decken zwei Dinge nicht ab, die für uns zentral sind:

1. **Trainings mit mehreren Teams.** Ein Training kann gleichzeitig zu mehreren Teams gehören. Die An- und Abmeldung zeigt die Mitglieder direkt nach Team gruppiert.
2. **Linien.** Spielerinnen sind Linien zugewiesen. Bei An- und Abmeldungen werden sie direkt nach Linie gruppiert angezeigt.

Dazu kommt: Coaches melden sich ebenfalls an und ab, erscheinen aber nicht als Spielerinnen. Und eine Person kann mehreren Teams angehören, mit je einer eigenen Funktion (z.B. Team 1 Coach, Team 2 Spielerin).

## Inhalt

| Dokument | Inhalt |
|---|---|
| [docs/project-context.md](docs/project-context.md) | Ziel, Grundprinzipien, Kacheln, Arbeitsweise |
| [docs/domain-model.md](docs/domain-model.md) | Begriffe und wie sie zusammenhängen |
| [docs/requirements/](docs/requirements/) | Anforderungen an den Kern, pro Thema eine Datei |
| [docs/more-tiles.md](docs/more-tiles.md) | Geplante Kacheln ausserhalb des Kerns |
| [docs/later-features.md](docs/later-features.md) | Bewusst zurückgestellte und gestrichene Funktionen |
| [docs/design-principles.md](docs/design-principles.md) | Grunddesign «Salbei frisch»: Farben, Formen, Typografie |
| [docs/technology.md](docs/technology.md) | Technologie-Entscheide und Architektur-Leitplanken |
| [docs/open-points.md](docs/open-points.md) | Ungeklärte Fragen |
| [docs/decisions.md](docs/decisions.md) | Protokoll der getroffenen Entscheide |
| [docs/pipeline/](docs/pipeline/README.md) | Produktionspipeline: Ablauf, Einrichtung, Backlog, Definition of Done |
| [docs/api/](docs/api/README.md) | API-Vertrag (OpenAPI 3.1) und Konventionen |
| [docs/states.md](docs/states.md) | Zustands- und Sequenzdiagramme |
| [docs/ui/navigation.md](docs/ui/navigation.md) | Routen, Screens, feste Test-IDs |
| [docs/testability.md](docs/testability.md) | Regeln, damit Test-Agenten das Frontend bedienen können |
| [docs/test-data.md](docs/test-data.md) | Seed-Daten mit festen IDs und Testszenarien |
| [AGENTS.md](AGENTS.md) | Hinweise für KI-Coding-Agenten, die in diesem Repo arbeiten |

### Anforderungen an den Kern

| Nr. | Thema | Kürzel |
|---|---|---|
| 01 | [Personen, Teams, Funktionen](docs/requirements/01-people-teams-roles.md) | PTF |
| 02 | [Linien](docs/requirements/02-lines.md) | LIN |
| 03 | [Termine](docs/requirements/03-events.md) | TER |
| 04 | [An- und Abmeldung](docs/requirements/04-responses.md) | ANM |
| 05 | [Spieltage und Aufgebot](docs/requirements/05-match-days-call-up.md) | AUF |
| 06 | [Anbindung swiss unihockey](docs/requirements/06-swiss-unihockey.md) | SUH |
| 07 | [Rollen und Rechte](docs/requirements/07-roles-permissions.md) | RR |
| 08 | [Benachrichtigungen](docs/requirements/08-notifications.md) | BEN |
| 09 | [Saisons](docs/requirements/09-seasons.md) | SAI |
| 10 | [Nichtfunktionale Anforderungen](docs/requirements/10-non-functional.md) | NF |

## Status der Anforderungen

Jede Anforderung trägt einen Status. Er zeigt, wie verbindlich sie ist.

| Status | Bedeutung |
|---|---|
| **Entschieden** | Von Cyrille ausdrücklich so festgelegt. |
| **Angenommen** | Vorschlag aus der gemeinsamen Erarbeitung, dem nicht widersprochen wurde. Gilt bis auf Widerruf. |
| **Annahme** | Noch nicht besprochen. Sinnvolle Annahme, damit die Umsetzung nicht blockiert ist. Darf hinterfragt werden. |
| **Offen** | Muss vor oder während der Umsetzung geklärt werden. |
| **Später** | Bewusst zurückgestellt. Nicht umsetzen, aber das Modell nicht dagegen verbauen. |
