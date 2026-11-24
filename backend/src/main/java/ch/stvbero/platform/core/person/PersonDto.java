package ch.stvbero.platform.core.person;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Person")
public record PersonDto(UUID id, String firstName, String lastName, String email, boolean admin,
                        @Schema(description = "Optional (PTF-15)") Gender gender,
                        @Schema(description = "Year of birth («Jahrgang»), optional (PTF-15)") Integer birthYear) {

    public static PersonDto of(Person p) {
        return new PersonDto(p.id, p.firstName, p.lastName, p.email, p.admin, p.gender, p.birthYear);
    }
}
