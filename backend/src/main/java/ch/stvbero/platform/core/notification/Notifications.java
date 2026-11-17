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
 * Sending and listing notifications (BEN). The triggers decide recipients and text and pass the messages
 * here; this class hands them to every {@link Channel} (BEN-11). Only the person can see their own list
 * (RR-07).
 */
@ApplicationScoped
public class Notifications {

    @Inject
    @Any
    Instance<Channel> channels;

    @Inject
    BackendClock clock;

    /** Sends the messages through all channels, with the time of the backend clock. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void send(Collection<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        Instant now = clock.now();
        for (Message message : messages) {
            for (Channel channel : channels) {
                channel.send(message, now);
            }
        }
    }

    /** The user's own notifications, newest first; with {@code onlyUnread} only the unread ones. */
    public List<NotificationDto> own(CurrentUser user, boolean onlyUnread) {
        String filter = onlyUnread ? " and readAt is null" : "";
        return Notification.<Notification>find("recipient.id = ?1" + filter
                        + " order by createdAt desc, sequenceNumber desc", user.personId())
                .list().stream().map(NotificationDto::of).toList();
    }

    /**
     * Marks a notification as read (docs/states.md, notification). Marking it again changes nothing. A
     * notification of another person: 403 (RR-07).
     */
    @Transactional
    public void read(CurrentUser user, UUID id) {
        Notification notification = Notification.<Notification>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Benachrichtigung gibt es nicht."));
        if (!notification.recipient.id.equals(user.personId())) {
            throw DomainError.forbidden();
        }
        if (notification.readAt == null) {
            notification.readAt = clock.now();
        }
    }

    /** All notifications of all persons in the order they were created (test support only). */
    public List<NotificationDto> all() {
        return Notification.<Notification>find("order by createdAt, sequenceNumber")
                .list().stream().map(NotificationDto::of).toList();
    }
}
