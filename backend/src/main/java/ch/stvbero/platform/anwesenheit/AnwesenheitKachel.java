package ch.stvbero.platform.anwesenheit;

import ch.stvbero.platform.kern.kachel.KachelDefinition;
import ch.stvbero.platform.kern.kachel.KachelFarbe;
import jakarta.enterprise.context.ApplicationScoped;

/** Kachel «Anwesenheit»: Termine und An-/Abmeldung. Sichtbar für alle Angemeldeten. */
@ApplicationScoped
public class AnwesenheitKachel implements KachelDefinition {

    @Override
    public String id() {
        return "anwesenheit";
    }

    @Override
    public String titel() {
        return "Anwesenheit";
    }

    @Override
    public String icon() {
        return "calendar-check";
    }

    @Override
    public String route() {
        return "/termine";
    }

    @Override
    public KachelFarbe farbe() {
        return KachelFarbe.primary;
    }

    @Override
    public int reihenfolge() {
        return 10;
    }
}
