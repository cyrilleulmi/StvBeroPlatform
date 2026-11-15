# API

Der Vertrag steht in [openapi.yaml](openapi.yaml) (OpenAPI 3.1, 50 Operationen). Er beschreibt das Zielbild für den Kern.

## Arbeitsweise: Contract first

1. Ändert ein Feature die API, wird **zuerst** `openapi.yaml` angepasst, im selben PR wie der Code.
2. Das Backend setzt den Vertrag um. Quarkus (`quarkus-smallrye-openapi`) erzeugt daraus zur Laufzeit `/q/openapi`.
3. Ein Test im Backend vergleicht die erzeugte Spezifikation mit `openapi.yaml` (Pfade, Methoden, Statuscodes, Schemas). Abweichungen lassen den Build fehlschlagen. Der Test kommt mit F-00.
4. Das Frontend erzeugt seine TypeScript-Typen aus `openapi.yaml` (z.B. mit `openapi-typescript`), statt sie von Hand zu schreiben.

Prüfen:

```bash
npx -y @redocly/cli lint docs/api/openapi.yaml        # 0 Fehler, 0 Warnungen
npx -y @redocly/cli preview-docs docs/api/openapi.yaml # lesbare Doku im Browser
```

Lokal laufend: Swagger UI unter `http://localhost:8080/q/swagger-ui`.

## Konventionen

| Thema | Regel |
|---|---|
| Sprache | Pfade, Felder und Enum-Werte auf Deutsch, aus dem [Fachmodell](../fachmodell.md). Umlaute umschreiben (`zugehoerigkeiten`, `veroeffentlichen`). |
| Pfade | Kleinbuchstaben, Mehrzahl für Sammlungen (`/api/termine`), Aktionen als Verb-Unterpfad (`/absagen`, `/veroeffentlichen`). |
| Felder | `camelCase`. Enum-Werte `GROSS_MIT_UNTERSTRICH`. |
| IDs | UUID. Seed-IDs sind fest, siehe [testdaten.md](../testdaten.md#id-schema). |
| Zeit | ISO 8601 mit Offset (`2026-11-17T18:30:00+01:00`). Zeitzone der Fachlogik: Europe/Zurich. Daten ohne Zeit als `date`. |
| «Jetzt» | Kommt immer von der Backend-Uhr (im Testprofil festsetzbar), nie vom Browser. |
| Fehler | RFC 9457 Problem Details (`application/problem+json`). `detail` ist ein deutscher Satz zum direkten Anzeigen, `regel` nennt die Anforderungs-ID. |
| Statuscodes | 400 formal ungültig · 401 nicht angemeldet · 403 keine Berechtigung · 404 nicht gefunden · 409 widerspricht dem Zustand · 422 fachliche Regel verletzt · 502 swiss unihockey gestört |
| Rechte | Werden im Backend geprüft (RR-07). Die Oberfläche blendet nur aus, was die API ohnehin verweigert. Hilfsfeld: `darfBearbeiten`. |
| Sichtbarkeit | Daten, die jemand nicht sehen darf (Kommentare, ANM-07), fehlen in der Antwort. Sie werden nicht nur im Frontend versteckt. |
| Sortierung | Die API liefert in Anzeigereihenfolge. Das Frontend sortiert nicht um. |
| Anmeldung | Sitzungs-Cookie `stv_sitzung`. Das produktive Verfahren ist offen (O-01). |

## Test-Endpunkte

Alles unter `/api/test/` existiert nur in den Profilen `dev` und `test`. Im Profil `prod` liefern diese Pfade 404, weil die Ressourcen nicht registriert werden (z.B. per `@IfBuildProfile`). Zweck und Gebrauch: [testbarkeit.md](../testbarkeit.md#test-hilfen-der-app).

## Annahmen im Vertrag

Diese Punkte stehen nicht ausdrücklich in den Anforderungen und sind im Vertrag als Annahme umgesetzt:

| Punkt | Annahme |
|---|---|
| Benachrichtigungen | Es gibt immer eine Liste in der App (`/api/benachrichtigungen`). E-Mail oder Push kommen als zusätzlicher Kanal dazu (BEN-10, BEN-11). |
| Spiel bei swiss unihockey verschwunden | Wird markiert (`beiSuNichtMehrVorhanden`), nicht gelöscht. Die Coach entscheidet. |
| Funktion wechseln | Wird eine Spielerin zur Coach, verliert sie ihre Linienzuweisung in diesem Team. |
| Kurzfristige Absage | Benachrichtigt werden die Coaches der Teams, in denen die Person beim Termin Spielerin ist (O-17). |
