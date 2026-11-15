#!/usr/bin/env bash
# Stellt JDK 25 bereit und setzt JAVA_HOME. Idempotent.
#
# Verwendung (in der aktuellen Shell, damit JAVA_HOME gilt):
#   source scripts/java.sh
#
# In Claude-Cloud-Sitzungen ist standardmässig nur JDK 21 installiert. JDK 25 kommt
# aus dem Ubuntu-Paket openjdk-25-jdk-headless (Ubuntu-Archiv ist in der Allowlist).
# Lokal: JDK 25 selbst installieren, dann tut dieses Skript nichts.

_java25_home() {
  for d in /usr/lib/jvm/java-25-openjdk-amd64 /usr/lib/jvm/java-25-openjdk-arm64 /usr/lib/jvm/temurin-25*; do
    [ -x "$d/bin/java" ] && { echo "$d"; return 0; }
  done
  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "25'; then
    dirname "$(dirname "$(readlink -f "$(command -v java)")")"; return 0
  fi
  return 1
}

if ! JH="$(_java25_home)"; then
  echo "JDK 25 fehlt, installiere openjdk-25-jdk-headless …"
  SUDO=""; [ "$(id -u)" != "0" ] && SUDO="sudo"
  $SUDO apt-get update -qq >/dev/null 2>&1 || true
  $SUDO apt-get install -y -qq openjdk-25-jdk-headless >/dev/null 2>&1 \
    || { echo "Installation von JDK 25 fehlgeschlagen." >&2; return 1 2>/dev/null || exit 1; }
  JH="$(_java25_home)" || { echo "JDK 25 nach Installation nicht gefunden." >&2; return 1 2>/dev/null || exit 1; }
fi

export JAVA_HOME="$JH"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | grep -v Picked | head -1
