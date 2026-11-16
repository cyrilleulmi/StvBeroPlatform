package ch.stvbero.platform.core.person;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PersonInput")
public record PersonInput(
        @NotBlank(message = "Bitte einen Vornamen eingeben.") @Size(max = 60, message = "Höchstens 60 Zeichen.") String firstName,
        @NotBlank(message = "Bitte einen Nachnamen eingeben.") @Size(max = 60, message = "Höchstens 60 Zeichen.") String lastName,
        @Email(message = "Bitte eine gültige E-Mail-Adresse eingeben.") @Size(max = 254) String email,
        Boolean admin) {
}
