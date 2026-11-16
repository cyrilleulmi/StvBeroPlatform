package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;

import java.util.UUID;

/**
 * Wer die aktuelle Anfrage stellt. Admin ist eine Eigenschaft der Person (RR-04); die Rechte in
 * einem Team ergeben sich aus der Funktion dort (RR-01).
 */
public record CurrentUser(UUID accountId, UUID personId, String firstName, boolean admin) {

    /** Verwaltung von Personen, Teams und Saisons nur für Admins (RR-04, RR-07), sonst 403. */
    public void checkAdmin() {
        if (!admin) {
            throw DomainError.forbidden();
        }
    }
}
