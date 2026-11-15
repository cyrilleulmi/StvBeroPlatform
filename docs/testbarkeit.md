# Testbarkeit des Frontends

Der Test-Agent der [Pipeline](pipeline/README.md) bedient die App wie ein Mensch, aber über den **Accessibility-Tree** des Browsers (Playwright MCP). Er sieht keine Pixel, sondern Rollen, Namen und Zustände. Was dort nicht auftaucht, kann er weder finden noch prüfen.

Diese Regeln sind für jedes Feature verbindlich. Der Review-Agent prüft sie, der Test-Agent ist auf sie angewiesen.

## Warum so

- Playwright MCP übergibt dem Agenten einen Text-Schnappschuss des Accessibility-Trees. Klicks und Eingaben laufen über Referenzen aus diesem Schnappschuss, nicht über Koordinaten.
- Playwright empfiehlt für Locators die Reihenfolge **Rolle → Label → Text → Test-ID → CSS** (letzter Ausweg).
- Reine Layout-`div`s fallen im Accessibility-Tree weg oder erscheinen als namenlose `generic`-Knoten, auch wenn sie eine `data-testid` haben. Gruppen müssen deshalb semantisch ausgezeichnet sein.
- Gute Barrierefreiheit und gute Testbarkeit sind dasselbe. Was ein Screenreader versteht, versteht auch der Agent.

## Regeln

### 1. Semantik zuerst

| Regel | Beispiel |
|---|---|
| Landmarks verwenden: `header`, `nav`, `main`, `footer`. Pro Seite genau ein `main`. | `<nav aria-label="Hauptnavigation">` |
| Jede Seite hat genau eine `h1`, die dem Seitentitel entspricht. | `<h1>Training Di 18:30</h1>` |
| Gruppen sind `section` mit Überschrift oder `aria-labelledby`. | `<section aria-labelledby="team-1-titel">` |
| Listen sind echte Listen (`ul`/`ol`/`li`) oder haben `role="list"` mit Namen. | `<ul aria-label="Linie 1">` |
| Tabellen nur für tabellarische Daten, mit `th` und `scope`. | Saisonstatistik |
| Klickbares ist ein `button` oder `a`, nie ein `div` mit Click-Handler. | – |
| Links führen zu einer URL, Buttons lösen eine Aktion aus. | – |

### 2. Jedes Bedienelement hat einen Namen

- Buttons haben sichtbaren Text. Icon-Buttons haben `aria-label` (z.B. «Menü öffnen»).
- Eingabefelder haben ein `<label>`. Ein Platzhalter ist kein Label.
- Gleiche Aktionen in Listen werden über ihren Container unterschieden, nicht über den Namen. Der Container hat einen Namen (z.B. `li` mit `aria-label="Anna Muster"`), oder der Button-Name enthält den Kontext («Anna aufbieten»).
- Texte kommen aus einer zentralen Stelle (Konstanten oder i18n-Datei), damit Tests und Doku dieselben Wörter verwenden.

### 3. Zustände sind maschinenlesbar, nicht nur farbig

Farbe allein trägt keine Information (siehe auch [design-grundlagen.md](design-grundlagen.md#noch-nicht-festgelegt)).

| Zustand | Auszeichnung |
|---|---|
| Gewählte Antwort | Antwort-Buttons als Gruppe mit `role="radiogroup"` bzw. `aria-pressed="true"` |
| Antwort einer Person in Listen | Sichtbarer Text oder `aria-label` («dabei», «nicht dabei», «vielleicht», «offen») **und** `data-antwort="DABEI"` |
| Aktiver Navigationspunkt | `aria-current="page"` |
| Aufgeboten | Checkbox mit `aria-checked` bzw. Text «aufgeboten» |
| Laden | Container mit `aria-busy="true"`, danach entfernt |
| Deaktiviert | `disabled` bzw. `aria-disabled="true"` mit Erklärung in der Nähe |
| Fehler im Formular | `aria-invalid="true"` und Fehlermeldung über `aria-describedby` |

Zähler (dabei/nicht dabei/vielleicht/offen) haben zusätzlich `data-zaehler="dabei"` usw. und einen Text, der die Zahl und das Wort enthält («10 dabei»).

### 4. Test-IDs dort, wo Semantik nicht reicht

`data-testid` ist die Ergänzung, nicht der Ersatz. Gesetzt wird es auf:

- wiederholte Container, die eine Entität darstellen (Karten, Listeneinträge),
- Gruppierungen der Übersicht (Team, Linie, Coach-Block),
- Zähler und Status-Anzeigen,
- alles, was in [oberflaeche/navigation.md](oberflaeche/navigation.md#test-ids) als feste Test-ID aufgeführt ist.

**Namensschema:** `kebab-case`, deutsch, `<bereich>-<element>[-<schlüssel>]`.

| Muster | Beispiel |
|---|---|
| Entität mit ID | `termin-karte-40000000-0000-4000-8000-000000000001` |
| Entität mit fachlichem Schlüssel | `uebersicht-team-team-1`, `uebersicht-gruppe-linie-1` |
| Einmaliges Element | `meine-antwort`, `coach-block`, `aufgebot-veroeffentlichen` |

Test-IDs sind Teil der Schnittstelle: Wer eine umbenennt, passt Tests und [navigation.md](oberflaeche/navigation.md) im selben PR an.

### 5. Jeder Zustand hat eine URL

- Jeder Screen ist direkt per URL erreichbar (Deep Link), auch nach einem Neuladen.
- Filter und Tabs stehen in der URL (`/termine?team=…&ansicht=vergangen`).
- Dialoge, die zu einem Screen gehören, dürfen ohne URL sein. Eigenständige Formulare (Termin erfassen, Aufgebot) haben eine eigene Route.

Der Test-Agent springt so direkt an die richtige Stelle, statt sich durchzuklicken.

### 6. Rückmeldungen sind auffindbar

- Erfolgsmeldungen (Toasts) haben `role="status"`, Fehlermeldungen `role="alert"`. Sie bleiben mindestens 5 Sekunden sichtbar und schliessen sich nicht beim ersten Fokuswechsel.
- Dialoge sind `role="dialog"` (bzw. `<dialog>`) mit `aria-modal="true"` und Namen über die Überschrift.
- Leere Zustände haben einen Text («Keine kommenden Termine»), nie eine leere Fläche.
- Fehler vom Backend werden sichtbar angezeigt und nicht nur in die Konsole geschrieben.

### 7. Bedienbar ohne Hover und ohne Drag & Drop

- Keine Aktion ist nur per Hover erreichbar.
- Drag & Drop (Linien) hat immer eine gleichwertige Alternative mit Buttons oder Auswahlfeld («Linie wählen»).
- Alles ist per Tastatur erreichbar, mit sichtbarem Fokus.

### 8. Deterministisch

- Listen haben eine feste Sortierung (z.B. Personen nach Nachname, dann Vorname).
- Animationen respektieren `prefers-reduced-motion` und sind dann aus. Der Test-Agent setzt reduzierte Bewegung.
- Zeitabhängige Texte («in 2 Tagen») leiten sich von der Backend-Uhr ab, die im Testprofil festgesetzt werden kann (siehe unten).
- Keine zufälligen IDs im DOM, die sich bei jedem Rendern ändern.

## Test-Hilfen der App

Diese Hilfen gibt es nur in den Quarkus-Profilen `dev` und `test`. Im Profil `prod` existieren die Endpunkte nicht (nicht nur versteckt). Der Review-Agent prüft das.

| Hilfe | Zweck | Schnittstelle |
|---|---|---|
| **Seed zurücksetzen** | Datenbank in den definierten Ausgangszustand bringen | `POST /api/test/reset` |
| **Feste Uhr** | «Jetzt» festlegen, um Antwortfristen, kurzfristige Absagen und Erinnerungen zu prüfen | `PUT /api/test/uhr`, `DELETE /api/test/uhr` |
| **Ohne aktuelle Saison** | Zustand «keine Saison als aktuell festgelegt» herstellen (Hinweis `/keine-saison`). Reset stellt den Seed wieder her | `DELETE /api/test/aktuelle-saison` |
| **Anmelden als** | Ohne Passwort als beliebige Seed-Person anmelden | `POST /api/test/anmelden`, Seite `/dev-login` |
| **Benachrichtigungen mitlesen** | Prüfen, wer wann worüber benachrichtigt wurde | `GET /api/test/benachrichtigungen` |
| **swiss unihockey simulieren** | Spieldaten vorgeben, die der Abgleich «von swiss unihockey» erhält | `PUT /api/test/swiss-unihockey/spiele` |

Details im API-Vertrag: [api/openapi.yaml](api/openapi.yaml) (Tag «Test»). Die Seed-Daten: [testdaten.md](testdaten.md).

Die Seite `/dev-login` listet alle Seed-Personen mit Funktion und Team. Ein Klick meldet an. Sie zeigt oben deutlich «Testmodus».

## Werkzeuge

| Werkzeug | Wofür | Wo |
|---|---|---|
| Playwright Test | Automatische End-to-End-Tests, laufen in CI und bei jedem Agenten | `e2e/` |
| Playwright MCP | Explorative Tests durch den Test-Agenten | Konfiguration in [.mcp.json](../.mcp.json) |
| axe-core (`@axe-core/playwright`) | Automatischer Barrierefreiheits-Check pro Screen in den E2E-Tests | `e2e/` |

Konventionen für E2E-Tests:

- Locators in der Reihenfolge `getByRole` → `getByLabel` → `getByText` → `getByTestId`. Keine CSS- oder XPath-Selektoren.
- Kein `waitForTimeout`. Auf Zustände warten (`expect(...).toBeVisible()` usw.).
- Jeder Test beginnt mit `POST /api/test/reset` und meldet sich über `POST /api/test/anmelden` an.
- Jeder Test läuft in zwei Projekten: `mobil` (375×812, Touch) und `desktop` (1280×800).
- Der Testname nennt die Anforderungs-ID: `test('ANM-04: vielleicht verlangt einen Kommentar', …)`.
- `e2e/tests/seed.spec.ts` ist der Einstiegstest (Reset, Anmeldung, Dashboard sichtbar). Er dient auch als Vorlage, wenn Tests mit den Playwright-Test-Agenten (Planner/Generator/Healer) erzeugt werden.
