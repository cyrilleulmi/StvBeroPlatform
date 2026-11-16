package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.clock.BackendClock;

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
public final class NotificationRules {

    /** Die Erinnerung geht 12 Stunden vor der Antwortfrist raus (Annahme O-18). */
    public static final Duration REMINDER_BEFORE_DEADLINE = Duration.ofHours(12);

    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationRules() {
    }

    // ------------------------------------------------------------------ Wann

    /**
     * Erinnerung fällig (BEN-01): Der Termin ist geplant und «jetzt» liegt im Fenster ab 12 Stunden vor der
     * Antwortfrist bis zur Frist. Danach nicht mehr: Wer nach der Frist noch nicht geantwortet hat, wird
     * nicht nachträglich erinnert. Dass es pro Person nur eine gibt (BEN-13), stellt die Tabelle
     * {@code erinnerung} sicher.
     */
    public static boolean reminderDue(EventStatus status, Instant start, int deadlineHours, Instant now) {
        if (status != EventStatus.PLANNED) {
            return false;
        }
        Instant deadline = EventRules.responseDeadline(start, deadlineHours);
        return !now.isBefore(deadline.minus(REMINDER_BEFORE_DEADLINE)) && now.isBefore(deadline);
    }

    /**
     * Kurzfristige Absage (BEN-02, ANM-10): der Wechsel weg von «dabei» nach der Frist. Nur dieser Wechsel
     * benachrichtigt; bleibt die Antwort danach kurzfristig (z.B. von «vielleicht» auf «nicht dabei»), nicht
     * nochmals.
     */
    public static boolean lateCancellationNotice(ResponseStatus previous, ResponseStatus added, boolean lateCancellation) {
        return lateCancellation && previous == ResponseStatus.ATTENDING && added != ResponseStatus.ATTENDING;
    }

    /**
     * Eine aufgebotene Spielerin meldet sich ab (BEN-04, AUF-26), auch vor der Frist: Nach der Veröffentlichung
     * wechselt sie von «dabei» oder «offen» auf «nicht dabei» oder «vielleicht», oder von «vielleicht» auf
     * «nicht dabei». Ein Entwurf zählt nicht, den sehen die Spielerinnen nicht (AUF-23).
     */
    public static boolean calledUpPlayerDeclined(boolean calledUp, boolean published, ResponseStatus previous,
                                               ResponseStatus added) {
        if (!calledUp || !published || previous == added) {
            return false;
        }
        return switch (added) {
            case DECLINED -> true;
            case MAYBE -> previous == ResponseStatus.ATTENDING || previous == ResponseStatus.PENDING;
            case ATTENDING, PENDING -> false;
        };
    }

    /** Eine andere Beginnzeit ist eine Verschiebung (BEN-07, TER-06). */
    public static boolean rescheduled(Instant oldStart, Instant newStart) {
        return !oldStart.equals(newStart);
    }

    // ------------------------------------------------------------------ Wer

    /**
     * Wer etwas auslöst, wird darüber nicht benachrichtigt (Annahme F-06): Die Coach, die einen Termin absagt,
     * oder die Spielerin, die sich abmeldet, weiss es schon. Reihenfolge bleibt, Doppelte fallen weg.
     */
    public static Set<UUID> withoutTrigger(Collection<UUID> recipient, UUID trigger) {
        Set<UUID> result = new LinkedHashSet<>(recipient);
        result.remove(trigger);
        return result;
    }

    /** Neu aufgebotene und nicht mehr aufgebotene Spielerinnen bei einer Änderung (BEN-05). */
    public record Change(Set<UUID> added, Set<UUID> removed) {
    }

    public static Change callUpChanged(Set<UUID> previous, Set<UUID> now) {
        Set<UUID> added = new LinkedHashSet<>(now);
        added.removeAll(previous);
        Set<UUID> removed = new LinkedHashSet<>(previous);
        removed.removeAll(now);
        return new Change(added, removed);
    }

    // ------------------------------------------------------------------ Texte

    /** «Training Di 17.11.» */
    public static String label(String title, Instant start) {
        ZonedDateTime z = start.atZone(BackendClock.ZONE);
        return title + " " + WEEKDAYS[z.getDayOfWeek().getValue() - 1] + " " + TAG.format(z);
    }

    /** «Mo 16.11. 18:30» */
    static String instant(Instant instant) {
        ZonedDateTime z = instant.atZone(BackendClock.ZONE);
        return WEEKDAYS[z.getDayOfWeek().getValue() - 1] + " " + TAG.format(z) + " " + TIME.format(z);
    }

    public static String reminderText(String event, Instant deadline) {
        return "Erinnerung: Bitte gib bis " + instant(deadline) + " Bescheid, ob du bei " + event + " dabei bist.";
    }

    public static String lateCancellationText(String person, String event, ResponseStatus added) {
        return added == ResponseStatus.MAYBE
                ? person + " ist bei " + event + " kurzfristig nur noch «vielleicht» dabei."
                : person + " hat sich kurzfristig für " + event + " abgemeldet.";
    }

    public static String calledUpDeclinedText(String person, String event, ResponseStatus added) {
        return added == ResponseStatus.MAYBE
                ? person + " ist für " + event + " aufgeboten und jetzt nur «vielleicht» dabei."
                : person + " ist für " + event + " aufgeboten und hat sich abgemeldet.";
    }

    public static String publishedText(String event, boolean calledUp) {
        return "Das Aufgebot für " + event + " ist da: Du bist " + (calledUp ? "aufgeboten." : "nicht aufgeboten.");
    }

    public static String callUpChangedText(String event, boolean nowCalledUp) {
        return nowCalledUp
                ? "Das Aufgebot für " + event + " wurde geändert: Du bist jetzt aufgeboten."
                : "Das Aufgebot für " + event + " wurde geändert: Du bist nicht mehr aufgeboten.";
    }

    public static String cancelledText(String event, String reason) {
        return event + " ist abgesagt: " + reason;
    }

    public static String rescheduledText(String event, Instant newStart) {
        return event + " ist verschoben auf " + instant(newStart) + ".";
    }
}
