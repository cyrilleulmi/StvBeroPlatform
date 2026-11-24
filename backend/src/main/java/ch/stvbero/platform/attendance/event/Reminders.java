package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.notification.NotificationType;
import ch.stvbero.platform.notification.Notifications;
import ch.stvbero.platform.notification.Message;
import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Reminder before the response deadline (BEN-01): to all members of the participating teams who have not
 * responded yet. At most once per person and event (BEN-13), recorded in the table {@code reminder}; two
 * concurrent checks therefore never remind twice.
 * <p>
 * Runs every minute. When the clock is set in dev/test, the test support checks right away.
 */
@ApplicationScoped
public class Reminders {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    ResponseService responses;

    @Inject
    Notifications notifications;

    @Scheduled(every = "60s", delayed = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void regularly() {
        check();
    }

    /** Send the reminders that are due. Returns the number of newly sent ones. */
    @Transactional
    public int check() {
        Instant now = clock.now();
        // Rough preselection: start after "now", at most the longest deadline plus 12 hours away.
        Instant latestStart = now.plus(Duration.ofHours(EventRules.MAX_RESPONSE_DEADLINE))
                .plus(NotificationRules.REMINDER_BEFORE_DEADLINE);
        List<Event> events = em.createQuery("""
                        select distinct t from Event t left join fetch t.teams join fetch t.season
                        where t.status = :status and t.start > :now and t.start <= :endDate
                        order by t.start, t.id""", Event.class)
                .setParameter("status", EventStatus.PLANNED).setParameter("now", now).setParameter("endDate", latestStart)
                .getResultList();
        List<Message> messages = new ArrayList<>();
        for (Event event : events) {
            if (!NotificationRules.reminderDue(event.status, event.start, event.responseDeadlineHours, now)) {
                continue;
            }
            Map<UUID, ResponseService.Stored> stored = responses.responses(event.id);
            String text = NotificationRules.reminderText(EventNotifications.label(event),
                    EventRules.responseDeadline(event.start, event.responseDeadlineHours));
            // People whose teams are all invited without obligation are not reminded (TER-15, BEN-01)
            Set<UUID> expected = new HashSet<>();
            responses.members(event).stream().filter(m -> !event.optionalTeamIds.contains(m.team.id))
                    .forEach(m -> expected.add(m.person.id));
            for (UUID person : EventNotifications.members(event)) {
                if (!expected.contains(person)) {
                    continue;
                }
                ResponseService.Stored response = stored.get(person);
                if (response != null && response.status() != ResponseStatus.PENDING) {
                    continue;
                }
                int inserted = em.createNativeQuery("""
                                insert into reminder (event_id, person_id, reminded_at) values (?, ?, ?)
                                on conflict do nothing""")
                        .setParameter(1, event.id).setParameter(2, person).setParameter(3, now).executeUpdate();
                if (inserted == 1) {
                    messages.add(new Message(NotificationType.REMINDER, person, event.id, text));
                }
            }
        }
        notifications.send(messages);
        return messages.size();
    }
}
