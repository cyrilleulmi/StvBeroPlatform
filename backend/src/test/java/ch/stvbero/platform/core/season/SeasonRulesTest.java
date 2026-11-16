package ch.stvbero.platform.core.season;

import ch.stvbero.platform.core.error.DomainError;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests of the season rules (SAI-01, SAI-02, SAI-04). */
class SeasonRulesTest {

    private static final LocalDate FROM_2025 = LocalDate.of(2025, 7, 1);
    private static final LocalDate FROM_2026 = LocalDate.of(2026, 7, 1);
    private static final LocalDate FROM_2027 = LocalDate.of(2027, 7, 1);

    private static final List<SeasonRules.Period> EXISTING = List.of(
            new SeasonRules.Period("2025/26", FROM_2025, LocalDate.of(2026, 6, 30)),
            new SeasonRules.Period("2026/27", FROM_2026, LocalDate.of(2027, 6, 30)));

    @Test
    void sai02SeasonBeforeCurrentIsArchived() {
        assertTrue(SeasonRules.isArchived(FROM_2025, false, FROM_2026));
    }

    @Test
    void sai02CurrentAndFutureSeasonsAreNotArchived() {
        assertFalse(SeasonRules.isArchived(FROM_2026, true, FROM_2026));
        assertFalse(SeasonRules.isArchived(FROM_2027, false, FROM_2026));
        assertFalse(SeasonRules.isArchived(FROM_2025, false, null));
    }

    @Test
    void sai02ArchiveIsNotWritable() {
        DomainError error = assertThrows(DomainError.class, () -> SeasonRules.checkWritable(FROM_2025, false, FROM_2026));
        assertEquals(409, error.status());
        assertEquals("SAI-02", error.rule());
        assertDoesNotThrow(() -> SeasonRules.checkWritable(FROM_2027, false, FROM_2026));
    }

    @Test
    void newSeasonAfterTheLastIsValid() {
        assertDoesNotThrow(() -> SeasonRules.checkNewSeason(
                new SeasonRules.Period("2027/28", FROM_2027, LocalDate.of(2028, 6, 30)), EXISTING));
    }

    @Test
    void endBeforeStartIsInvalid() {
        DomainError error = assertThrows(DomainError.class, () -> SeasonRules.checkNewSeason(
                new SeasonRules.Period("2027/28", FROM_2027, FROM_2027.minusDays(1)), EXISTING));
        assertEquals(400, error.status());
        assertEquals("endDate", error.toProblem().fields().get(0).field());
    }

    @Test
    void nameMustBeUnique() {
        DomainError error = assertThrows(DomainError.class, () -> SeasonRules.checkNewSeason(
                new SeasonRules.Period(" 2026/27 ", FROM_2027, LocalDate.of(2028, 6, 30)), EXISTING));
        assertEquals(409, error.status());
    }

    @Test
    void periodsMustNotOverlap() {
        DomainError error = assertThrows(DomainError.class, () -> SeasonRules.checkNewSeason(
                new SeasonRules.Period("Zwischen", LocalDate.of(2027, 6, 30), LocalDate.of(2028, 6, 30)), EXISTING));
        assertEquals(409, error.status());
        assertTrue(error.getMessage().contains("2026/27"));
    }
}
