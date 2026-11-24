package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.notification.NotificationType;
import ch.stvbero.platform.notification.Notifications;
import ch.stvbero.platform.notification.Message;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.TeamMembership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Triggers the notifications about events, responses and call-ups (BEN-02 to BEN-05, BEN-07). Determines
 * recipients and text according to {@link NotificationRules} and sends them through {@link Notifications}
 * in the transaction of the change.
 */
@ApplicationScoped
public class EventNotifications {

    @Inject
    Notifications notifications;

    @Inject
    EventGrants grants;

    /**
     * After a saved response: a called-up player declining (BEN-04) or a late cancellation (BEN-02) goes to
     * the coaches of the teams in which the person is a player for this event (O-17). If both apply, only the
     * notice about the called-up player is sent.
     */
    void responseChanged(CurrentUser user, Event event, List<TeamMembership> members, UUID personId,
                          ResponseStatus previousStatus, ResponseStatus newStatus, boolean lateCancellation,
                          String comment) {
        // A call-up only counts where the variant of the match day makes one (VAR-06)
        CallUp callUp = event.type == EventType.MATCH_DAY && ch.stvbero.platform.attendance.variant.Variants.of(event).callUp
                ? CallUp.findById(event.id) : null;
        boolean calledUp = callUp != null && callUp.personIds.contains(personId);
        NotificationType type;
        boolean published = callUp != null && callUp.publishedAt != null;
        if (NotificationRules.calledUpPlayerDeclined(calledUp, published, previousStatus, newStatus)) {
            type = NotificationType.CALLED_UP_PLAYER_DECLINED;
        } else if (NotificationRules.lateCancellationNotice(previousStatus, newStatus, lateCancellation)) {
            type = NotificationType.LATE_CANCELLATION;
        } else {
            return;
        }
        Set<UUID> teams = new LinkedHashSet<>();
        members.stream().filter(m -> m.person.id.equals(personId) && m.teamRole == TeamRole.PLAYER)
                .forEach(m -> teams.add(m.team.id));
        List<UUID> coaches = new java.util.ArrayList<>(members.stream()
                .filter(m -> m.teamRole == TeamRole.COACH && teams.contains(m.team.id))
                .map(m -> m.person.id).toList());
        // Those with a special permission for the event hear it like the coaches (RR-09)
        grants.holders(event).stream().filter(id -> !coaches.contains(id)).forEach(coaches::add);
        Set<UUID> recipients = NotificationRules.withoutTrigger(coaches, user.personId());
        recipients.remove(personId);
        String person = name(personId);
        String eventLabel = label(event);
        String text = NotificationRules.withReason(type == NotificationType.CALLED_UP_PLAYER_DECLINED
                ? NotificationRules.calledUpDeclinedText(person, eventLabel, newStatus)
                : NotificationRules.lateCancellationText(person, eventLabel, newStatus), comment);
        send(recipients, p -> new Message(type, p, event.id, text));
    }

    /** Call-up published: all players of the team, each with «aufgeboten» or «nicht aufgeboten» (BEN-03). */
    void published(CurrentUser user, Event event, Collection<UUID> players, Set<UUID> calledUp) {
        String eventLabel = label(event);
        send(NotificationRules.withoutTrigger(players, user.personId()),
                p -> new Message(NotificationType.CALL_UP_PUBLISHED, p, event.id,
                        NotificationRules.publishedText(eventLabel, calledUp.contains(p))));
    }

    /** Published call-up changed: only those who are added or removed (BEN-05, AUF-27). */
    void callUpChanged(CurrentUser user, Event event, Set<UUID> previous, Set<UUID> now) {
        NotificationRules.Change change = NotificationRules.callUpChanged(previous, now);
        Set<UUID> affected = new LinkedHashSet<>(change.added());
        affected.addAll(change.removed());
        String eventLabel = label(event);
        send(NotificationRules.withoutTrigger(affected, user.personId()),
                p -> new Message(NotificationType.CALL_UP_CHANGED, p, event.id,
                        NotificationRules.callUpChangedText(eventLabel, change.added().contains(p))));
    }

    /** Event cancelled: all members of the participating teams (BEN-07, TER-12). */
    void cancelled(CurrentUser user, Event event) {
        String text = NotificationRules.cancelledText(label(event), event.cancellationReason);
        send(NotificationRules.withoutTrigger(members(event), user.personId()),
                p -> new Message(NotificationType.EVENT_CANCELLED, p, event.id, text));
    }

    /**
     * Event rescheduled: all members of the participating teams (BEN-07, TER-12), before and after the change,
     * so that a removed team learns about it as well. The label uses the previous date.
     */
    void rescheduled(CurrentUser user, Event event, String oldLabel, Collection<UUID> previousMembers) {
        Set<UUID> all = new LinkedHashSet<>(previousMembers);
        all.addAll(members(event));
        String text = NotificationRules.rescheduledText(oldLabel, event.start);
        send(NotificationRules.withoutTrigger(all, user.personId()),
                p -> new Message(NotificationType.EVENT_RESCHEDULED, p, event.id, text));
    }

    /** All members (any team role) of the participating teams in the season of the event, by name. */
    public static List<UUID> members(Event event) {
        List<UUID> teams = event.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return TeamMembership.getEntityManager().createQuery("""
                        select distinct z.person.id, z.person.lastName, z.person.firstName from TeamMembership z
                        where z.season.id = :season and z.team.id in :teams
                        order by z.person.lastName, z.person.firstName, z.person.id""", Object[].class)
                .setParameter("season", event.season.id).setParameter("teams", teams).getResultList()
                .stream().map(row -> (UUID) row[0]).toList();
    }

    static String label(Event event) {
        return NotificationRules.label(event.title, event.start);
    }

    private static String name(UUID personId) {
        Person person = Person.findById(personId);
        return person.firstName + " " + person.lastName;
    }

    private void send(Collection<UUID> recipients, java.util.function.Function<UUID, Message> message) {
        List<Message> messages = new ArrayList<>();
        recipients.forEach(p -> messages.add(message.apply(p)));
        notifications.send(messages);
    }
}
