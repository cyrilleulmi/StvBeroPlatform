package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Rules of the calendar view (KAL-01 to KAL-04) without database. */
class CalendarRulesTest {

    static final UUID T1 = UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final UUID T2 = UUID.fromString("10000000-0000-4000-8000-000000000002");
    static final UUID T3 = UUID.fromString("10000000-0000-4000-8000-000000000003");
    static final List<UUID> ALL = List.of(T1, T2, T3);

    @Test
    void kal01Period() {
        LocalDate from = LocalDate.of(2026, 10, 26);
        assertDoesNotThrow(() -> CalendarRules.checkPeriod(from, from.plusDays(41)));
        assertEquals(400, assertThrows(DomainError.class, () -> CalendarRules.checkPeriod(from, from.plusDays(42))).status());
        assertEquals(400, assertThrows(DomainError.class, () -> CalendarRules.checkPeriod(from, from.minusDays(1))).status());
        assertEquals(400, assertThrows(DomainError.class, () -> CalendarRules.checkPeriod(null, from)).status());
    }

    @Test
    void kal03PlayerInTwoTeamsSeesBothTogether() {
        Map<UUID, TeamRole> lea = new LinkedHashMap<>();
        lea.put(T1, TeamRole.PLAYER);
        lea.put(T2, TeamRole.PLAYER);
        assertEquals(List.of(T1, T2), CalendarRules.defaults(lea, false, ALL));
    }

    @Test
    void kal04CoachStartsWithHerCoachedTeams() {
        Map<UUID, TeamRole> petra = new LinkedHashMap<>();
        petra.put(T1, TeamRole.COACH);
        petra.put(T2, TeamRole.PLAYER);
        assertEquals(List.of(T1), CalendarRules.defaults(petra, true, ALL));
    }

    @Test
    void kal04AdminWithoutTeamSeesAllTeams() {
        assertEquals(ALL, CalendarRules.defaults(Map.of(), false, ALL));
    }

    @Test
    void kal02SelectionOfAPlayer() {
        assertEquals(Set.of(T2), CalendarRules.selection(List.of(T2), false, false, Set.of(T1, T2), ALL, List.of(T1, T2)));
        assertEquals(Set.of(T1, T2), CalendarRules.selection(List.of(), false, false, Set.of(T1, T2), ALL, List.of(T1, T2)));
        assertEquals(403, assertThrows(DomainError.class,
                () -> CalendarRules.selection(List.of(T3), false, false, Set.of(T1, T2), ALL, List.of(T1, T2))).status());
        assertEquals(403, assertThrows(DomainError.class,
                () -> CalendarRules.selection(null, true, false, Set.of(T1, T2), ALL, List.of(T1, T2))).status());
    }

    @Test
    void kal04CoachesAndAdminsMayShowEveryTeam() {
        assertEquals(Set.copyOf(ALL), CalendarRules.selection(null, true, true, Set.of(T1), ALL, List.of(T1)));
        assertEquals(Set.of(T3), CalendarRules.selection(List.of(T3), false, true, Set.of(T1), ALL, List.of(T1)));
        UUID unknown = UUID.randomUUID();
        assertEquals(400, assertThrows(DomainError.class,
                () -> CalendarRules.selection(List.of(unknown), false, true, Set.of(T1), ALL, List.of(T1))).status());
    }
}
