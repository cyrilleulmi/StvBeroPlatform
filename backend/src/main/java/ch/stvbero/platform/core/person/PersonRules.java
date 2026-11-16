package ch.stvbero.platform.core.person;

import ch.stvbero.platform.core.error.DomainError;

/** Fachregeln zu Personen. Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class PersonRules {

    private PersonRules() {
    }

    /**
     * Es braucht immer mindestens eine Person mit Admin-Rechten, sonst kann niemand mehr Personen,
     * Teams und Saisons verwalten (RR-04).
     *
     * @param bisherAdmin  ob die Person bisher Admin ist
     * @param kuenftigAdmin ob sie nach der Änderung Admin ist
     * @param anzahlAdmins Anzahl Admins vor der Änderung
     */
    public static void checkAdminRevocation(boolean previousAdmin, boolean futureAdmin, long countAdmins) {
        if (previousAdmin && !futureAdmin && countAdmins <= 1) {
            throw DomainError.conflict("Letzter Admin",
                    "Es braucht mindestens eine Person mit Admin-Rechten.", "RR-04");
        }
    }

    /** Leerzeichen am Rand entfernen, leere E-Mail als «keine». */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip();
        return t.isEmpty() ? null : t;
    }
}
