package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.uhr.Uhr;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wann wer benachrichtigt wird (BEN-01 bis BEN-07, BEN-13) und mit welchem Text. Reine Fachlogik ohne
 * Datenbank; die Auslöser in den Services wenden sie an.
 */
public final class BenachrichtigungsRegeln {

    /** Die Erinnerung geht 12 Stunden vor der Antwortfrist raus (Annahme O-18). */
    public static final Duration ERINNERUNG_VOR_FRIST = Duration.ofHours(12);

    private static final String[] WOCHENTAGE = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("HH:mm");

    private BenachrichtigungsRegeln() {
    }

    // ------------------------------------------------------------------ Wann

    /**
     * Erinnerung fällig (BEN-01): Der Termin ist geplant und «jetzt» liegt im Fenster ab 12 Stunden vor der
     * Antwortfrist bis zur Frist. Danach nicht mehr: Wer nach der Frist noch nicht geantwortet hat, wird
     * nicht nachträglich erinnert. Dass es pro Person nur eine gibt (BEN-13), stellt die Tabelle
     * {@code erinnerung} sicher.
     */
    public static boolean erinnerungFaellig(TerminStatus status, Instant beginn, int fristStunden, Instant jetzt) {
        if (status != TerminStatus.GEPLANT) {
            return false;
        }
        Instant frist = TerminRegeln.antwortfrist(beginn, fristStunden);
        return !jetzt.isBefore(frist.minus(ERINNERUNG_VOR_FRIST)) && jetzt.isBefore(frist);
    }

    /**
     * Kurzfristige Absage (BEN-02, ANM-10): der Wechsel weg von «dabei» nach der Frist. Nur dieser Wechsel
     * benachrichtigt; bleibt die Antwort danach kurzfristig (z.B. von «vielleicht» auf «nicht dabei»), nicht
     * nochmals.
     */
    public static boolean kurzfristigeAbsage(AntwortStatus bisher, AntwortStatus neu, boolean kurzfristig) {
        return kurzfristig && bisher == AntwortStatus.DABEI && neu != AntwortStatus.DABEI;
    }

    /**
     * Eine aufgebotene Spielerin meldet sich ab (BEN-04, AUF-26), auch vor der Frist: Nach der Veröffentlichung
     * wechselt sie von «dabei» oder «offen» auf «nicht dabei» oder «vielleicht», oder von «vielleicht» auf
     * «nicht dabei». Ein Entwurf zählt nicht, den sehen die Spielerinnen nicht (AUF-23).
     */
    public static boolean abmeldungAufgebotene(boolean aufgeboten, boolean veroeffentlicht, AntwortStatus bisher,
                                               AntwortStatus neu) {
        if (!aufgeboten || !veroeffentlicht || bisher == neu) {
            return false;
        }
        return switch (neu) {
            case NICHT_DABEI -> true;
            case VIELLEICHT -> bisher == AntwortStatus.DABEI || bisher == AntwortStatus.OFFEN;
            case DABEI, OFFEN -> false;
        };
    }

    /** Eine andere Beginnzeit ist eine Verschiebung (BEN-07, TER-06). */
    public static boolean verschoben(Instant alterBeginn, Instant neuerBeginn) {
        return !alterBeginn.equals(neuerBeginn);
    }

    // ------------------------------------------------------------------ Wer

    /**
     * Wer etwas auslöst, wird darüber nicht benachrichtigt (Annahme F-06): Die Coach, die einen Termin absagt,
     * oder die Spielerin, die sich abmeldet, weiss es schon. Reihenfolge bleibt, Doppelte fallen weg.
     */
    public static Set<UUID> ohneAusloeser(Collection<UUID> empfaenger, UUID ausloeser) {
        Set<UUID> ergebnis = new LinkedHashSet<>(empfaenger);
        ergebnis.remove(ausloeser);
        return ergebnis;
    }

    /** Neu aufgebotene und nicht mehr aufgebotene Spielerinnen bei einer Änderung (BEN-05). */
    public record Aenderung(Set<UUID> neu, Set<UUID> entfernt) {
    }

    public static Aenderung aufgebotGeaendert(Set<UUID> bisher, Set<UUID> jetzt) {
        Set<UUID> neu = new LinkedHashSet<>(jetzt);
        neu.removeAll(bisher);
        Set<UUID> entfernt = new LinkedHashSet<>(bisher);
        entfernt.removeAll(jetzt);
        return new Aenderung(neu, entfernt);
    }

    // ------------------------------------------------------------------ Texte

    /** «Training Di 17.11.» */
    public static String bezeichnung(String titel, Instant beginn) {
        ZonedDateTime z = beginn.atZone(Uhr.ZONE);
        return titel + " " + WOCHENTAGE[z.getDayOfWeek().getValue() - 1] + " " + TAG.format(z);
    }

    /** «Mo 16.11. 18:30» */
    static String zeitpunkt(Instant zeitpunkt) {
        ZonedDateTime z = zeitpunkt.atZone(Uhr.ZONE);
        return WOCHENTAGE[z.getDayOfWeek().getValue() - 1] + " " + TAG.format(z) + " " + ZEIT.format(z);
    }

    public static String textErinnerung(String termin, Instant frist) {
        return "Erinnerung: Bitte gib bis " + zeitpunkt(frist) + " Bescheid, ob du bei " + termin + " dabei bist.";
    }

    public static String textKurzfristig(String person, String termin, AntwortStatus neu) {
        return neu == AntwortStatus.VIELLEICHT
                ? person + " ist bei " + termin + " kurzfristig nur noch «vielleicht» dabei."
                : person + " hat sich kurzfristig für " + termin + " abgemeldet.";
    }

    public static String textAufgeboteneAbgemeldet(String person, String termin, AntwortStatus neu) {
        return neu == AntwortStatus.VIELLEICHT
                ? person + " ist für " + termin + " aufgeboten und jetzt nur «vielleicht» dabei."
                : person + " ist für " + termin + " aufgeboten und hat sich abgemeldet.";
    }

    public static String textVeroeffentlicht(String termin, boolean aufgeboten) {
        return "Das Aufgebot für " + termin + " ist da: Du bist " + (aufgeboten ? "aufgeboten." : "nicht aufgeboten.");
    }

    public static String textAufgebotGeaendert(String termin, boolean jetztAufgeboten) {
        return jetztAufgeboten
                ? "Das Aufgebot für " + termin + " wurde geändert: Du bist jetzt aufgeboten."
                : "Das Aufgebot für " + termin + " wurde geändert: Du bist nicht mehr aufgeboten.";
    }

    public static String textAbgesagt(String termin, String grund) {
        return termin + " ist abgesagt: " + grund;
    }

    public static String textVerschoben(String termin, Instant neuerBeginn) {
        return termin + " ist verschoben auf " + zeitpunkt(neuerBeginn) + ".";
    }
}
