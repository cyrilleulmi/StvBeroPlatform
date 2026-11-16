#!/usr/bin/env bash
# Setup script for the pipeline's Claude cloud environment.
#
# Paste the CONTENT of this file into claude.ai/code → edit environment → setup script.
# The script runs before the repo is cloned and is cached. It must therefore
# not depend on anything from the repo.
#
# The default image already includes: Java 21, Maven, Gradle, Node 22, Google Chrome,
# PostgreSQL 16 binaries. This script only warms caches and checks.
set -euo pipefail

echo "== Tools =="
java -version 2>&1 | grep -v Picked | head -1
node -v
command -v google-chrome >/dev/null && google-chrome --version || echo "WARNING: Google Chrome is missing"
ls -d /usr/lib/postgresql/*/bin >/dev/null 2>&1 && ls -d /usr/lib/postgresql/*/bin || echo "WARNING: PostgreSQL binaries are missing"

echo "== Warm npm cache =="
npm cache add @playwright/mcp@0.0.85 >/dev/null 2>&1 || true
npm cache add @redocly/cli@2.78.0 >/dev/null 2>&1 || true
npm cache add @playwright/test@1.65.1 >/dev/null 2>&1 || true

echo "== GitHub CLI =="
command -v gh >/dev/null && gh --version | head -1 || echo "WARNING: gh is missing"

echo "Setup done."
