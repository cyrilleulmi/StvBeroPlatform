package ch.stvbero.platform.attendance.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ch.stvbero.platform.attendance.event.ResponseStatus.ATTENDING;
import static ch.stvbero.platform.attendance.event.ResponseStatus.DECLINED;
import static ch.stvbero.platform.attendance.event.ResponseStatus.PENDING;
import static ch.stvbero.platform.attendance.event.ResponseStatus.MAYBE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-06: rules for when who is notified (BEN-01 to BEN-07, BEN-13). */
class NotificationRulesTest {

    /** Event 01: Tue 17.11.2026 18:30, response deadline 24 hours, so Mon 16.11. 18:30. */
    private static final Instant START = time("2026-11-17T18:30:00+01:00");

    private static Instant time(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    @Test
    void ben01ReminderFromTwelveHoursBeforeUntilTheDeadline() {
        assertFalse(NotificationRules.reminderDue(EventStatus.PLANNED, START, 24, time("2026-11-16T06:29:59+01:00")));
        assertTrue(NotificationRules.reminderDue(EventStatus.PLANNED, START, 24, time("2026-11-16T06:30:00+01:00")));
        assertTrue(NotificationRules.reminderDue(EventStatus.PLANNED, START, 24, time("2026-11-16T18:29:59+01:00")));
        assertFalse(NotificationRules.reminderDue(EventStatus.PLANNED, START, 24, time("2026-11-16T18:30:00+01:00")));
        // A different deadline shifts the window
        assertTrue(NotificationRules.reminderDue(EventStatus.PLANNED, START, 0, time("2026-11-17T07:00:00+01:00")));
        // Cancelled events: no reminder
        assertFalse(NotificationRules.reminderDue(EventStatus.CANCELLED, START, 24, time("2026-11-16T07:00:00+01:00")));
    }

    @Test
    void ben02LateCancellationOnlyOnChangeAwayFromAttending() {
        assertTrue(NotificationRules.lateCancellationNotice(ATTENDING, DECLINED, true));
        assertTrue(NotificationRules.lateCancellationNotice(ATTENDING, MAYBE, true));
        // Before the deadline it is not a late cancellation
        assertFalse(NotificationRules.lateCancellationNotice(ATTENDING, DECLINED, false));
        // Stays a late cancellation (maybe → declined): not again
        assertFalse(NotificationRules.lateCancellationNotice(MAYBE, DECLINED, true));
        assertFalse(NotificationRules.lateCancellationNotice(PENDING, DECLINED, false));
    }

    @Test
    void ben04CalledUpPlayerDeclines() {
        assertTrue(NotificationRules.calledUpPlayerDeclined(true, true, ATTENDING, DECLINED));
        assertTrue(NotificationRules.calledUpPlayerDeclined(true, true, ATTENDING, MAYBE));
        assertTrue(NotificationRules.calledUpPlayerDeclined(true, true, PENDING, DECLINED));
        assertTrue(NotificationRules.calledUpPlayerDeclined(true, true, MAYBE, DECLINED));
        assertFalse(NotificationRules.calledUpPlayerDeclined(true, true, DECLINED, MAYBE));
        assertFalse(NotificationRules.calledUpPlayerDeclined(true, true, DECLINED, DECLINED));
        assertFalse(NotificationRules.calledUpPlayerDeclined(true, true, DECLINED, ATTENDING));
        // Not called up or only a draft (AUF-23)
        assertFalse(NotificationRules.calledUpPlayerDeclined(false, true, ATTENDING, DECLINED));
        assertFalse(NotificationRules.calledUpPlayerDeclined(true, false, ATTENDING, DECLINED));
    }

    @Test
    void ben05OnlyAffectedPersonsAreNotified() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        NotificationRules.Change change = NotificationRules.callUpChanged(Set.of(a, b), Set.of(b, c));
        assertEquals(Set.of(c), change.added());
        assertEquals(Set.of(a), change.removed());
    }

    @Test
    void ben07ReschedulingMeansADifferentStartTime() {
        assertTrue(NotificationRules.rescheduled(START, time("2026-11-17T19:00:00+01:00")));
        assertFalse(NotificationRules.rescheduled(START, time("2026-11-17T18:30:00+01:00")));
    }

    @Test
    void triggerIsNotNotified() {
        UUID petra = UUID.randomUUID();
        UUID anna = UUID.randomUUID();
        assertEquals(List.of(anna), List.copyOf(NotificationRules.withoutTrigger(List.of(petra, anna, anna), petra)));
    }

    @Test
    void notificationTexts() {
        assertEquals("Training Di 17.11.", NotificationRules.label("Training", START));
        assertEquals("Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.",
                NotificationRules.lateCancellationText("Anna Muster", "Training Di 17.11.", DECLINED));
        assertEquals("Erinnerung: Bitte gib bis Mo 16.11. 18:30 Bescheid, ob du bei Training Di 17.11. dabei bist.",
                NotificationRules.reminderText("Training Di 17.11.", time("2026-11-16T18:30:00+01:00")));
        assertEquals("Training Di 17.11. ist verschoben auf Mi 18.11. 19:00.",
                NotificationRules.rescheduledText("Training Di 17.11.", time("2026-11-18T19:00:00+01:00")));
    }
}
