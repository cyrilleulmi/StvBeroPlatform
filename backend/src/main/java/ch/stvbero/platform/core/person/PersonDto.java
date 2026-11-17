package ch.stvbero.platform.core.person;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Person")
public record PersonDto(UUID id, String firstName, String lastName, String email, boolean admin) {

    public static PersonDto of(Person p) {
        return new PersonDto(p.id, p.firstName, p.lastName, p.email, p.admin);
    }
}
