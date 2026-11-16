# 06 · Anbindung swiss unihockey

Spieltage sollen wenn möglich mit den Spieldaten von swiss unihockey gefüllt werden, statt sie von Hand zu erfassen.

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Anforderungen

| ID | Anforderung | Status |
|---|---|---|
| SUH-01 | Wenn möglich werden die Spieldaten von swiss unihockey bezogen. | Entschieden |
| SUH-02 | Ein Admin verknüpft jedes Team einmal mit dem entsprechenden Team bei swiss unihockey. | Angenommen |
| SUH-03 | Die Spiele der Saison werden importiert und täglich abgeglichen. | Angenommen |
| SUH-04 | Spiele desselben Teams am selben Tag in derselben Halle werden zu einem Spieltag zusammengefasst (Turnierform, siehe [05](05-spieltage-aufgebot.md)). | Annahme |
| SUH-05 | Ändert sich ein importiertes Spiel (Datum, Zeit, Ort), wird der Spieltag angepasst und die Betroffenen werden benachrichtigt. | Angenommen |
| SUH-06 | Treffpunkt, Hinweise, Antworten und Aufgebot sind eigene Daten. Der Abgleich überschreibt sie nie. | Angenommen |
| SUH-07 | Der Google-Maps-Link des Spielorts wird bei importierten Spieltagen automatisch gesetzt. | Angenommen |
| SUH-08 | Resultate werden nach dem Spiel übernommen, sofern sie geliefert werden. | Annahme |
| SUH-09 | Ist die Schnittstelle nicht erreichbar oder liefert sie unerwartete Daten, funktioniert die App normal weiter. Der Abgleich wird später wiederholt. | Annahme |
| SUH-10 | Von Hand erfasste Spieltage bleiben vom Abgleich unberührt. | Angenommen |

## Was über die Schnittstelle bekannt ist

Stand 15.11.2026, aus der öffentlichen Dokumentation gelesen. Noch nichts davon ist gegen die echte Schnittstelle getestet.

- Basis-URL: `https://api-v2.swissunihockey.ch/api`
- Laut Übersicht sind unter anderem diese Endpunkte öffentlich: `/api/games`, `/api/teams`, `/api/clubs`, `/api/calendars`, `/api/seasons`.
- Teams eines Clubs: `GET /api/teams?mode=by_club&season=…&club_id=…`
- Spiele eines Teams: `GET /api/games?mode=team&team_id=…&season=…`
- Spiele eines Clubs: `GET /api/games?mode=club&club_id=…&season=…`
- Die Spielliste enthält eine Spalte mit der Halle samt Google-Maps-Link.
- Das Spieldetail liefert Heim- und Gastteam, Resultat, Datum, Anspielzeit und die Koordinaten des Spielorts.
- Kalender eines Teams als iCalendar: `GET /api/calendars?team_id=…`
- Die Antworten sind als generische Tabellen aufgebaut (Zeilen und Zellen), nicht als fachliche Objekte. Die Auswertung braucht deshalb eine eigene Übersetzungsschicht.

Dokumentation:

- Übersicht: https://api-v2.swissunihockey.ch/api/doc/table/overview
- Games: https://api-v2.swissunihockey.ch/api/games/doc
- Teams: https://api-v2.swissunihockey.ch/api/teams/doc
- Calendars: https://api-v2.swissunihockey.ch/api/calendars/doc

## Stand nach F-07

- Im Profil `dev`/`test` spricht die App mit einem Simulator ([testdaten.md](../testdaten.md#simulierte-swiss-unihockey-daten)), im Profil `prod` mit der Adresse aus `STV_SU_URL` (Standard `https://api-v2.swissunihockey.ch/api`).
- Laut öffentlichen Clients der Schnittstelle (Stand Herbst 2026) verlangt `api-v2` inzwischen Zugangsdaten. Frei ist `https://wc.swissunihockey.ch` mit demselben Antwortformat, siehe [O-20 und O-24](../offene-punkte.md#swiss-unihockey).
- Spalten der Spielliste und die Gruppierung von Turnieren: [O-21](../offene-punkte.md#swiss-unihockey).
- Der Abgleich ist in [zustaende.md](../zustaende.md#spiel-aus-swiss-unihockey) beschrieben.

## Noch zu prüfen

| Punkt | Warum |
|---|---|
| Braucht es einen Schlüssel oder eine Registrierung? | Die Dokumentation sagt dazu nichts. Einzelne Ressourcen sind laut Dokumentation nur für registrierte Partner. |
| Welche Felder liefert eine Zeile der Spielliste genau? | Die Dokumentation listet sie für die Modi `team` und `club` nicht auf. |
| Wie sind Spieltage in Turnierform abgebildet? | Entscheidet, wie zuverlässig SUH-04 gruppieren kann. |
| Club-ID des STV Beromünster und Team-IDs | Nötig für die Verknüpfung (SUH-02). |
| Nutzungsbedingungen | Klären, ob der regelmässige Abruf für eine Vereins-App erlaubt ist. |
| Gibt es eine neuere Schnittstelle? | Ein unabhängiges WordPress-Plugin erwähnt die Hosts `wc.swissunihockey.ch` (frei) und `office.swissunihockey.ch` (Partner). Laut weiteren Clients nutzen die offiziellen Web-Komponenten `wc.swissunihockey.ch`. Nicht live geprüft (O-24). |
