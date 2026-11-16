package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.notification.NotificationType;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.notification.Message;
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
import java.util.Map;
import java.util.UUID;

/**
 * Erinnerung vor der Antwortfrist (BEN-01): an alle Mitglieder der beteiligten Teams, die noch nicht
 * geantwortet haben. Pro Person und Termin höchstens einmal (BEN-13), festgehalten in der Tabelle
 * {@code erinnerung}; zwei gleichzeitige Prüfungen erinnern deshalb nicht doppelt.
 * <p>
 * Läuft jede Minute. Wird die Uhr in dev/test gesetzt, prüft die Test-Hilfe sofort.
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

    /** Fällige Erinnerungen versenden. Gibt die Anzahl der neu versandten zurück. */
    @Transactional
    public int check() {
        Instant now = clock.now();
        // Grobe Vorauswahl: Beginn nach «jetzt», höchstens längste Frist plus 12 Stunden entfernt.
        Instant endDate = now.plus(Duration.ofHours(EventRules.MAX_RESPONSE_DEADLINE))
                .plus(NotificationRules.REMINDER_BEFORE_DEADLINE);
        List<Event> events = em.createQuery("""
                        select distinct t from Event t left join fetch t.teams join fetch t.season
                        where t.status = :status and t.start > :now and t.start <= :endDate
                        order by t.start, t.id""", Event.class)
                .setParameter("status", EventStatus.PLANNED).setParameter("now", now).setParameter("endDate", endDate)
                .getResultList();
        List<Message> messages = new ArrayList<>();
        for (Event t : events) {
            if (!NotificationRules.reminderDue(t.status, t.start, t.responseDeadlineHours, now)) {
                continue;
            }
            Map<UUID, ResponseService.Stored> stored = responses.responses(t.id);
            String text = NotificationRules.reminderText(EventNotifications.label(t),
                    EventRules.responseDeadline(t.start, t.responseDeadlineHours));
            for (UUID person : EventNotifications.members(t)) {
                ResponseService.Stored a = stored.get(person);
                if (a != null && a.status() != ResponseStatus.PENDING) {
                    continue;
                }
                int newValue = em.createNativeQuery("""
                                insert into reminder (event_id, person_id, reminded_at) values (?, ?, ?)
                                on conflict do nothing""")
                        .setParameter(1, t.id).setParameter(2, person).setParameter(3, now).executeUpdate();
                if (newValue == 1) {
                    messages.add(new Message(NotificationType.REMINDER, person, t.id, text));
                }
            }
        }
        notifications.send(messages);
        return messages.size();
    }
}
