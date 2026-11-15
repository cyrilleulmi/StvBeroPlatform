#!/usr/bin/env bash
# Setup-Skript für die Claude-Cloud-Umgebung der Pipeline.
#
# Den INHALT dieser Datei in claude.ai/code → Umgebung bearbeiten → Setup-Skript einfügen.
# Das Skript läuft, bevor das Repo geklont ist, und wird gecacht. Es darf daher
# nichts aus dem Repo voraussetzen.
#
# Das Standard-Image bringt bereits mit: Java 21, Maven, Gradle, Node 22, Google Chrome,
# PostgreSQL-16-Binaries. Hier wird nur vorgewärmt und geprüft.
set -euo pipefail

echo "== Werkzeuge =="
java -version 2>&1 | grep -v Picked | head -1
node -v
command -v google-chrome >/dev/null && google-chrome --version || echo "WARNUNG: Google Chrome fehlt"
ls -d /usr/lib/postgresql/*/bin >/dev/null 2>&1 && ls -d /usr/lib/postgresql/*/bin || echo "WARNUNG: PostgreSQL-Binaries fehlen"

echo "== npm-Cache vorwärmen =="
npm cache add @playwright/mcp@0.0.85 >/dev/null 2>&1 || true
npm cache add @redocly/cli@2.78.0 >/dev/null 2>&1 || true
npm cache add @playwright/test@1.65.1 >/dev/null 2>&1 || true

echo "== GitHub CLI =="
command -v gh >/dev/null && gh --version | head -1 || echo "WARNUNG: gh fehlt"

echo "Setup fertig."
