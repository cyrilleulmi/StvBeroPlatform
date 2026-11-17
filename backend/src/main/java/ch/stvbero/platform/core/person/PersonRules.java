package ch.stvbero.platform.core.person;

import ch.stvbero.platform.core.error.DomainError;

/** Domain rules for persons. Without a database, so that unit tests can check them. */
public final class PersonRules {

    private PersonRules() {
    }

    /**
     * There must always be at least one person with admin rights, otherwise nobody can manage persons,
     * teams and seasons any more (RR-04).
     *
     * @param wasAdmin   whether the person is an admin so far
     * @param staysAdmin whether the person is an admin after the change
     * @param adminCount number of admins before the change
     */
    public static void checkAdminRevocation(boolean wasAdmin, boolean staysAdmin, long adminCount) {
        if (wasAdmin && !staysAdmin && adminCount <= 1) {
            throw DomainError.conflict("Letzter Admin",
                    "Es braucht mindestens eine Person mit Admin-Rechten.", "RR-04");
        }
    }

    /** Strips surrounding whitespace; an empty text (e.g. an empty email) becomes {@code null}. */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
