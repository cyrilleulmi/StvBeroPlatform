# Hinweise für KI-Coding-Agenten

Dieses Repo wird mit Unterstützung von KI-Agenten entwickelt. Diese Datei sagt, wo die Wahrheit steht und wie gearbeitet wird.

## Zuerst lesen

1. [README.md](README.md) – Überblick und Status-Legende.
2. [docs/domain-model.md](docs/domain-model.md) – Begriffe. Verwende im Code, in der API und in der Oberfläche dieselben Begriffe.
3. [docs/requirements/](docs/requirements/) – die Anforderungen mit IDs (z.B. `ANM-03`).
4. [docs/design-principles.md](docs/design-principles.md) – verbindliches Design.
5. [docs/technology.md](docs/technology.md) – Technologie und Leitplanken.
6. [docs/testability.md](docs/testability.md) – verbindliche Regeln, damit Test-Agenten das Frontend bedienen können.
7. [docs/api/openapi.yaml](docs/api/openapi.yaml) – API-Vertrag (contract first), Konventionen in [docs/api/README.md](docs/api/README.md).
8. [docs/states.md](docs/states.md), [docs/ui/navigation.md](docs/ui/navigation.md), [docs/test-data.md](docs/test-data.md) – Verhalten, Screens, Seed.

## Stand und Pipeline

Features werden über die [Produktionspipeline](docs/pipeline/README.md) gebaut: Entwicklung → Review → Test → automatischer Merge durch den Test-Agenten. Ein Feature wird umgesetzt, sobald Cyrille das Issue mit `status:bereit` freigibt. Reihenfolge und Akzeptanzkriterien: [docs/pipeline/backlog.md](docs/pipeline/backlog.md).

Die Rollen sind Skills unter `.claude/skills/` (`/pipeline-entwicklung`, `/pipeline-review`, `/pipeline-test`). Gemeinsame Regeln: [docs/pipeline/agent-rules.md](docs/pipeline/agent-rules.md). Was jeder PR erfüllen muss: [docs/pipeline/definition-of-done.md](docs/pipeline/definition-of-done.md).

In Cloud-Sitzungen gibt es keinen Docker-Daemon. Datenbank: `scripts/db-start.sh`.

## Umgang mit Anforderungen

- Der Status entscheidet über die Verbindlichkeit (Legende im README).
- **Entschieden** und **Angenommen**: umsetzen wie beschrieben.
- **Annahme**: umsetzen, aber beim Ergebnis nennen, dass es eine Annahme ist.
- **Offen**: sinnvolle Annahme treffen, beim Ergebnis nennen und in [docs/open-points.md](docs/open-points.md) festhalten.
- **Später**: nicht umsetzen. Das Modell aber so bauen, dass es später ohne Umbau geht.
- Verweise in Commits, Tests und Pull Requests auf die Anforderungs-IDs.
- Ändert sich eine Anforderung, wird zuerst das Dokument angepasst und der Entscheid in [docs/decisions.md](docs/decisions.md) eingetragen.

## Arbeitsweise

- Pläne werden nur gemeinsam erarbeitet, wenn das ausdrücklich gewünscht ist.
- Sonst: sinnvolle Annahmen treffen und vollständig umsetzen, ohne Rückfragen oder Zwischenstopps. Keine kleinen Zwischenschritte.
- Getroffene Annahmen beim Ergebnis kurz auflisten.
- Jedes Ergebnis ist lauffähig und direkt aufrufbar (URL bzw. Startbefehl), auf Desktop und Mobile geprüft, mit Seed-Daten zum Ausprobieren.
- Tests für Backend-Logik.

## Sprache

- Oberfläche: Deutsch (Schweiz, also «ss» statt «ß»), klare Sprache, keine Fachbegriffe.
- In den Anforderungen steht durchgehend «Spielerin» und «Spielerinnen», so wie in der Erarbeitung verwendet. Welche Form die Oberfläche zeigt, ist noch offen (siehe [docs/open-points.md](docs/open-points.md)).
- Dokumentation in diesem Repo: Deutsch.
