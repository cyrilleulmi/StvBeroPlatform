package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.uhr.Uhr;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Fachregeln zu Terminen und Serien (TER). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class TerminRegeln {

    /** Standard-Antwortfrist in Stunden vor Beginn (TER-10, Annahme zu O-11). */
    public static final int ANTWORTFRIST_STANDARD = 24;

    /** Längste Antwortfrist: zwei Wochen. */
    public static final int ANTWORTFRIST_HOECHSTENS = 336;
    public static final String ANTWORTFRIST_MINDESTENS_TEXT = "Mindestens 0 Stunden.";
    public static final String ANTWORTFRIST_HOECHSTENS_TEXT = "Höchstens 336 Stunden (14 Tage).";

    /** Längster Gültigkeitszeitraum einer Serie in Tagen, damit nicht versehentlich Jahre erzeugt werden. */
    public static final int SERIE_HOECHSTENS_TAGE = 366;

    /** Uhrzeit «18:30». */
    public static final String ZEIT_MUSTER = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("HH:mm");

    /** Ein Zeitraum von Tag bis Tag, beide eingeschlossen. */
    public record Zeitraum(LocalDate von, LocalDate bis) {
        public boolean enthaelt(LocalDate tag) {
            return !tag.isBefore(von) && !tag.isAfter(bis);
        }
    }

    /** Was mit einem künftigen Serientermin geschieht, der in eine neue Ausnahme fällt (TER-07, O-19). */
    public enum Folge {
        /** Ohne Antworten: der Termin verschwindet. */
        ENTFERNEN,
        /** Mit Antworten: der Termin wird abgesagt, Grund = Grund der Ausnahme. So geht keine Antwort verloren. */
        ABSAGEN
    }

    /** Zustand eines bestehenden künftigen Serientermins beim Abgleich mit der geänderten Serie. */
    public record Bestehend(boolean geplant, boolean einzelnGeaendert) {
    }

    /**
     * Ergebnis des Abgleichs einer geänderten Serie mit ihren künftigen Terminen (TER-08).
     *
     * @param erzeugen     Daten, für die ein neuer Termin entsteht
     * @param uebernehmen  Daten, deren Termin die neuen Angaben der Serie übernimmt
     * @param wegfallen    Daten, deren Termin nicht mehr zur Serie passt (entfernen bzw. absagen, siehe {@link Folge})
     */
    public record Abgleich(List<LocalDate> erzeugen, List<LocalDate> uebernehmen, List<LocalDate> wegfallen) {
    }

    private TerminRegeln() {
    }

    // ------------------------------------------------------------------ Rechte

    /**
     * Termine und Serien erfassen: Admins oder Coach in mindestens einem der Teams (TER-11, RR-03, RR-06).
     * So kann die Coach von Team 1 ein gemeinsames Training mit Team 2 erfassen (TER-01).
     */
    public static void pruefeDarfErfassen(boolean admin, Collection<Funktion> funktionenInDenTeams) {
        if (!darfBearbeiten(admin, funktionenInDenTeams)) {
            throw FachFehler.keineBerechtigung();
        }
    }

    /** Bearbeiten und absagen: Admins oder Coach eines beteiligten Teams (TER-11, RR-06). */
    public static boolean darfBearbeiten(boolean admin, Collection<Funktion> funktionenInDenTeams) {
        return admin || funktionenInDenTeams.contains(Funktion.COACH);
    }

    /** Ansehen: Mitglieder eines beteiligten Teams (jede Funktion) und Admins (RR-02, RR-07). */
    public static void pruefeDarfLesen(boolean admin, Collection<Funktion> funktionenInDenTeams) {
        if (!admin && funktionenInDenTeams.isEmpty()) {
            throw FachFehler.keineBerechtigung();
        }
    }

    // ------------------------------------------------------------------ Zustand

    /** Abgesagte und vergangene Termine lassen sich nicht mehr ändern oder absagen (409, TER-06). */
    public static void pruefeAenderbar(TerminStatus status, Instant ende, Instant jetzt) {
        if (status == TerminStatus.ABGESAGT) {
            throw FachFehler.konflikt("Termin abgesagt", "Der Termin ist abgesagt und lässt sich nicht mehr ändern.", "TER-06");
        }
        if (!ende.isAfter(jetzt)) {
            throw FachFehler.konflikt("Termin vorbei", "Der Termin ist vorbei und lässt sich nicht mehr ändern.", "TER-06");
        }
    }

    /** Eine geänderte Beginnzeit gilt als Verschiebung (TER-06). Einmal verschoben, bleibt der Hinweis. */
    public static boolean verschoben(boolean bisher, Instant alterBeginn, Instant neuerBeginn) {
        return bisher || !alterBeginn.equals(neuerBeginn);
    }

    /** Antwortfrist: X Stunden vor Beginn (TER-09). */
    public static Instant antwortfrist(Instant beginn, int stunden) {
        return beginn.minus(Duration.ofHours(stunden));
    }

    /** Ohne Angabe gilt die Standard-Antwortfrist von 24 Stunden (TER-10). */
    public static int antwortfrist(Integer stunden) {
        return stunden == null ? ANTWORTFRIST_STANDARD : stunden;
    }

    // ------------------------------------------------------------------ Eingaben

    /** Ein Termin, der schon vorbei ist, wird weder erfasst noch dorthin verschoben. */
    public static void pruefeNichtVorbei(Instant ende, Instant jetzt) {
        if (!ende.isAfter(jetzt)) {
            throw FachFehler.ungueltigesFeld("ende", "Der Termin liegt in der Vergangenheit.");
        }
    }

    public static void pruefeZeit(Instant beginn, Instant ende) {
        if (!ende.isAfter(beginn)) {
            throw FachFehler.ungueltigesFeld("ende", "Das Ende muss nach dem Beginn liegen.");
        }
    }

    /** Ein Spieltag gehört zu genau einem Team (AUF-03, O-14). */
    public static void pruefeTeams(TerminTyp typ, int anzahlTeams) {
        if (typ == TerminTyp.SPIELTAG && anzahlTeams != 1) {
            throw FachFehler.ungueltigesFeld("teamIds", "Ein Spieltag gehört zu genau einem Team.");
        }
    }

    public static void pruefeSerie(LocalTime beginn, LocalTime ende, LocalDate von, LocalDate bis) {
        if (!ende.isAfter(beginn)) {
            throw FachFehler.ungueltigesFeld("endeZeit", "Das Ende muss nach dem Beginn liegen.");
        }
        if (bis.isBefore(von)) {
            throw FachFehler.ungueltigesFeld("gueltigBis", "Das Ende der Serie muss nach ihrem Beginn liegen.");
        }
        if (ChronoUnit.DAYS.between(von, bis) >= SERIE_HOECHSTENS_TAGE) {
            throw FachFehler.ungueltigesFeld("gueltigBis", "Eine Serie dauert höchstens ein Jahr.");
        }
    }

    public static void pruefeAusnahme(LocalDate von, LocalDate bis) {
        if (bis.isBefore(von)) {
            throw FachFehler.ungueltigesFeld("bis", "Das Ende der Ausnahme muss nach ihrem Beginn liegen.");
        }
    }

    // ------------------------------------------------------------------ Serien

    /**
     * Alle Daten einer Serie: jeder {@code tag} im Gültigkeitszeitraum, ohne die Daten in einer Ausnahme
     * (TER-05, TER-07). Aufsteigend sortiert.
     */
    public static List<LocalDate> daten(DayOfWeek tag, LocalDate von, LocalDate bis, Collection<Zeitraum> ausnahmen) {
        List<LocalDate> daten = new ArrayList<>();
        for (LocalDate d = von.with(TemporalAdjusters.nextOrSame(tag)); !d.isAfter(bis); d = d.plusWeeks(1)) {
            if (!inAusnahme(d, ausnahmen)) {
                daten.add(d);
            }
        }
        return daten;
    }

    public static boolean inAusnahme(LocalDate tag, Collection<Zeitraum> ausnahmen) {
        return ausnahmen.stream().anyMatch(a -> a.enthaelt(tag));
    }

    /** Zeitpunkt eines Serientermins in der Zeitzone der Fachlogik (Sommer- und Winterzeit richtig). */
    public static Instant zeitpunkt(LocalDate tag, LocalTime zeit) {
        return tag.atTime(zeit).atZone(Uhr.ZONE).toInstant();
    }

    /** Neue Ausnahme: Termin ohne Antworten entfernen, mit Antworten absagen (TER-07, zustaende.md). */
    public static Folge beiAusnahme(boolean hatAntworten) {
        return hatAntworten ? Folge.ABSAGEN : Folge.ENTFERNEN;
    }

    /**
     * Abgleich nach einer Änderung der Serie (TER-08). Betrachtet werden nur künftige Daten.
     * Einzeln geänderte oder abgesagte Termine bleiben, wie sie sind (TER-06). Für ein Datum, das schon
     * einen Termin hat (auch einen abgesagten), entsteht kein zweiter.
     *
     * @param bestehend künftige Termine der Serie nach ihrem Seriendatum
     * @param soll      künftige Daten der geänderten Serie
     */
    public static Abgleich abgleichen(Map<LocalDate, Bestehend> bestehend, Collection<LocalDate> soll) {
        Set<LocalDate> sollDaten = new TreeSet<>(soll);
        List<LocalDate> erzeugen = new ArrayList<>();
        List<LocalDate> uebernehmen = new ArrayList<>();
        List<LocalDate> wegfallen = new ArrayList<>();
        for (LocalDate d : sollDaten) {
            Bestehend b = bestehend.get(d);
            if (b == null) {
                erzeugen.add(d);
            } else if (b.geplant() && !b.einzelnGeaendert()) {
                uebernehmen.add(d);
            }
        }
        new TreeSet<>(bestehend.keySet()).forEach(d -> {
            Bestehend b = bestehend.get(d);
            if (!sollDaten.contains(d) && b.geplant() && !b.einzelnGeaendert()) {
                wegfallen.add(d);
            }
        });
        return new Abgleich(erzeugen, uebernehmen, wegfallen);
    }

    // ------------------------------------------------------------------ Anzeige

    /** Anzahl Spielerinnen pro Antwortstatus. Jede Spielerin zählt einmal, auch in mehreren Teams. */
    public static ZaehlerDto zaehlen(Collection<AntwortStatus> antwortenDerSpielerinnen) {
        int dabei = 0;
        int nichtDabei = 0;
        int vielleicht = 0;
        int offen = 0;
        for (AntwortStatus s : antwortenDerSpielerinnen) {
            switch (s) {
                case DABEI -> dabei++;
                case NICHT_DABEI -> nichtDabei++;
                case VIELLEICHT -> vielleicht++;
                case OFFEN -> offen++;
            }
        }
        return new ZaehlerDto(dabei, nichtDabei, vielleicht, offen);
    }

    /** Kalendertag eines Zeitpunkts in der Zeitzone der Fachlogik. */
    public static LocalDate datum(Instant zeitpunkt) {
        return zeitpunkt.atZone(Uhr.ZONE).toLocalDate();
    }

    public static String zeit(LocalTime zeit) {
        return zeit.format(ZEIT);
    }

    public static LocalTime zeit(String zeit) {
        return LocalTime.parse(zeit, ZEIT);
    }

    public static String leerZuNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
