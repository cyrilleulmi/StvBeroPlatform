package ch.stvbero.platform.core.variant;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Rules of the event variants (VAR), without a database. */
class VariantRulesTest {

    @Test
    void var03StandardVariantOfAnEvent() {
        assertEquals(VariantKind.PRACTICE, VariantKind.standardFor(EventType.PRACTICE, false));
        assertEquals(VariantKind.OTHER, VariantKind.standardFor(EventType.OTHER, false));
        assertEquals(VariantKind.MATCH_DAY, VariantKind.standardFor(EventType.MATCH_DAY, false));
        // A match day with a match from swiss unihockey
        assertEquals(VariantKind.SU_MATCH_DAY, VariantKind.standardFor(EventType.MATCH_DAY, true));
    }

    @Test
    void var07ResponsesComeFirstByDefault() {
        assertEquals(Section.RESPONSES, VariantRules.defaultSections(EventType.MATCH_DAY).get(0));
        assertEquals(Section.RESPONSES, VariantRules.defaultSections(EventType.PRACTICE).get(0));
        assertEquals(List.of(Section.RESPONSES, Section.ATTENDANCE, Section.DETAILS),
                VariantRules.defaultSections(EventType.OTHER));
        assertEquals(8, VariantRules.allowedSections(EventType.MATCH_DAY).size());
    }

    @Test
    void var07SectionsFitTheTypeAndKeepDetails() {
        List<Section> order = List.of(Section.DETAILS, Section.DRIVER_LIST, Section.RESPONSES);
        assertEquals(order, VariantRules.checkSections(EventType.MATCH_DAY, order));
        assertEquals(VariantRules.defaultSections(EventType.PRACTICE), VariantRules.checkSections(EventType.PRACTICE, null));
        assertEquals(VariantRules.defaultSections(EventType.PRACTICE),
                VariantRules.checkSections(EventType.PRACTICE, List.of()));

        assertEquals(400, assertThrows(DomainError.class,
                () -> VariantRules.checkSections(EventType.MATCH_DAY, List.of(Section.RESPONSES))).status());
        assertEquals(400, assertThrows(DomainError.class,
                () -> VariantRules.checkSections(EventType.PRACTICE, List.of(Section.TIMELINE, Section.DETAILS))).status());
        assertEquals(400, assertThrows(DomainError.class,
                () -> VariantRules.checkSections(EventType.OTHER, List.of(Section.DETAILS, Section.DETAILS))).status());
    }

    @Test
    void storedSectionsAreReadLeniently() {
        assertEquals(List.of(Section.MATCHES, Section.DETAILS), VariantRules.parseSections("MATCHES, DETAILS"));
        // Unknown names are skipped, doubles dropped, «Details» is always there
        assertEquals(List.of(Section.RESPONSES, Section.DETAILS), VariantRules.parseSections("RESPONSES,GONE,RESPONSES"));
        assertEquals(List.of(Section.DETAILS), VariantRules.parseSections(null));
        assertEquals("RESPONSES,DETAILS", VariantRules.joinSections(List.of(Section.RESPONSES, Section.DETAILS)));
    }

    @Test
    void var01StandardIdsAreStablePerTeamAndKind() {
        UUID team = UUID.fromString("10000000-0000-4000-8000-000000000001");
        assertEquals(VariantRules.standardId(team, VariantKind.PRACTICE), VariantRules.standardId(team, VariantKind.PRACTICE));
        assertEquals(false, VariantRules.standardId(team, VariantKind.PRACTICE)
                .equals(VariantRules.standardId(team, VariantKind.OTHER)));
    }

    @Test
    void limitsAndGuards() {
        VariantRules.checkRoom(VariantRules.MAX_PER_TEAM - 1);
        assertEquals(409, assertThrows(DomainError.class, () -> VariantRules.checkRoom(VariantRules.MAX_PER_TEAM)).status());
        VariantRules.checkDeletable(VariantKind.CUSTOM);
        assertEquals(409, assertThrows(DomainError.class, () -> VariantRules.checkDeletable(VariantKind.PRACTICE)).status());
        VariantRules.checkCallUp(true);
        assertEquals(409, assertThrows(DomainError.class, () -> VariantRules.checkCallUp(false)).status());
        VariantRules.checkFits(EventType.MATCH_DAY, EventType.MATCH_DAY, true);
        assertEquals(400, assertThrows(DomainError.class,
                () -> VariantRules.checkFits(EventType.MATCH_DAY, EventType.PRACTICE, true)).status());
        assertEquals(400, assertThrows(DomainError.class,
                () -> VariantRules.checkFits(EventType.MATCH_DAY, EventType.MATCH_DAY, false)).status());
    }
}
