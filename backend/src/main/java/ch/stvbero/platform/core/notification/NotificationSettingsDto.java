package ch.stvbero.platform.core.notification;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * The person's channels (F-19): e-mail on or off, and the devices with push. The list in the app always exists.
 *
 * @param email          e-mails are on (default: on if there is an e-mail address)
 * @param emailAddress   address the e-mails go to, {@code null} if there is none
 * @param emailAvailable the app can send e-mails (configured)
 * @param pushAvailable  the app can send pushes (VAPID configured)
 * @param pushPublicKey  public VAPID key the browser needs to register, {@code null} without push
 * @param devices        devices with push, oldest first
 */
@Schema(name = "NotificationSettings")
public record NotificationSettingsDto(boolean email, String emailAddress, boolean emailAvailable,
                                      boolean pushAvailable, String pushPublicKey, List<PushDeviceDto> devices) {
}
