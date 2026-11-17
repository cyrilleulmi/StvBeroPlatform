package ch.stvbero.platform.core.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The push subscription of the browser ({@code PushSubscription.toJSON()}), registers this device (F-19). */
@Schema(name = "PushSubscriptionInput")
public record PushSubscriptionInput(@NotBlank @Size(max = 1000) String endpoint,
                                    @NotNull @Valid PushSubscriptionKeys keys) {

    /** Keys of the browser for the encryption (RFC 8291), base64url. */
    @Schema(name = "PushSubscriptionKeys")
    public record PushSubscriptionKeys(@NotBlank @Size(max = 200) String p256dh,
                                       @NotBlank @Size(max = 100) String auth) {
    }
}
