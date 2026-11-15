package ch.stvbero.platform.kern.kachel;

import ch.stvbero.platform.anwesenheit.AnwesenheitKachel;
import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** NF-11, NF-12, RR-05: Kacheln registrieren sich und sind nur für berechtigte Rollen sichtbar. */
class KachelRegisterTest {

    static final Angemeldet SPIELERIN = new Angemeldet(UUID.randomUUID(), UUID.randomUUID(), "Anna", false);
    static final Angemeldet ADMIN = new Angemeldet(UUID.randomUUID(), UUID.randomUUID(), "Alex", true);

    record NurAdmin(String id, int reihenfolge) implements KachelDefinition {
        public String titel() { return "Nur Admin"; }
        public String icon() { return "settings"; }
        public String route() { return "/verwaltung"; }
        public KachelFarbe farbe() { return KachelFarbe.coral; }
        @Override
        public boolean sichtbarFuer(Angemeldet person) { return person.admin(); }
    }

    @Test
    void rr05KachelMitRolleNurFuerBerechtigteSichtbar() {
        KachelRegister register = new KachelRegister(List.of(new AnwesenheitKachel(), new NurAdmin("verwaltung", 99)));

        assertEquals(List.of("anwesenheit"), register.sichtbarFuer(SPIELERIN).stream().map(Kachel::id).toList());
        assertEquals(List.of("anwesenheit", "verwaltung"), register.sichtbarFuer(ADMIN).stream().map(Kachel::id).toList());
    }

    @Test
    void nf11KachelnInFesterReihenfolge() {
        KachelRegister register = new KachelRegister(List.of(
                new NurAdmin("b", 5), new AnwesenheitKachel(), new NurAdmin("a", 5)));

        assertEquals(List.of("a", "b", "anwesenheit"), register.sichtbarFuer(ADMIN).stream().map(Kachel::id).toList());
    }

    @Test
    void nf11AnwesenheitLiefertIhreMetadaten() {
        Kachel kachel = new KachelRegister(List.of(new AnwesenheitKachel())).sichtbarFuer(SPIELERIN).getFirst();

        assertEquals(new Kachel("anwesenheit", "Anwesenheit", "calendar-check", "/termine", KachelFarbe.primary, null), kachel);
    }
}
