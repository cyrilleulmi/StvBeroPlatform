# Projektkontext

## Ziel

Eine zentrale Web-Plattform für das Unihockey des STV Beromünster. Sie bündelt die organisatorischen Werkzeuge, die heute verstreut sind (Listen, Chats, Tabellen), an einem Ort – einfach genug, dass Spielerinnen, Coaches und Helfer sie ohne Anleitung nutzen können.

Die App ist momentan für Erwachsene gedacht. Funktionen für Eltern (ein Login antwortet für mehrere Kinder) sind eine mögliche spätere Erweiterung und sollen nicht verbaut werden.

## Grundprinzipien

1. **Erweiterbar:** Jede Funktion ist ein eigenständiges Modul («Kachel»). Neue Kacheln kommen dazu, ohne bestehende anzupassen.
2. **Nutzerfreundlich:** Wenige Klicks, klare Sprache (Deutsch), keine Fachbegriffe. Die häufigste Aktion («Ich bin dabei / nicht dabei») ist mit einem Tap erledigt.
3. **Mobile first, Desktop vollwertig:** Spielerinnen nutzen die App auf dem Handy in der Halle, Coaches und Verantwortliche auch am Desktop. Responsives Layout, grosse Touch-Ziele, als PWA installierbar.
4. **Ergebnisse statt Zwischenstände:** Funktionen werden vollständig umgesetzt und direkt aufrufbar gemacht. Anhand der Ergebnisse werden Folgeentscheide getroffen.

## Kacheln (Module)

| Kachel | Zweck | Hauptnutzer | Stand |
|---|---|---|---|
| Anwesenheit Spielerinnen | Zu- und Absagen für Trainings und Spieltage, Übersicht pro Termin, Aufgebot | Spielerinnen, Coaches | Anforderungen erarbeitet, siehe [requirements/](requirements/) |
| Anwesenheit Coaches | Einsatz und Präsenz der Coaches pro Training und Spieltag, Stellvertretungen | Coaches, Verantwortliche | An-/Abmeldung im Kern erarbeitet, Rest offen |
| Übungssammlung | Trainingsübungen mit Beschreibung, Skizze/Video, Tags, Trainingsplanung | Coaches | Noch nicht ausgearbeitet |
| Helfereinsatzplanung | Schichten für Turniere und Anlässe, Eintragen und Erinnerungen | Mitglieder, Organisatoren | Noch nicht ausgearbeitet |
| Ressourcen | Hilfsvideos, Regeln, Technik-Tipps, Dokumente | Spielerinnen | Noch nicht ausgearbeitet |

Details zu den noch nicht ausgearbeiteten Kacheln: [more-tiles.md](more-tiles.md).

Weitere Kacheln können später ergänzt werden (z.B. Kalender, News, Material/Ausrüstung).

## Der Kern

Der Kern ist das, was als Erstes gebaut wird und weshalb es die App überhaupt gibt:

- Personen, Teams und Funktionen
- Linien
- Termine (Trainings, Spieltage, Anlässe), auch für mehrere Teams gleichzeitig
- An- und Abmeldung mit Ansicht nach Team und Linie
- Spieltage mit Aufgebot, wenn möglich mit Spieldaten von swiss unihockey

## Arbeitsweise

- Pläne werden nur gemeinsam erarbeitet, wenn das ausdrücklich gewünscht ist.
- Sonst: sinnvolle Annahmen treffen und nach bestem Wissen vollständig implementieren, ohne Rückfragen oder Zwischenstopps.
- Getroffene Annahmen werden beim Ergebnis kurz aufgelistet, damit darauf basierend entschieden werden kann.
- Jedes Ergebnis ist lauffähig und direkt aufrufbar (URL bzw. Startbefehl), auf Desktop und Mobile geprüft, mit Seed-Daten zum Ausprobieren.
- Tests für Backend-Logik.
