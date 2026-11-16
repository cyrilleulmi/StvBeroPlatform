# Antworten von swiss unihockey für Tests

Antworten der Spielliste `GET /games?mode=team&team_id=…&season=…`, wie sie `SuApi` liest. Die Tests spielen sie
über einen lokalen HTTP-Server ab; die echte Schnittstelle wird in Tests nie aufgerufen (F-07, Kriterium 2).

**Herkunft:** Aus der Cloud-Umgebung war `api-v2.swissunihockey.ch` beim Bau von F-07 nicht erreichbar (Proxy
gesperrt). Die Dateien sind deshalb nicht live mitgeschnitten, sondern nach dem Format nachgebaut, das die
Dokumentation und mehrere öffentliche Clients der Schnittstelle zeigen (Tabelle `data.regions[].rows[].cells[].text[]`,
Spiel-ID in `rows[].link.ids[0]`, Spalten Datum/Zeit, Ort, Heimteam, Gastteam, Resultat). Team- und Spiel-IDs sind
erfunden. Sobald die Schnittstelle erreichbar ist, eine echte Antwort hier ablegen und den Test darauf richten
(O-21 in docs/offene-punkte.md).

| Datei | Inhalt |
|---|---|
| `games-team.json` | Normale Spielliste: Turnier mit zwei Spielen in derselben Halle, ein Auswärtsspiel mit Resultat, Zwischenzeile ohne Spiel |
| `games-relativ.json` | Relative Datumsangaben «heute» und «morgen», wie sie swiss unihockey um den aktuellen Tag liefert |
| `games-unerwartet.json` | Fehlermeldung statt Tabelle, wie bei fehlendem Zugang (SUH-09) |
