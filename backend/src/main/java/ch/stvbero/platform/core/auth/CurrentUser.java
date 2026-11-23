package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;

import java.util.UUID;

/**
 * Who makes the current request. Admin is a property of the person (RR-04). {@code coach} is true if the
 * person is coach of at least one team in the current season (RR-03, E-69). Admins and coaches may act in
 * every team ({@link #allTeams()}); only deleting, seasons, swiss unihockey and the admin flag stay with the
 * admins (RR-04, RR-08). A player's permissions follow from her team role (RR-01, RR-02).
 */
public record CurrentUser(UUID accountId, UUID personId, String firstName, boolean admin, boolean coach,
                          boolean eventGrant) {

    public CurrentUser(UUID accountId, UUID personId, String firstName, boolean admin, boolean coach) {
        this(accountId, personId, firstName, admin, coach, false);
    }

    /**
     * The same person with coach rights for the one event of this request, because a coach or admin gave her a
     * special permission for it (RR-09). {@link #eventGrant()} tells the services that the rights come from the
     * grant: changing the teams of the event and managing grants stay with real coaches (RR-11).
     */
    public CurrentUser withEventGrant() {
        return new CurrentUser(accountId, personId, firstName, admin, true, true);
    }

    /** Coach or admin in her own right, not only through a special permission (RR-11). */
    public boolean coachOrAdmin() {
        return allTeams() && !eventGrant;
    }

    /** Admins and coaches may manage every team: people, members, lines, events, responses (RR-03, E-69). */
    public boolean allTeams() {
        return admin || coach;
    }

    /** Deleting, seasons, swiss unihockey and the admin flag: admins only (RR-04, RR-07, RR-08), otherwise 403. */
    public void checkAdmin() {
        if (!admin) {
            throw DomainError.forbidden();
        }
    }

    /** Managing people, teams and team memberships: admins and coaches (RR-03, RR-07), otherwise 403. */
    public void checkAllTeams() {
        if (!allTeams()) {
            throw DomainError.forbidden();
        }
    }
}
