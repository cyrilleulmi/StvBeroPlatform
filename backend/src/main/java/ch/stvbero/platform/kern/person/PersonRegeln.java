package ch.stvbero.platform.kern.person;

import ch.stvbero.platform.kern.fehler.FachFehler;

/** Fachregeln zu Personen. Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class PersonRegeln {

    private PersonRegeln() {
    }

    /**
     * Es braucht immer mindestens eine Person mit Admin-Rechten, sonst kann niemand mehr Personen,
     * Teams und Saisons verwalten (RR-04).
     *
     * @param bisherAdmin  ob die Person bisher Admin ist
     * @param kuenftigAdmin ob sie nach der Änderung Admin ist
     * @param anzahlAdmins Anzahl Admins vor der Änderung
     */
    public static void pruefeAdminEntzug(boolean bisherAdmin, boolean kuenftigAdmin, long anzahlAdmins) {
        if (bisherAdmin && !kuenftigAdmin && anzahlAdmins <= 1) {
            throw FachFehler.konflikt("Letzter Admin",
                    "Es braucht mindestens eine Person mit Admin-Rechten.", "RR-04");
        }
    }

    /** Leerzeichen am Rand entfernen, leere E-Mail als «keine». */
    public static String bereinigen(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip();
        return t.isEmpty() ? null : t;
    }
}
