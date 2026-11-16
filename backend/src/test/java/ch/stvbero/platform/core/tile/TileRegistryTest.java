package ch.stvbero.platform.core.tile;

import ch.stvbero.platform.attendance.AttendanceTile;
import ch.stvbero.platform.core.auth.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** NF-11, NF-12, RR-05: Kacheln registrieren sich und sind nur für berechtigte Rollen sichtbar. */
class TileRegistryTest {

    static final CurrentUser PLAYER = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), "Anna", false);
    static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), "Alex", true);

    record AdminOnly(String id, int sortOrder) implements TileDefinition {
        public String title() { return "Nur Admin"; }
        public String icon() { return "settings"; }
        public String route() { return "/verwaltung"; }
        public TileColor color() { return TileColor.coral; }
        @Override
        public boolean visibleFor(CurrentUser person) { return person.admin(); }
    }

    @Test
    void rr05TileWithRoleOnlyForAuthorizedVisible() {
        TileRegistry register = new TileRegistry(List.of(new AttendanceTile(), new AdminOnly("verwaltung", 99)));

        assertEquals(List.of("anwesenheit"), register.visibleFor(PLAYER).stream().map(Tile::id).toList());
        assertEquals(List.of("anwesenheit", "verwaltung"), register.visibleFor(ADMIN).stream().map(Tile::id).toList());
    }

    @Test
    void nf11TilesInFixedSortOrder() {
        TileRegistry register = new TileRegistry(List.of(
                new AdminOnly("b", 5), new AttendanceTile(), new AdminOnly("a", 5)));

        assertEquals(List.of("a", "b", "anwesenheit"), register.visibleFor(ADMIN).stream().map(Tile::id).toList());
    }

    @Test
    void nf11AttendanceDeliversHerMetadata() {
        Tile tile = new TileRegistry(List.of(new AttendanceTile())).visibleFor(PLAYER).getFirst();

        assertEquals(new Tile("anwesenheit", "Anwesenheit", "calendar-check", "/termine", TileColor.primary, null), tile);
    }
}
