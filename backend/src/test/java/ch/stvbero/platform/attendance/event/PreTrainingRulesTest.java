package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.DomainError;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pre-training of a practice (TER-29, TER-30, TER-31, E-117), without database. */
class PreTrainingRulesTest {

    private static final Instant PRACTICE = Instant.parse("2026-11-19T19:20:00Z"); // 20:20 in Zurich

    private static PreTrainingDto pre(String title, String start) {
        return new PreTrainingDto(title, start == null ? null : OffsetDateTime.parse(start));
    }

    private static DomainError fails(Runnable check) {
        return assertThrows(DomainError.class, check::run);
    }

    private static String field(DomainError error) {
        return error.toProblem().fields().get(0).field();
    }

    @Test
    void ter29NoPreTrainingIsFine() {
        assertNull(PreTrainingRules.checkEvent(EventType.MATCH_DAY, null, PRACTICE));
        assertNull(PreTrainingRules.checkSeries(SeriesType.OTHER, null, LocalTime.of(20, 20)));
    }

    @Test
    void ter29TitleIsStrippedAndStartMustBeBeforeThePractice() {
        PreTrainingRules.Moment moment = PreTrainingRules.checkEvent(EventType.PRACTICE,
                pre("  Joggen ", "2026-11-19T20:00:00+01:00"), PRACTICE);
        assertEquals("Joggen", moment.title());
        assertEquals(Instant.parse("2026-11-19T19:00:00Z"), moment.start());
        DomainError same = fails(() -> PreTrainingRules.checkEvent(EventType.PRACTICE,
                pre("Joggen", "2026-11-19T20:20:00+01:00"), PRACTICE));
        assertEquals(400, same.status());
        assertEquals("Das Vortraining muss vor dem Training beginnen.", same.getMessage());
        assertEquals("preTraining.start", field(same));
    }

    @Test
    void ter29StartMustBeOnTheSameDayInZurich() {
        // 23:30 the evening before is before the practice but on another day
        DomainError otherDay = fails(() -> PreTrainingRules.checkEvent(EventType.PRACTICE,
                pre("Joggen", "2026-11-18T23:30:00+01:00"), PRACTICE));
        assertEquals("Das Vortraining muss am gleichen Tag sein wie das Training.", otherDay.getMessage());
        // 00:10 in Zurich is the day before in UTC, but the same day for the practice at 08:00
        PreTrainingRules.Moment early = PreTrainingRules.checkEvent(EventType.PRACTICE,
                pre("Joggen", "2026-11-19T00:10:00+01:00"), Instant.parse("2026-11-19T08:00:00+01:00"));
        assertEquals(Instant.parse("2026-11-18T23:10:00Z"), early.start());
    }

    @Test
    void ter29OnlyPracticesHaveOne() {
        DomainError other = fails(() -> PreTrainingRules.checkEvent(EventType.OTHER,
                pre("Joggen", "2026-11-19T20:00:00+01:00"), PRACTICE));
        assertEquals("Ein Vortraining gibt es nur bei Trainings.", other.getMessage());
        assertEquals("preTraining.title", field(other));
        assertEquals("Ein Vortraining gibt es nur bei Trainings.", fails(() -> PreTrainingRules.checkSeries(
                SeriesType.OTHER, new SeriesPreTrainingDto("Joggen", "20:00"), LocalTime.of(20, 20))).getMessage());
    }

    @Test
    void ter29TitleIsRequiredAndAtMost60Characters() {
        for (String title : new String[] {null, "", "   "}) {
            assertEquals("preTraining.title", field(fails(() -> PreTrainingRules.checkEvent(EventType.PRACTICE,
                    pre(title, "2026-11-19T20:00:00+01:00"), PRACTICE))));
        }
        PreTrainingRules.checkEvent(EventType.PRACTICE, pre("x".repeat(60), "2026-11-19T20:00:00+01:00"), PRACTICE);
        assertEquals(400, fails(() -> PreTrainingRules.checkEvent(EventType.PRACTICE,
                pre("x".repeat(61), "2026-11-19T20:00:00+01:00"), PRACTICE)).status());
        assertEquals("preTraining.start", field(fails(() -> PreTrainingRules.checkEvent(EventType.PRACTICE,
                pre("Joggen", null), PRACTICE))));
    }

    @Test
    void ter30SeriesTimeIsBeforeTheStartOfTheSeries() {
        PreTrainingRules.TimeOfDay time = PreTrainingRules.checkSeries(SeriesType.PRACTICE,
                new SeriesPreTrainingDto(" Kraft ", "19:00"), LocalTime.of(19, 15));
        assertEquals(new PreTrainingRules.TimeOfDay("Kraft", LocalTime.of(19, 0)), time);
        for (String bad : new String[] {"19:15", "20:00"}) {
            DomainError error = fails(() -> PreTrainingRules.checkSeries(SeriesType.PRACTICE,
                    new SeriesPreTrainingDto("Kraft", bad), LocalTime.of(19, 15)));
            assertEquals("Das Vortraining muss vor dem Training beginnen.", error.getMessage());
            assertEquals("preTraining.startTime", field(error));
        }
        for (String bad : new String[] {null, "7 Uhr", "25:00"}) {
            assertEquals("Bitte eine Zeit im Format 18:30 angeben.", fails(() -> PreTrainingRules.checkSeries(
                    SeriesType.PRACTICE, new SeriesPreTrainingDto("Kraft", bad), LocalTime.of(19, 15))).getMessage());
        }
    }

    @Test
    void ter30EventTakesTheTimeOfTheSeriesOnItsDay() {
        PreTrainingRules.TimeOfDay time = new PreTrainingRules.TimeOfDay("Joggen", LocalTime.of(20, 0));
        // 20:00 in Zurich in winter and in summer time
        assertEquals(Instant.parse("2026-11-19T19:00:00Z"), PreTrainingRules.on(time, LocalDate.of(2026, 11, 19)).start());
        assertEquals(Instant.parse("2027-04-15T18:00:00Z"), PreTrainingRules.on(time, LocalDate.of(2027, 4, 15)).start());
        assertNull(PreTrainingRules.on(null, LocalDate.of(2026, 11, 19)));
    }

    @Test
    void ter31ChangedMeansAddedRemovedOrDifferentTitleOrStart() {
        PreTrainingRules.Moment joggen = new PreTrainingRules.Moment("Joggen", Instant.parse("2026-11-19T19:00:00Z"));
        assertFalse(PreTrainingRules.changed(null, null));
        assertFalse(PreTrainingRules.changed(joggen, new PreTrainingRules.Moment("Joggen", joggen.start())));
        assertTrue(PreTrainingRules.changed(null, joggen));
        assertTrue(PreTrainingRules.changed(joggen, null));
        assertTrue(PreTrainingRules.changed(joggen, new PreTrainingRules.Moment("Kraft", joggen.start())));
        assertTrue(PreTrainingRules.changed(joggen, new PreTrainingRules.Moment("Joggen", joggen.start().minusSeconds(60))));
    }

    @Test
    void ter31NotificationTextNamesWhatChanged() {
        PreTrainingRules.Moment joggen = new PreTrainingRules.Moment("Joggen", Instant.parse("2026-11-19T19:00:00Z"));
        String event = "Training Do 19.11. 20:20";
        assertEquals("Training Do 19.11. 20:20 hat neu ein Vortraining: «Joggen» ab 20:00.",
                NotificationRules.preTrainingChangedText(event, null, joggen));
        assertEquals("Das Vortraining bei Training Do 19.11. 20:20 ist geändert: «Joggen» ab 20:00.",
                NotificationRules.preTrainingChangedText(event, new PreTrainingRules.Moment("Kraft", joggen.start()), joggen));
        assertEquals("Bei Training Do 19.11. 20:20 entfällt das Vortraining «Joggen».",
                NotificationRules.preTrainingChangedText(event, joggen, null));
    }
}
