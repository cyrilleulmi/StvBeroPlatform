package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Funktion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Fachregeln zu Antworten (ANM, RR-06). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class AntwortRegeln {

    public static final String KOMMENTAR_PFLICHT = "Bei «vielleicht» bitte kurz begründen.";
    public static final String ABGESAGT = "Der Termin ist abgesagt. Antworten sind nicht mehr möglich.";
    public static final String VORBEI = "Der Termin ist vorbei. Antworten sind nicht mehr möglich.";

    private AntwortRegeln() {
    }

    // ------------------------------------------------------------------ Rechte

    /** Für sich antworten: Mitglied eines beteiligten Teams, in jeder Funktion (ANM-01, RR-02). */
    public static void pruefeDarfSelbstAntworten(boolean mitglied) {
        if (!mitglied) {
            throw FachFehler.keineBerechtigung();
        }
    }

    /**
     * Stellvertretend antworten (ANM-11, RR-03, RR-06): Admins für jedes Mitglied eines beteiligten Teams,
     * Coaches nur für Personen, die in einem Team Spielerin sind, in dem die Coach Coach ist.
     *
     * @param meineFunktionen    Funktion der anfragenden Person pro beteiligtem Team
     * @param funktionenPerson   Funktion der Person, für die geantwortet wird, pro beteiligtem Team
     */
    public static boolean darfStellvertreten(boolean admin, Map<UUID, Funktion> meineFunktionen,
                                             Map<UUID, Funktion> funktionenPerson) {
        if (admin) {
            return !funktionenPerson.isEmpty();
        }
        return funktionenPerson.entrySet().stream().anyMatch(e -> e.getValue() == Funktion.SPIELERIN
                && meineFunktionen.get(e.getKey()) == Funktion.COACH);
    }

    public static void pruefeDarfStellvertreten(boolean admin, Map<UUID, Funktion> meineFunktionen,
                                                Map<UUID, Funktion> funktionenPerson) {
        if (!darfStellvertreten(admin, meineFunktionen, funktionenPerson)) {
            throw FachFehler.keineBerechtigung();
        }
    }

    /**
     * Kommentare und Gründe sehen die Person selbst, Coaches des Teams, in dem der Eintrag steht, und
     * Admins (ANM-07, RR-06).
     */
    public static boolean kommentarSichtbar(boolean admin, boolean eigeneAntwort, Funktion meineFunktionImTeam) {
        return admin || eigeneAntwort || meineFunktionImTeam == Funktion.COACH;
    }

    // ------------------------------------------------------------------ Zustand und Eingabe

    /** Abgesagte und vergangene Termine nehmen keine Antworten an (409, TER-06). */
    public static void pruefeOffen(TerminStatus status, Instant ende, Instant jetzt) {
        if (status == TerminStatus.ABGESAGT) {
            throw FachFehler.konflikt("Termin abgesagt", ABGESAGT, "TER-06");
        }
        if (!ende.isAfter(jetzt)) {
            throw FachFehler.konflikt("Termin vorbei", VORBEI, "TER-06");
        }
    }

    /** «Vielleicht» verlangt einen Kommentar (ANM-04). Bei «nicht dabei» ist er freiwillig (ANM-05). */
    public static void pruefeKommentar(AntwortStatus status, String kommentar) {
        if (status == AntwortStatus.OFFEN) {
            throw FachFehler.ungueltigesFeld("status", "Die Antwort ist «dabei», «nicht dabei» oder «vielleicht».");
        }
        if (status == AntwortStatus.VIELLEICHT && TerminRegeln.leerZuNull(kommentar) == null) {
            throw FachFehler.regelverletzung("kommentar", "Kommentar fehlt", KOMMENTAR_PFLICHT, "ANM-04");
        }
    }

    /**
     * Kurzfristige Absage (ANM-10): Wechsel weg von «dabei» nach der Antwortfrist. Die Markierung bleibt
     * bei weiteren Wechseln zwischen «nicht dabei» und «vielleicht» bestehen und fällt mit «dabei» weg.
     *
     * @param bisher             bisherige Antwort, {@code OFFEN} ohne Antwort
     * @param bisherKurzfristig  bisherige Antwort war schon eine kurzfristige Absage
     */
    public static boolean kurzfristig(AntwortStatus bisher, boolean bisherKurzfristig, AntwortStatus neu,
                                      Instant antwortfrist, Instant jetzt) {
        if (neu == AntwortStatus.DABEI) {
            return false;
        }
        if (bisherKurzfristig) {
            return true;
        }
        return bisher == AntwortStatus.DABEI && !jetzt.isBefore(antwortfrist);
    }
}
