# Definition of Done

Ein PR ist fertig, wenn alle Punkte erfüllt sind. Der Entwicklungs-Agent hakt sie in der PR-Beschreibung ab, der Review-Agent prüft sie, der Test-Agent prüft das Verhalten.

## Fachlich

- [ ] Jedes Akzeptanzkriterium des Issues ist umgesetzt und hat mindestens einen automatischen Test.
- [ ] Die betroffenen Anforderungs-IDs sind im PR genannt.
- [ ] Getroffene Annahmen sind im PR unter «Annahmen» aufgelistet. Neue offene Fragen stehen in [offene-punkte.md](../offene-punkte.md).
- [ ] Keine Anforderung mit Status «Entschieden» wurde geändert oder umgangen.
- [ ] Begriffe aus dem [Fachmodell](../fachmodell.md) in Code, API und Oberfläche.

## Backend

- [ ] API entspricht [openapi.yaml](../api/openapi.yaml). Änderungen am Vertrag sind im selben PR, `redocly lint` ist ohne Fehler und Warnungen.
- [ ] Rechte werden im Backend geprüft (RR-07), mit Test für den verbotenen Fall (403).
- [ ] Fachregeln liegen in der Domänenlogik, nicht im REST-Layer, und haben Unit-Tests.
- [ ] Schema-Änderungen als neue Flyway-Migration. Bestehende Migrationen sind unverändert.
- [ ] Test-Endpunkte nur in den Profilen `dev`/`test`.
- [ ] «Jetzt» kommt aus der injizierten Uhr, nie aus `LocalDateTime.now()` direkt.

## Frontend

- [ ] Regeln aus [testbarkeit.md](../testbarkeit.md) eingehalten: Semantik, Namen, Zustände, Test-IDs, URLs.
- [ ] Neue Routen und feste Test-IDs in [navigation.md](../oberflaeche/navigation.md) eingetragen.
- [ ] Design nach [design-grundlagen.md](../design-grundlagen.md): nur Tokens, keine fest codierten Farben. Light- und Darkmode geprüft.
- [ ] Mobil (375px) und Desktop (1280px) geprüft, Touch-Ziele mindestens 44px.
- [ ] Keine Fehler in der Browser-Konsole.

## Tests und Daten

- [ ] Backend-Tests grün.
- [ ] E2E-Tests (`e2e/`) grün in den Projekten `mobil` und `desktop`, inklusive axe-Check ohne kritische Befunde.
- [ ] Seed-Daten decken das Feature ab. Änderungen am Seed sind in [testdaten.md](../testdaten.md) nachgeführt, neue Szenarien dort ergänzt.
- [ ] Geändertes Verhalten ist in [zustaende.md](../zustaende.md) nachgeführt.

## Lauffähigkeit

- [ ] Die App startet mit dem dokumentierten Startbefehl aus einem frischen Klon.
- [ ] Die PR-Beschreibung nennt, wie man das Feature ausprobiert (Startbefehl, URL, Testperson).
