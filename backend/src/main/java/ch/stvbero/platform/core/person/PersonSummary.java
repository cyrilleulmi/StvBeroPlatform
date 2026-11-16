package ch.stvbero.platform.core.person;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Comparator;
import java.util.UUID;

@Schema(name = "PersonSummary")
public record PersonSummary(UUID id, String firstName, String lastName) {

    /** Anzeigereihenfolge: Nachname, dann Vorname (docs/oberflaeche/navigation.md). */
    public static final Comparator<PersonSummary> ORDER = Comparator
            .comparing(PersonSummary::lastName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PersonSummary::firstName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PersonSummary::id);

    public static PersonSummary of(Person p) {
        return new PersonSummary(p.id, p.firstName, p.lastName);
    }
}
