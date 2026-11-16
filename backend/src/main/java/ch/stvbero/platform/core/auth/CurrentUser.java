package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;

import java.util.UUID;

/**
 * Who makes the current request. Admin is a property of the person (RR-04); the permissions in a
 * team follow from the team role there (RR-01).
 */
public record CurrentUser(UUID accountId, UUID personId, String firstName, boolean admin) {

    /** Only admins manage persons, teams and seasons (RR-04, RR-07), otherwise 403. */
    public void checkAdmin() {
        if (!admin) {
            throw DomainError.forbidden();
        }
    }
}
