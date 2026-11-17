package ch.stvbero.platform.core.tile;

import ch.stvbero.platform.attendance.AttendanceTile;
import ch.stvbero.platform.core.auth.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** NF-11, NF-12, RR-05: tiles registry themselves and are visible only to authorized roles. */
class TileRegistryTest {

    static final CurrentUser PLAYER = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), "Anna", false, false);
    static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), "Alex", true, false);

    record AdminOnly(String id, int sortOrder) implements TileDefinition {
        public String title() { return "Nur Admin"; }
        public String icon() { return "settings"; }
        public String route() { return "/verwaltung"; }
        public TileColor color() { return TileColor.coral; }
        @Override
        public boolean visibleFor(CurrentUser person) { return person.admin(); }
    }

    @Test
    void rr05TileWithRoleVisibleOnlyToAuthorizedUsers() {
        TileRegistry registry = new TileRegistry(List.of(new AttendanceTile(), new AdminOnly("verwaltung", 99)));

        assertEquals(List.of("anwesenheit"), registry.visibleFor(PLAYER).stream().map(Tile::id).toList());
        assertEquals(List.of("anwesenheit", "verwaltung"), registry.visibleFor(ADMIN).stream().map(Tile::id).toList());
    }

    @Test
    void nf11TilesAreInFixedSortOrder() {
        TileRegistry registry = new TileRegistry(List.of(
                new AdminOnly("b", 5), new AttendanceTile(), new AdminOnly("a", 5)));

        assertEquals(List.of("a", "b", "anwesenheit"), registry.visibleFor(ADMIN).stream().map(Tile::id).toList());
    }

    @Test
    void nf11AttendanceTileProvidesItsMetadata() {
        Tile tile = new TileRegistry(List.of(new AttendanceTile())).visibleFor(PLAYER).getFirst();

        assertEquals(new Tile("anwesenheit", "Anwesenheit", "calendar-check", "/termine", TileColor.primary, null), tile);
    }
}
