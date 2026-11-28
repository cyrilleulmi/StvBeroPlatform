package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rules of the pre-training («Vortraining») of a practice (TER-29, TER-30, E-117), without database: the checks of
 * the input, the copy from a series to its events and the comparison «did it change». A pre-training is a title and
 * a start; on an event the start is a moment, on a series a time of day. Removing it from an event removes its
 * responses ({@link Event#removePreTraining()}).
 */
public final class PreTrainingRules {

    public static final int MAX_TITLE = 60;

    static final String TITLE_FIELD = "preTraining.title";
    static final String START_FIELD = "preTraining.start";
    static final String START_TIME_FIELD = "preTraining.startTime";

    private static final Pattern TIME = Pattern.compile(EventRules.TIME_PATTERN);

    private PreTrainingRules() {
    }

    /** Pre-training of an event: title and moment. */
    public record Moment(String title, Instant start) {
    }

    /** Pre-training of a series: title and time of day. */
    public record TimeOfDay(String title, LocalTime time) {
    }

    // ------------------------------------------------------------------ Checks

    /**
     * Checks the pre-training of an event and returns it with the title stripped; {@code null} for none. Only
     * practices have one, it begins before the practice on the same day (TER-29).
     */
    public static Moment checkEvent(EventType type, PreTrainingDto input, Instant eventStart) {
        if (input == null) {
            return null;
        }
        String title = checkTitle(type == EventType.PRACTICE, input.title());
        if (input.start() == null) {
            throw DomainError.invalidField(START_FIELD, "Bitte gib an, wann das Vortraining beginnt.");
        }
        Instant start = input.start().toInstant();
        if (!start.isBefore(eventStart)) {
            throw DomainError.invalidField(START_FIELD, "Das Vortraining muss vor dem Training beginnen.");
        }
        if (!EventRules.date(start).equals(EventRules.date(eventStart))) {
            throw DomainError.invalidField(START_FIELD, "Das Vortraining muss am gleichen Tag sein wie das Training.");
        }
        return new Moment(title, start);
    }

    /** Checks the pre-training of a series; {@code null} for none. The time is before the start of the series. */
    public static TimeOfDay checkSeries(SeriesType type, SeriesPreTrainingDto input, LocalTime seriesStart) {
        if (input == null) {
            return null;
        }
        String title = checkTitle(type == SeriesType.PRACTICE, input.title());
        if (input.startTime() == null || !TIME.matcher(input.startTime()).matches()) {
            throw DomainError.invalidField(START_TIME_FIELD, "Bitte eine Zeit im Format 18:30 angeben.");
        }
        LocalTime time = EventRules.time(input.startTime());
        if (!time.isBefore(seriesStart)) {
            throw DomainError.invalidField(START_TIME_FIELD, "Das Vortraining muss vor dem Training beginnen.");
        }
        return new TimeOfDay(title, time);
    }

    private static String checkTitle(boolean practice, String title) {
        if (!practice) {
            throw DomainError.invalidField(TITLE_FIELD, "Ein Vortraining gibt es nur bei Trainings.");
        }
        if (title == null || title.isBlank()) {
            throw DomainError.invalidField(TITLE_FIELD, "Bitte gib an, was ihr vor dem Training macht.");
        }
        String stripped = title.strip();
        if (stripped.length() > MAX_TITLE) {
            throw DomainError.invalidField(TITLE_FIELD, "Das darf höchstens " + MAX_TITLE + " Zeichen lang sein.");
        }
        return stripped;
    }

    // ------------------------------------------------------------------ Moment of a series event

    /** The pre-training of the series on this day, as it would be stored on the event; {@code null} for none. */
    static Moment on(TimeOfDay series, LocalDate day) {
        return series == null ? null : new Moment(series.title(), EventRules.instant(day, series.time()));
    }

    /** The pre-training of the series as stored; {@code null} for none. */
    static TimeOfDay of(Series s) {
        return s.preTrainingTitle == null ? null : new TimeOfDay(s.preTrainingTitle, s.preTrainingTime);
    }

    /** The pre-training of the event as stored; {@code null} for none. */
    static Moment of(Event event) {
        return event.preTrainingTitle == null ? null : new Moment(event.preTrainingTitle, event.preTrainingStart);
    }

    static void set(Event event, Moment preTraining) {
        if (preTraining == null) {
            event.removePreTraining();
        } else {
            event.preTrainingTitle = preTraining.title();
            event.preTrainingStart = preTraining.start();
        }
    }

    static void set(Series s, TimeOfDay preTraining) {
        s.preTrainingTitle = preTraining == null ? null : preTraining.title();
        s.preTrainingTime = preTraining == null ? null : preTraining.time();
    }

    /** Copies the pre-training of the series to its event on this day (TER-30). */
    static void copy(Event event, Series s, LocalDate day) {
        set(event, on(of(s), day));
    }

    /** The pre-training was added, removed or changed (title or start): members are told (TER-31). */
    static boolean changed(Moment before, Moment after) {
        return !Objects.equals(before, after);
    }

    // ------------------------------------------------------------------ Display

    static PreTrainingDto dto(Event event) {
        return event.preTrainingTitle == null ? null
                : new PreTrainingDto(event.preTrainingTitle, event.preTrainingStart.atZone(BackendClock.ZONE).toOffsetDateTime());
    }

    static SeriesPreTrainingDto dto(Series s) {
        return s.preTrainingTitle == null ? null
                : new SeriesPreTrainingDto(s.preTrainingTitle, EventRules.time(s.preTrainingTime));
    }

    /** The event would have a different pre-training after the change of the series, on this day (preview, TER-19). */
    static boolean differs(Event event, SeriesInput input, LocalDate day) {
        TimeOfDay series = input.preTraining() == null ? null
                : new TimeOfDay(input.preTraining().title().strip(), EventRules.time(input.preTraining().startTime()));
        return !Objects.equals(of(event), on(series, day));
    }
}
