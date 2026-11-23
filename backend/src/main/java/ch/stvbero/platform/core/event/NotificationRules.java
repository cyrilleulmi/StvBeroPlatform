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
 * When who is notified (BEN-01 to BEN-07, BEN-13) and with which text. Pure domain logic without a database;
 * the triggers in the services apply it.
 */
public final class NotificationRules {

    /** The reminder goes out 12 hours before the response deadline (assumption O-18). */
    public static final Duration REMINDER_BEFORE_DEADLINE = Duration.ofHours(12);

    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationRules() {
    }

    // ------------------------------------------------------------------ When

    /**
     * Reminder due (BEN-01): the event is planned and "now" lies in the window from 12 hours before the
     * response deadline up to the deadline. Not afterwards: whoever has not responded by the deadline is not
     * reminded later. The table {@code reminder} makes sure there is only one per person (BEN-13).
     */
    public static boolean reminderDue(EventStatus status, Instant start, int deadlineHours, Instant now) {
        if (status != EventStatus.PLANNED) {
            return false;
        }
        Instant deadline = EventRules.responseDeadline(start, deadlineHours);
        return !now.isBefore(deadline.minus(REMINDER_BEFORE_DEADLINE)) && now.isBefore(deadline);
    }

    /**
     * Late cancellation (BEN-02, ANM-10): the change away from «dabei» after the deadline. Only this change
     * notifies; if the response stays a late cancellation afterwards (e.g. from «vielleicht» to «nicht dabei»),
     * there is no second notification.
     */
    public static boolean lateCancellationNotice(ResponseStatus previousStatus, ResponseStatus newStatus,
                                                 boolean lateCancellation) {
        return lateCancellation && previousStatus == ResponseStatus.ATTENDING && newStatus != ResponseStatus.ATTENDING;
    }

    /**
     * A called-up player declines (BEN-04, AUF-26), also before the deadline: after publication she changes
     * from «dabei» or «offen» to «nicht dabei» or «vielleicht», or from «vielleicht» to «nicht dabei». A draft
     * does not count, because players do not see it (AUF-23).
     */
    public static boolean calledUpPlayerDeclined(boolean calledUp, boolean published, ResponseStatus previousStatus,
                                                 ResponseStatus newStatus) {
        if (!calledUp || !published || previousStatus == newStatus) {
            return false;
        }
        return switch (newStatus) {
            case DECLINED -> true;
            case MAYBE -> previousStatus == ResponseStatus.ATTENDING || previousStatus == ResponseStatus.PENDING;
            case ATTENDING, PENDING -> false;
        };
    }

    /** A different start time is a rescheduling (BEN-07, TER-06). */
    public static boolean rescheduled(Instant oldStart, Instant newStart) {
        return !oldStart.equals(newStart);
    }

    // ------------------------------------------------------------------ Who

    /**
     * Whoever triggers something is not notified about it (assumption F-06): the coach who cancels an event or
     * the player who declines already knows. The order stays, duplicates are removed.
     */
    public static Set<UUID> withoutTrigger(Collection<UUID> recipients, UUID trigger) {
        Set<UUID> result = new LinkedHashSet<>(recipients);
        result.remove(trigger);
        return result;
    }

    /** Newly called-up players and players no longer called up after a change (BEN-05). */
    public record Change(Set<UUID> added, Set<UUID> removed) {
    }

    public static Change callUpChanged(Set<UUID> previous, Set<UUID> now) {
        Set<UUID> added = new LinkedHashSet<>(now);
        added.removeAll(previous);
        Set<UUID> removed = new LinkedHashSet<>(previous);
        removed.removeAll(now);
        return new Change(added, removed);
    }

    // ------------------------------------------------------------------ Texts

    /** «Training Di 17.11.» */
    public static String label(String title, Instant start) {
        ZonedDateTime zoned = start.atZone(BackendClock.ZONE);
        return title + " " + WEEKDAYS[zoned.getDayOfWeek().getValue() - 1] + " " + DAY.format(zoned);
    }

    /** «Mo 16.11. 18:30» */
    static String instant(Instant instant) {
        ZonedDateTime zoned = instant.atZone(BackendClock.ZONE);
        return WEEKDAYS[zoned.getDayOfWeek().getValue() - 1] + " " + DAY.format(zoned) + " " + TIME.format(zoned);
    }

    public static String reminderText(String event, Instant deadline) {
        return "Erinnerung: Bitte gib bis " + instant(deadline) + " Bescheid, ob du bei " + event + " dabei bist.";
    }

    public static String lateCancellationText(String person, String event, ResponseStatus newStatus) {
        return newStatus == ResponseStatus.MAYBE
                ? person + " ist bei " + event + " kurzfristig nur noch «vielleicht» dabei."
                : person + " hat sich kurzfristig für " + event + " abgemeldet.";
    }

    /** The reason or comment of a response, for the coaches and those with a special permission (ANM-05, ANM-07). */
    public static String withReason(String text, String comment) {
        return comment == null || comment.isBlank() ? text : text + " Grund: «" + comment.strip() + "»";
    }

    /** Special permission given (BEN-08, RR-14). */
    public static String grantedText(String event) {
        return "Du hast eine Sonderberechtigung für " + event
                + ": Du kannst den Termin bearbeiten, absagen, für andere antworten und die Gründe sehen.";
    }

    public static String calledUpDeclinedText(String person, String event, ResponseStatus newStatus) {
        return newStatus == ResponseStatus.MAYBE
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
