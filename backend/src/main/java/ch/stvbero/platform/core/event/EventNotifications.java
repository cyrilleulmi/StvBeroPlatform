package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.notification.NotificationType;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.notification.Message;
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
 * Auslöser der Benachrichtigungen rund um Termine, Antworten und Aufgebote (BEN-02 bis BEN-05, BEN-07).
 * Bestimmt Empfänger und Text nach {@link BenachrichtigungsRegeln} und versendet in der Transaktion der
 * Änderung über {@link Benachrichtigungen}.
 */
@ApplicationScoped
public class EventNotifications {

    @Inject
    Notifications notifications;

    /**
     * Nach einer gespeicherten Antwort: Abmeldung einer Aufgebotenen (BEN-04) oder kurzfristige Absage
     * (BEN-02) an die Coaches der Teams, in denen die Person beim Termin Spielerin ist (O-17). Trifft beides
     * zu, geht nur die Abmeldung der Aufgebotenen raus.
     */
    void responseChanged(CurrentUser user, Event t, List<TeamMembership> members, UUID personId,
                          ResponseStatus previous, ResponseStatus newValue, boolean lateCancellation) {
        CallUp a = t.type == EventType.MATCH_DAY ? CallUp.findById(t.id) : null;
        boolean calledUp = a != null && a.personIds.contains(personId);
        NotificationType type;
        if (NotificationRules.calledUpPlayerDeclined(calledUp, a != null && a.publishedAt != null, previous, newValue)) {
            type = NotificationType.CALLED_UP_PLAYER_DECLINED;
        } else if (NotificationRules.lateCancellationNotice(previous, newValue, lateCancellation)) {
            type = NotificationType.LATE_CANCELLATION;
        } else {
            return;
        }
        Set<UUID> teams = new LinkedHashSet<>();
        members.stream().filter(z -> z.person.id.equals(personId) && z.teamRole == TeamRole.PLAYER)
                .forEach(z -> teams.add(z.team.id));
        List<UUID> coaches = members.stream()
                .filter(z -> z.teamRole == TeamRole.COACH && teams.contains(z.team.id))
                .map(z -> z.person.id).toList();
        Set<UUID> recipient = NotificationRules.withoutTrigger(coaches, user.personId());
        recipient.remove(personId);
        String person = name(personId);
        String event = label(t);
        String text = type == NotificationType.CALLED_UP_PLAYER_DECLINED
                ? NotificationRules.calledUpDeclinedText(person, event, newValue)
                : NotificationRules.lateCancellationText(person, event, newValue);
        send(recipient, p -> new Message(type, p, t.id, text));
    }

    /** Aufgebot veröffentlicht: alle Spielerinnen des Teams, je mit «aufgeboten» oder «nicht aufgeboten» (BEN-03). */
    void published(CurrentUser user, Event t, Collection<UUID> players, Set<UUID> calledUp) {
        String event = label(t);
        send(NotificationRules.withoutTrigger(players, user.personId()),
                p -> new Message(NotificationType.CALL_UP_PUBLISHED, p, t.id,
                        NotificationRules.publishedText(event, calledUp.contains(p))));
    }

    /** Veröffentlichtes Aufgebot geändert: nur wer neu dazukommt oder herausfällt (BEN-05, AUF-27). */
    void callUpChanged(CurrentUser user, Event t, Set<UUID> previous, Set<UUID> now) {
        NotificationRules.Change change = NotificationRules.callUpChanged(previous, now);
        Set<UUID> affected = new LinkedHashSet<>(change.added());
        affected.addAll(change.removed());
        String event = label(t);
        send(NotificationRules.withoutTrigger(affected, user.personId()),
                p -> new Message(NotificationType.CALL_UP_CHANGED, p, t.id,
                        NotificationRules.callUpChangedText(event, change.added().contains(p))));
    }

    /** Termin abgesagt: alle Mitglieder der beteiligten Teams (BEN-07, TER-12). */
    void cancelled(CurrentUser user, Event t) {
        String text = NotificationRules.cancelledText(label(t), t.cancellationReason);
        send(NotificationRules.withoutTrigger(members(t), user.personId()),
                p -> new Message(NotificationType.EVENT_CANCELLED, p, t.id, text));
    }

    /**
     * Termin verschoben: alle Mitglieder der beteiligten Teams (BEN-07, TER-12), vor und nach der Änderung,
     * damit auch ein entferntes Team davon erfährt. Bezeichnung mit dem bisherigen Datum.
     */
    void rescheduled(CurrentUser user, Event t, String oldLabel, Collection<UUID> previousMembers) {
        Set<UUID> all = new LinkedHashSet<>(previousMembers);
        all.addAll(members(t));
        String text = NotificationRules.rescheduledText(oldLabel, t.start);
        send(NotificationRules.withoutTrigger(all, user.personId()),
                p -> new Message(NotificationType.EVENT_RESCHEDULED, p, t.id, text));
    }

    /** Alle Mitglieder (jede Funktion) der beteiligten Teams in der Saison des Termins, nach Name. */
    public static List<UUID> members(Event t) {
        List<UUID> teams = t.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return TeamMembership.getEntityManager().createQuery("""
                        select distinct z.person.id, z.person.lastName, z.person.firstName from TeamMembership z
                        where z.season.id = :season and z.team.id in :teams
                        order by z.person.lastName, z.person.firstName, z.person.id""", Object[].class)
                .setParameter("season", t.season.id).setParameter("teams", teams).getResultList()
                .stream().map(z -> (UUID) z[0]).toList();
    }

    static String label(Event t) {
        return NotificationRules.label(t.title, t.start);
    }

    private static String name(UUID personId) {
        Person p = Person.findById(personId);
        return p.firstName + " " + p.lastName;
    }

    private void send(Collection<UUID> recipient, java.util.function.Function<UUID, Message> message) {
        List<Message> messages = new ArrayList<>();
        recipient.forEach(p -> messages.add(message.apply(p)));
        notifications.send(messages);
    }
}
