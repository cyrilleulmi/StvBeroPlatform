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
        Boolean admin,
        @Schema(description = "Optional (PTF-15); missing or null: not given") Gender gender,
        @Schema(description = "Year of birth («Jahrgang»), optional (PTF-15)")
        @jakarta.validation.constraints.Min(value = 1900, message = "Bitte einen Jahrgang zwischen 1900 und 2100 eingeben.")
        @jakarta.validation.constraints.Max(value = 2100, message = "Bitte einen Jahrgang zwischen 1900 und 2100 eingeben.")
        Integer birthYear) {
}
