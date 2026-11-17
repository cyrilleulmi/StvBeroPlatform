package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.clock.BackendClock;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A device with push (F-19). The endpoint lets the browser find out whether it is this device. */
@Schema(name = "PushDevice")
public record PushDeviceDto(UUID id, String endpoint, String label, OffsetDateTime createdAt) {

    static PushDeviceDto of(PushSubscription s) {
        return new PushDeviceDto(s.id, s.endpoint, s.label, OffsetDateTime.ofInstant(s.createdAt, BackendClock.ZONE));
    }
}
