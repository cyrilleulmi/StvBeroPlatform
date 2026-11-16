# Testplan Nachbesserung #26 · Abmelden ist idempotent

Issue #26 · PR #60 · Regel «Abmelden» in `docs/zustaende.md` (F-00)

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, Cookies leeren, anmelden über `/dev-login` (Klick auf die Person)
  oder `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }`. Uhr: Standard, spielt keine Rolle.
- «Ohne Saison»: zusätzlich `DELETE /api/test/aktuelle-saison`, danach `/teams` → leitet auf `/keine-saison`.
- Gezählt werden alle Antworten von `POST /api/abmelden` (Status) und alle Antworten ≥ 500.
- Nach jedem Fall: Konsolenfehler und `pageerror` prüfen. Erlaubt ist nur der Browser-Eintrag
  «Failed to load resource … 409» von `GET /api/ich` ohne aktuelle Saison (Nachbesserung #13).
- Ansichten: mobil 375×812 (alle Fälle), Desktop 1280×800 (K2.1–K2.6). Darkmode einmal mobil (K2.7).

## K1 · `POST /api/abmelden` ist idempotent (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Nacheinander | Als `anna` anmelden, zweimal `POST /api/abmelden` mit demselben Cookie | Beide 204, beide `Set-Cookie: stv_sitzung=;…Max-Age=0`. Danach `GET /api/ich` → 401 |
| K1.2 Gleichzeitig | Als `anna` anmelden, 20 gleichzeitige `POST /api/abmelden` mit demselben Cookie | Alle 204, kein 500, kein `OptimisticLockException` im Backend-Log. Danach `GET /api/ich` → 401 |
| K1.3 Nur eigene Sitzung | Zwei Sitzungen `anna` (A, B) und eine `petra`; mit A zweimal abmelden | `GET /api/ich`: A → 401, B → 200, petra → 200 |
| K1.4 Ungültiges Cookie | `POST /api/abmelden` mit leerem Cookie, mit 3000 Zeichen langem Token, mit `x' OR '1'='1` | Jeweils 204. Andere Sitzungen bleiben gültig |

## K2 · Abmelden in der Oberfläche (Kriterium 2)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K2.1 Profil Doppelklick | Als `anna` `/profil`, Doppelklick «Abmelden» | `/dev-login`, genau 1 `POST /api/abmelden` (204), keine 5xx, keine Konsolenfehler |
| K2.2 Profil schnell zweimal | Wie K2.1, Klick und sofort zweiter Klick (erzwungen) | Wie K2.1 |
| K2.3 Profil zwei Tabs | Als `anna` `/profil` in zwei Tabs, in beiden gleichzeitig «Abmelden» | Beide Tabs `/dev-login`, 2 × 204, keine 5xx, keine Konsolenfehler in beiden Tabs |
| K2.4 Ohne Saison Doppelklick | Ohne Saison, als `anna` `/teams` → `/keine-saison`, Doppelklick «Abmelden» | Wie K2.1 (409 von `/api/ich` erlaubt) |
| K2.5 Ohne Saison schnell | Wie K2.4 mit zwei schnellen Klicks | Wie K2.4 |
| K2.6 Ohne Saison zwei Tabs | Wie K2.3, auf `/keine-saison` | Wie K2.3 (409 von `/api/ich` erlaubt) |
| K2.7 Darkmode | K2.1 und K2.4 mit `prefers-color-scheme: dark`, Bildschirmfoto nach dem Abmelden | `/dev-login` lesbar, nichts Weisses auf Weiss |
| K2.8 Neuladen, Zurück | Nach K2.1–K2.6 neu laden, dann Zurück-Taste | Bleibt bzw. landet auf `/dev-login`, `GET /api/sitzung` → `angemeldet: false` |
| K2.9 Rollen | K2.1 als `alex` und `petra`, K2.4 als `alex` | Wie K2.1 (Abmelden ist für alle gleich, öffentlicher Endpunkt) |

## Ergebnis (Runde 0, PR #60, Stand 101e672)

- Automatisch: Backend 267 ✅ (inkl. `AnmeldungTest` gleichzeitig/zweimal), E2E 286 ✅ (6 übersprungen wie bisher).
- K1.1–K1.4 bestanden: 20 × 204 gleichzeitig, danach 401; nur die eigene Sitzung gelöscht; ungültige Cookies 204.
- K2.1–K2.9 bestanden mobil und Desktop: immer `/dev-login`, keine 5xx, keine Konsolenfehler ausser dem erlaubten 409 von `/api/ich`.
