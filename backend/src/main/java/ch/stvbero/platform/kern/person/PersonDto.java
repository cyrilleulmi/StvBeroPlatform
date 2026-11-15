package ch.stvbero.platform.kern.person;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Person")
public record PersonDto(UUID id, String vorname, String nachname, String email, boolean admin) {

    public static PersonDto von(Person p) {
        return new PersonDto(p.id, p.vorname, p.nachname, p.email, p.admin);
    }
}
