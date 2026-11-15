package ch.stvbero.platform.kern.kachel;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;

/**
 * Eine Kachel registriert sich, indem sie dieses Interface als CDI-Bean umsetzt (NF-10, NF-11).
 * Das Dashboard zeigt alle Kacheln, die für die angemeldete Person sichtbar sind (NF-12, RR-05).
 */
public interface KachelDefinition {

    /** Fachlicher Schlüssel, z.B. «anwesenheit». Ergibt die Test-ID {@code kachel-<id>}. */
    String id();

    String titel();

    /** Lucide-Icon-Name, z.B. «calendar-check». */
    String icon();

    /** Route im Frontend, z.B. «/termine». */
    String route();

    KachelFarbe farbe();

    /** Reihenfolge auf dem Dashboard, aufsteigend. */
    int reihenfolge();

    /** Ob die Person die Kachel sehen darf (RR-05). Standard: alle Angemeldeten. */
    default boolean sichtbarFuer(Angemeldet person) {
        return true;
    }

    /** Kurzer Status, z.B. «2 offen». */
    default String hinweis(Angemeldet person) {
        return null;
    }
}
