# Hinweise für KI-Coding-Agenten

Dieses Repo wird mit Unterstützung von KI-Agenten entwickelt. Diese Datei sagt, wo die Wahrheit steht und wie gearbeitet wird.

## Zuerst lesen

1. [README.md](README.md) – Überblick und Status-Legende.
2. [docs/fachmodell.md](docs/fachmodell.md) – Begriffe. Verwende im Code, in der API und in der Oberfläche dieselben Begriffe.
3. [docs/anforderungen/](docs/anforderungen/) – die Anforderungen mit IDs (z.B. `ANM-03`).
4. [docs/design-grundlagen.md](docs/design-grundlagen.md) – verbindliches Design.
5. [docs/technik.md](docs/technik.md) – Technologie und Leitplanken.

## Stand

Es ist noch nichts implementiert. Mit der Umsetzung wird erst begonnen, wenn Cyrille das ausdrücklich verlangt.

## Umgang mit Anforderungen

- Der Status entscheidet über die Verbindlichkeit (Legende im README).
- **Entschieden** und **Angenommen**: umsetzen wie beschrieben.
- **Annahme**: umsetzen, aber beim Ergebnis nennen, dass es eine Annahme ist.
- **Offen**: sinnvolle Annahme treffen, beim Ergebnis nennen und in [docs/offene-punkte.md](docs/offene-punkte.md) festhalten.
- **Später**: nicht umsetzen. Das Modell aber so bauen, dass es später ohne Umbau geht.
- Verweise in Commits, Tests und Pull Requests auf die Anforderungs-IDs.
- Ändert sich eine Anforderung, wird zuerst das Dokument angepasst und der Entscheid in [docs/entscheide.md](docs/entscheide.md) eingetragen.

## Arbeitsweise

- Pläne werden nur gemeinsam erarbeitet, wenn das ausdrücklich gewünscht ist.
- Sonst: sinnvolle Annahmen treffen und vollständig umsetzen, ohne Rückfragen oder Zwischenstopps. Keine kleinen Zwischenschritte.
- Getroffene Annahmen beim Ergebnis kurz auflisten.
- Jedes Ergebnis ist lauffähig und direkt aufrufbar (URL bzw. Startbefehl), auf Desktop und Mobile geprüft, mit Seed-Daten zum Ausprobieren.
- Tests für Backend-Logik.

## Sprache

- Oberfläche: Deutsch (Schweiz, also «ss» statt «ß»), klare Sprache, keine Fachbegriffe.
- In den Anforderungen steht durchgehend «Spielerin» und «Spielerinnen», so wie in der Erarbeitung verwendet. Welche Form die Oberfläche zeigt, ist noch offen (siehe [docs/offene-punkte.md](docs/offene-punkte.md)).
- Dokumentation in diesem Repo: Deutsch.
