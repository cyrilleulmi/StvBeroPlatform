package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Versand und Liste der Benachrichtigungen (BEN). Die Auslöser bestimmen Empfänger und Text und geben die
 * Nachrichten hierher; diese Klasse verteilt sie an alle {@link Kanal}e (BEN-11). Die eigene Liste sieht nur
 * die Person selbst (RR-07).
 */
@ApplicationScoped
public class Notifications {

    @Inject
    @Any
    Instance<Channel> channels;

    @Inject
    BackendClock clock;

    /** Nachrichten über alle Kanäle versenden, mit dem Zeitpunkt der Backend-Uhr. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void send(Collection<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        Instant now = clock.now();
        for (Message n : messages) {
            for (Channel k : channels) {
                k.send(n, now);
            }
        }
    }

    /** Eigene Benachrichtigungen, neueste zuerst; mit {@code nurUngelesene} nur die ungelesenen. */
    public List<NotificationDto> own(CurrentUser user, boolean onlyUnread) {
        String filter = onlyUnread ? " and readAt is null" : "";
        return Notification.<Notification>find("recipient.id = ?1" + filter
                        + " order by createdAt desc, sequenceNumber desc", user.personId())
                .list().stream().map(NotificationDto::of).toList();
    }

    /**
     * Als gelesen markieren (zustaende.md, Benachrichtigung). Mehrfach markieren ändert nichts. Die
     * Benachrichtigung einer anderen Person: 403 (RR-07).
     */
    @Transactional
    public void read(CurrentUser user, UUID id) {
        Notification b = Notification.<Notification>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Benachrichtigung gibt es nicht."));
        if (!b.recipient.id.equals(user.personId())) {
            throw DomainError.forbidden();
        }
        if (b.readAt == null) {
            b.readAt = clock.now();
        }
    }

    /** Alle Benachrichtigungen aller Personen in der Reihenfolge ihrer Entstehung (nur Test-Hilfe). */
    public List<NotificationDto> all() {
        return Notification.<Notification>find("order by createdAt, sequenceNumber")
                .list().stream().map(NotificationDto::of).toList();
    }
}
