Closes #

## Was

<!-- Ein, zwei Sätze: was kann man nach diesem PR? -->

**Feature:** F-xx · **Anforderungen:** 

## Akzeptanzkriterien

| Nr. | Kriterium | Umsetzung | Test |
|---|---|---|---|
| 1 | | | `e2e/tests/…` |

## Annahmen

<!-- Was nicht eindeutig war und wie entschieden wurde. Neue offene Fragen auch in docs/offene-punkte.md. -->

- 

## Ausprobieren

```bash
scripts/start.sh
```

http://localhost:4200/dev-login → als `…` anmelden → …

## Definition of Done

<!-- docs/pipeline/definition-of-done.md – abhaken oder begründen -->

- [ ] Jedes Akzeptanzkriterium umgesetzt und automatisch getestet
- [ ] API entspricht docs/api/openapi.yaml, Lint ohne Fehler und Warnungen
- [ ] Rechte im Backend geprüft, mit 403-Tests
- [ ] Testbarkeit nach docs/testbarkeit.md, navigation.md nachgeführt
- [ ] Design-Tokens, Hell und Dunkel geprüft, mobil und Desktop geprüft
- [ ] Backend-Tests und E2E-Tests (mobil, desktop, axe) grün
- [ ] testdaten.md und zustaende.md nachgeführt
- [ ] Startet aus frischem Klon
