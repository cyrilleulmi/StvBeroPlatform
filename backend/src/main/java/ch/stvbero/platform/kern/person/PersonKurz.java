package ch.stvbero.platform.kern.person;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Comparator;
import java.util.UUID;

@Schema(name = "PersonKurz")
public record PersonKurz(UUID id, String vorname, String nachname) {

    /** Anzeigereihenfolge: Nachname, dann Vorname (docs/oberflaeche/navigation.md). */
    public static final Comparator<PersonKurz> REIHENFOLGE = Comparator
            .comparing(PersonKurz::nachname, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PersonKurz::vorname, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PersonKurz::id);

    public static PersonKurz von(Person p) {
        return new PersonKurz(p.id, p.vorname, p.nachname);
    }
}
