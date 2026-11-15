# 01 · Personen, Teams, Funktionen

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Anforderungen

| ID | Anforderung | Status |
|---|---|---|
| PTF-01 | Jede Person gibt es genau einmal, unabhängig davon, in wie vielen Teams sie ist. | Angenommen |
| PTF-02 | Es gibt mehrere Teams. | Entschieden |
| PTF-03 | Eine Person kann mehreren Teams angehören. | Entschieden |
| PTF-04 | Pro Team hat eine Person genau eine Funktion. Über Teams hinweg sind die Funktionen frei kombinierbar, z.B. Team 1 Coach und Team 2 Spielerin. | Entschieden |
| PTF-05 | Es gibt mindestens die Funktionen «Spielerin» und «Coach». | Entschieden |
| PTF-06 | Weitere Funktionen (z.B. Assistenzcoach, Betreuerin) sollen später ergänzt werden können, ohne das Modell umzubauen. Welche es braucht, ist nicht festgelegt. | Offen |
| PTF-07 | Coaches werden nirgends als Spielerinnen angezeigt und zählen nie zum Spielerinnen-Total. | Entschieden |
| PTF-08 | Die App ist momentan für Erwachsene gedacht. Jede Person antwortet für sich selbst. | Entschieden |
| PTF-09 | Konto (Login) und Person sind getrennt modelliert, damit später ein Konto für mehrere Personen antworten kann (Eltern für Kinder). Heute gehört zu einem Konto genau eine Person. | Angenommen |
| PTF-10 | Teamzugehörigkeiten gelten pro Saison (siehe [09 · Saisons](09-saisons.md)). | Angenommen |
| PTF-11 | Admins verwalten Personen, Teams und Teamzugehörigkeiten. | Annahme |
| PTF-12 | Eine Person sieht alle Teams, denen sie angehört, und ihre Funktion darin. | Annahme |

## Beispiel

| Person | Team 1 | Team 2 |
|---|---|---|
| Anna | Spielerin | – |
| Petra | Coach | Spielerin |
| Lea | Spielerin | Spielerin |

Petra erscheint in Team 1 im Coach-Block und in Team 2 unter den Spielerinnen. Lea erscheint in beiden Teams unter den Spielerinnen und kann in jedem Team eine andere Linie haben.

## Nicht Teil des Kerns

- Elternfunktionen: siehe [Spätere Funktionen](../spaetere-funktionen.md).
- Gäste und Probetrainings: siehe [Spätere Funktionen](../spaetere-funktionen.md).
