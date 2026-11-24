package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.MatchRules.SuState;
import ch.stvbero.platform.attendance.event.SuChangeDto.Field;
import ch.stvbero.platform.attendance.event.SuChangeDto.Item;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SUH-12: what swiss unihockey changed about a match, before and after, without a database. */
class MatchSuChangeRulesTest {

    private static Instant at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(BackendClock.ZONE).toInstant();
    }

    private static final SuState BEFORE = new SuState(at(22, 11, 45), "Unihockey Beispielstadt", false,
            "Sporthalle Seefeld");

    @Test
    void everyChangedValueIsListedWithBeforeAndAfter() {
        List<Item> changes = MatchRules.suChanges(BEFORE,
                new SuState(at(29, 12, 15), "UHC Drei", true, "Turnhalle Weid"));

        assertEquals(List.of(
                new Item(Field.START_TIME, "So 22.11. 11:45", "So 29.11. 12:15"),
                new Item(Field.VENUE, "Sporthalle Seefeld", "Turnhalle Weid"),
                new Item(Field.OPPONENT, "Unihockey Beispielstadt", "UHC Drei"),
                new Item(Field.HOME_AWAY, "Auswärtsspiel", "Heimspiel")), changes);
    }

    @Test
    void onlyWhatChangedIsListed() {
        assertEquals(List.of(new Item(Field.START_TIME, "So 22.11. 11:45", "So 22.11. 12:15")),
                MatchRules.suChanges(BEFORE, new SuState(at(22, 12, 15), "Unihockey Beispielstadt", false,
                        " sporthalle  seefeld ")), "the venue is compared without case and whitespace");
    }

    @Test
    void changedBackLeavesNothingToWarnAbout() {
        assertTrue(MatchRules.suChanges(BEFORE, BEFORE).isEmpty());
    }

    @Test
    void venueWithoutNameIsNamedAsSuch() {
        assertEquals(List.of(new Item(Field.VENUE, "ohne Angabe", "Sporthalle Seefeld")),
                MatchRules.suChanges(new SuState(at(22, 11, 45), "Unihockey Beispielstadt", false, null), BEFORE));
    }

    @Test
    void onlyAMatchWithAnOpenChangeCanBeConfirmed() {
        assertDoesNotThrow(() -> MatchRules.checkHasSuChange(at(16, 6, 0)));
        DomainError error = assertThrows(DomainError.class, () -> MatchRules.checkHasSuChange(null));
        assertEquals(409, error.status());
    }
}
