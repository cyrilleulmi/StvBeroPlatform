package ch.stvbero.platform.notification;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Switch e-mails on or off (F-19). Push is switched per device through the push subscriptions. */
@Schema(name = "NotificationSettingsInput")
public record NotificationSettingsInput(@NotNull(message = "Bitte wählen.") Boolean email) {
}
