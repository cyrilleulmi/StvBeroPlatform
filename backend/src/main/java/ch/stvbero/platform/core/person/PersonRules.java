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

    /**
     * The admin flag after a change. Only admins grant or remove it (RR-04, E-69). Coaches may create and edit
     * persons, but the flag stays as it is: without a value it is kept, a different value is 403.
     * For admins, a missing value means «no admin», as before.
     *
     * @param callerAdmin whether the person making the change is an admin
     * @param wasAdmin    the flag so far ({@code false} for a new person)
     * @param requested   the value in the input, may be {@code null}
     */
    public static boolean adminFlag(boolean callerAdmin, boolean wasAdmin, Boolean requested) {
        if (callerAdmin) {
            return Boolean.TRUE.equals(requested);
        }
        if (requested != null && requested != wasAdmin) {
            throw DomainError.forbidden();
        }
        return wasAdmin;
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
