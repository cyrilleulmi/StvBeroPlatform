# StvBeroPlatform

Zentrale Web-Plattform für das Unihockey des STV Beromünster. Sie bündelt die organisatorischen Werkzeuge, die heute verstreut sind (Listen, Chats, Tabellen), an einem Ort – einfach genug, dass Spielerinnen, Coaches und Helfer sie ohne Anleitung nutzen können.

> **Stand 15.11.2026: Anforderungsphase.** Dieses Repo enthält bisher nur die Anforderungen. Es ist noch nichts implementiert.

## Warum eine eigene App?

Standard-Apps decken zwei Dinge nicht ab, die für uns zentral sind:

1. **Trainings mit mehreren Teams.** Ein Training kann gleichzeitig zu mehreren Teams gehören. Die An- und Abmeldung zeigt die Mitglieder direkt nach Team gruppiert.
2. **Linien.** Spielerinnen sind Linien zugewiesen. Bei An- und Abmeldungen werden sie direkt nach Linie gruppiert angezeigt.

Dazu kommt: Coaches melden sich ebenfalls an und ab, erscheinen aber nicht als Spielerinnen. Und eine Person kann mehreren Teams angehören, mit je einer eigenen Funktion (z.B. Team 1 Coach, Team 2 Spielerin).

## Inhalt

| Dokument | Inhalt |
|---|---|
| [docs/projektkontext.md](docs/projektkontext.md) | Ziel, Grundprinzipien, Kacheln, Arbeitsweise |
| [docs/fachmodell.md](docs/fachmodell.md) | Begriffe und wie sie zusammenhängen |
| [docs/anforderungen/](docs/anforderungen/) | Anforderungen an den Kern, pro Thema eine Datei |
| [docs/weitere-kacheln.md](docs/weitere-kacheln.md) | Geplante Kacheln ausserhalb des Kerns |
| [docs/spaetere-funktionen.md](docs/spaetere-funktionen.md) | Bewusst zurückgestellte und gestrichene Funktionen |
| [docs/design-grundlagen.md](docs/design-grundlagen.md) | Grunddesign «Salbei frisch»: Farben, Formen, Typografie |
| [docs/technik.md](docs/technik.md) | Technologie-Entscheide und Architektur-Leitplanken |
| [docs/offene-punkte.md](docs/offene-punkte.md) | Ungeklärte Fragen |
| [docs/entscheide.md](docs/entscheide.md) | Protokoll der getroffenen Entscheide |
| [AGENTS.md](AGENTS.md) | Hinweise für KI-Coding-Agenten, die in diesem Repo arbeiten |

### Anforderungen an den Kern

| Nr. | Thema | Kürzel |
|---|---|---|
| 01 | [Personen, Teams, Funktionen](docs/anforderungen/01-personen-teams-funktionen.md) | PTF |
| 02 | [Linien](docs/anforderungen/02-linien.md) | LIN |
| 03 | [Termine](docs/anforderungen/03-termine.md) | TER |
| 04 | [An- und Abmeldung](docs/anforderungen/04-an-und-abmeldung.md) | ANM |
| 05 | [Spieltage und Aufgebot](docs/anforderungen/05-spieltage-aufgebot.md) | AUF |
| 06 | [Anbindung swiss unihockey](docs/anforderungen/06-swiss-unihockey.md) | SUH |
| 07 | [Rollen und Rechte](docs/anforderungen/07-rollen-rechte.md) | RR |
| 08 | [Benachrichtigungen](docs/anforderungen/08-benachrichtigungen.md) | BEN |
| 09 | [Saisons](docs/anforderungen/09-saisons.md) | SAI |
| 10 | [Nichtfunktionale Anforderungen](docs/anforderungen/10-nichtfunktional.md) | NF |

## Status der Anforderungen

Jede Anforderung trägt einen Status. Er zeigt, wie verbindlich sie ist.

| Status | Bedeutung |
|---|---|
| **Entschieden** | Von Cyrille ausdrücklich so festgelegt. |
| **Angenommen** | Vorschlag aus der gemeinsamen Erarbeitung, dem nicht widersprochen wurde. Gilt bis auf Widerruf. |
| **Annahme** | Noch nicht besprochen. Sinnvolle Annahme, damit die Umsetzung nicht blockiert ist. Darf hinterfragt werden. |
| **Offen** | Muss vor oder während der Umsetzung geklärt werden. |
| **Später** | Bewusst zurückgestellt. Nicht umsetzen, aber das Modell nicht dagegen verbauen. |
