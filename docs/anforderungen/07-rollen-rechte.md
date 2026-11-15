# 07 · Rollen und Rechte

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Anforderungen

| ID | Anforderung | Status |
|---|---|---|
| RR-01 | Die Rechte einer Person ergeben sich aus ihrer Funktion im jeweiligen Team. Wer in Team 1 Coach und in Team 2 Spielerin ist, hat in Team 1 Coach-Rechte und in Team 2 die Rechte einer Spielerin. | Angenommen |
| RR-02 | Eine Spielerin sieht ihre Teams und deren Termine und antwortet für sich selbst. | Angenommen |
| RR-03 | Eine Coach bearbeitet Termine und Linien ihres Teams, antwortet stellvertretend, sieht Kommentare und erstellt das Aufgebot. | Angenommen |
| RR-04 | Admins dürfen alles, über alle Teams hinweg. Admin ist keine Funktion in einem Team, sondern eine Eigenschaft der Person. | Angenommen |
| RR-05 | Kacheln und Aktionen sind nur für die Rollen sichtbar, die sie nutzen dürfen. | Entschieden |
| RR-06 | Bei einem Termin mit mehreren Teams darf die Coach eines beteiligten Teams den Termin bearbeiten. Stellvertretend antworten und Kommentare sehen darf sie nur für ihr eigenes Team. | Annahme |
| RR-07 | Die Rechte werden im Backend durchgesetzt, nicht nur in der Oberfläche ausgeblendet. | Annahme |

## Übersicht

| Aktion | Spielerin | Coach (eigenes Team) | Admin |
|---|---|---|---|
| Eigene Termine sehen | ✓ | ✓ | ✓ |
| Für sich antworten | ✓ | ✓ | ✓ |
| Status der anderen im Termin sehen | ✓ | ✓ | ✓ |
| Kommentare und Gründe anderer sehen | – | ✓ | ✓ |
| Stellvertretend antworten | – | ✓ | ✓ |
| Termine und Serien erfassen, bearbeiten, absagen | – | ✓ | ✓ |
| Linien verwalten | – | ✓ | ✓ |
| Aufgebot erstellen und veröffentlichen | – | ✓ | ✓ |
| Personen, Teams, Teamzugehörigkeiten verwalten | – | – | ✓ |
| Team mit swiss unihockey verknüpfen | – | – | ✓ |
| Saisons verwalten | – | – | ✓ |

## Hinweis zu den Rollen der Plattform

Für die ganze Plattform sind mindestens die Rollen Spielerin, Coach, Helfer/Eltern und Admin vorgesehen. Helfer und Eltern spielen im Kern keine Rolle. Sie werden mit der Helfereinsatzplanung bzw. den Elternfunktionen relevant.
