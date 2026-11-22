package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.NotificationRules;
import ch.stvbero.platform.core.notification.Message;
import ch.stvbero.platform.core.notification.NotificationType;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Swaps of trips (DRV-25): a driver gives her trip of a match day to another member of the team, as a takeover or
 * against a trip of the other person on another match day. Asked first or entered directly. Only the driver of the
 * cars changes; seats and passengers stay. All methods run in the caller's transaction.
 */
@ApplicationScoped
public class DriverSwaps {

    static final String GONE_TEXT = "Diese Anfrage gibt es nicht mehr.";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    Notifications notifications;

    @Inject
    DriverPlanHistory history;

    /** Who and what of a swap, as stored. */
    public record Stored(UUID id, UUID teamId, UUID seasonId, UUID eventId, UUID requesterId, UUID partnerId,
                         UUID partnerEventId, String status, Instant createdAt) {
    }

    /**
     * Asks the partner or swaps directly. {@code members} are the members of the team in the season,
     * {@code matchDays} its match days; the caller checked that she may see the team.
     */
    public void start(CurrentUser user, UUID teamId, UUID seasonId, Set<UUID> members, Set<UUID> matchDays,
                      DriverPlanDto.SwapInput input) {
        UUID requester = input.driverId() == null ? user.personId() : input.driverId();
        if (!requester.equals(user.personId()) && !user.allTeams()) {
            throw DomainError.forbidden();
        }
        if (!matchDays.contains(input.eventId())
                || (input.partnerEventId() != null && !matchDays.contains(input.partnerEventId()))) {
            throw DomainError.invalidField("eventId", "Ein gewählter Spieltag gehört nicht zu diesem Team und dieser Saison.");
        }
        if (!members.contains(input.partnerId())) {
            throw DomainError.invalidField("partnerId", "Diese Person ist nicht im Team.");
        }
        if (input.partnerId().equals(requester)) {
            throw DomainError.invalidField("partnerId", "Bitte eine andere Person wählen.");
        }
        if (input.eventId().equals(input.partnerEventId())) {
            throw DomainError.invalidField("partnerEventId", "Bitte einen anderen Spieltag wählen.");
        }
        Stored swap = new Stored(UUID.randomUUID(), teamId, seasonId, input.eventId(), requester, input.partnerId(),
                input.partnerEventId(), "OPEN", clock.now());
        check(user, swap);
        if (input.direct()) {
            execute(user, swap);
            return;
        }
        boolean pending = !em.createNativeQuery("""
                        select 1 from driver_swap where event_id = ? and requester_id = ? and status = 'OPEN'""")
                .setParameter(1, swap.eventId()).setParameter(2, requester).getResultList().isEmpty();
        if (pending) {
            throw DomainError.conflict("Anfrage offen",
                    "Für diese Fahrt ist schon eine Anfrage offen. Zieh sie zuerst zurück.", "DRV-25");
        }
        em.createNativeQuery("""
                        insert into driver_swap (id, team_id, season_id, event_id, requester_id, partner_id,
                                                 partner_event_id, status, created_by, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)""")
                .setParameter(1, swap.id()).setParameter(2, teamId).setParameter(3, seasonId)
                .setParameter(4, swap.eventId()).setParameter(5, requester).setParameter(6, swap.partnerId())
                .setParameter(7, swap.partnerEventId()).setParameter(8, user.personId()).setParameter(9, swap.createdAt())
                .executeUpdate();
        String event = label(swap.eventId());
        String partnerEvent = swap.partnerEventId() == null ? null : label(swap.partnerEventId());
        history.ofMatchDay(swap.eventId(), seasonId, DriverPlanHistory.Kind.SWAP_REQUESTED, user.personId(),
                DriverRules.historySwapRequested(name(requester), name(swap.partnerId()), event, partnerEvent));
        send(user, swap.partnerId(), swap.eventId(),
                DriverRules.swapRequestText(name(requester), event, partnerEvent));
    }

    /** The partner (or a coach, an admin) accepts: the swap is carried out if it still fits. */
    public void accept(CurrentUser user, UUID teamId, UUID seasonId, UUID swapId) {
        Stored swap = open(teamId, seasonId, swapId);
        if (!canAnswer(user, swap)) {
            throw DomainError.forbidden();
        }
        check(user, swap);
        close(swapId, "ACCEPTED");
        execute(user, swap);
        send(user, swap.requesterId(), swap.eventId(),
                DriverRules.swapAcceptedText(name(swap.partnerId()), label(swap.eventId())));
    }

    /** The partner (or a coach, an admin) declines. */
    public void decline(CurrentUser user, UUID teamId, UUID seasonId, UUID swapId) {
        Stored swap = open(teamId, seasonId, swapId);
        if (!canAnswer(user, swap)) {
            throw DomainError.forbidden();
        }
        close(swapId, "DECLINED");
        String event = label(swap.eventId());
        history.ofMatchDay(swap.eventId(), seasonId, DriverPlanHistory.Kind.SWAP_DECLINED, user.personId(),
                DriverRules.historySwapDeclined(name(swap.requesterId()), name(swap.partnerId()), event));
        send(user, swap.requesterId(), swap.eventId(), DriverRules.swapDeclinedText(name(swap.partnerId()), event));
    }

    /** The requester (or a coach, an admin) withdraws. */
    public void withdraw(CurrentUser user, UUID teamId, UUID seasonId, UUID swapId) {
        Stored swap = open(teamId, seasonId, swapId);
        if (!canWithdraw(user, swap)) {
            throw DomainError.forbidden();
        }
        close(swapId, "WITHDRAWN");
        String event = label(swap.eventId());
        history.ofMatchDay(swap.eventId(), seasonId, DriverPlanHistory.Kind.SWAP_WITHDRAWN, user.personId(),
                DriverRules.historySwapWithdrawn(name(swap.requesterId()), name(swap.partnerId()), event));
        send(user, swap.partnerId(), swap.eventId(), DriverRules.swapWithdrawnText(name(swap.requesterId()), event));
    }

    /** Open requests of a team in a season that the caller sees, oldest first. */
    public List<DriverPlanDto.Swap> visible(CurrentUser user, UUID teamId, UUID seasonId) {
        List<DriverPlanDto.Swap> result = new ArrayList<>();
        for (Stored swap : openOf(teamId, seasonId)) {
            boolean mine = swap.requesterId().equals(user.personId()) || swap.partnerId().equals(user.personId());
            if (!mine && !user.allTeams()) {
                continue;
            }
            result.add(new DriverPlanDto.Swap(swap.id(), swap.eventId(), label(swap.eventId()),
                    PersonSummary.of(Person.findById(swap.requesterId())),
                    PersonSummary.of(Person.findById(swap.partnerId())), swap.partnerEventId(),
                    swap.partnerEventId() == null ? null : label(swap.partnerEventId()),
                    swap.createdAt().atZone(BackendClock.ZONE).toOffsetDateTime(), canAnswer(user, swap),
                    canWithdraw(user, swap)));
        }
        return result;
    }

    static boolean canAnswer(CurrentUser user, Stored swap) {
        return user.allTeams() || swap.partnerId().equals(user.personId());
    }

    static boolean canWithdraw(CurrentUser user, Stored swap) {
        return user.allTeams() || swap.requesterId().equals(user.personId());
    }

    // ------------------------------------------------------------------ Carrying out

    /** The swap still fits the cars: requester drives the match day, partner not; and the other way round. */
    private void check(CurrentUser user, Stored swap) {
        Event event = matchDay(swap.eventId());
        checkChangeable(user, event);
        if (driverCar(swap.eventId(), swap.requesterId()).isEmpty()) {
            throw DomainError.conflict("Keine Fahrt", name(swap.requesterId()) + " fährt am " + label(event)
                    + " nicht (mehr). Der Tausch geht nicht.", "DRV-25");
        }
        if (driverCar(swap.eventId(), swap.partnerId()).isPresent()) {
            throw DomainError.conflict("Fährt schon", name(swap.partnerId()) + " fährt am " + label(event)
                    + " schon. Der Tausch geht nicht.", "DRV-25");
        }
        if (swap.partnerEventId() != null) {
            Event other = matchDay(swap.partnerEventId());
            checkChangeable(user, other);
            if (driverCar(swap.partnerEventId(), swap.partnerId()).isEmpty()) {
                throw DomainError.conflict("Keine Fahrt", name(swap.partnerId()) + " fährt am " + label(other)
                        + " nicht (mehr). Der Tausch geht nicht.", "DRV-25");
            }
            if (driverCar(swap.partnerEventId(), swap.requesterId()).isPresent()) {
                throw DomainError.conflict("Fährt schon", name(swap.requesterId()) + " fährt am " + label(other)
                        + " schon. Der Tausch geht nicht.", "DRV-25");
            }
        }
    }

    private void execute(CurrentUser user, Stored swap) {
        hand(swap.eventId(), swap.requesterId(), swap.partnerId());
        String event = label(swap.eventId());
        String requester = name(swap.requesterId());
        String partner = name(swap.partnerId());
        if (swap.partnerEventId() == null) {
            history.ofMatchDay(swap.eventId(), swap.seasonId(), DriverPlanHistory.Kind.TAKEN_OVER, user.personId(),
                    DriverRules.historyTakenOver(requester, partner, event));
            send(user, swap.partnerId(), swap.eventId(), DriverRules.swapDoneText(requester, event, null));
            send(user, swap.requesterId(), swap.eventId(),
                    "Du fährst am " + event + " nicht mehr, " + partner + " übernimmt.");
        } else {
            hand(swap.partnerEventId(), swap.partnerId(), swap.requesterId());
            String partnerEvent = label(swap.partnerEventId());
            history.ofMatchDay(swap.eventId(), swap.seasonId(), DriverPlanHistory.Kind.SWAPPED, user.personId(),
                    DriverRules.historySwapped(requester, partner, event, partnerEvent));
            send(user, swap.partnerId(), swap.eventId(), DriverRules.swapDoneText(requester, event, partnerEvent));
            send(user, swap.requesterId(), swap.partnerEventId(),
                    DriverRules.swapDoneText(partner, partnerEvent, event));
        }
    }

    /** The car of {@code from} on the match day gets {@code to} as driver; {@code to} leaves her seat there. */
    private void hand(UUID eventId, UUID from, UUID to) {
        em.createNativeQuery("select id from event where id = ? for update").setParameter(1, eventId).getResultList();
        UUID car = driverCar(eventId, from).orElseThrow();
        em.createNativeQuery("delete from car_passenger where event_id = ? and person_id = ?")
                .setParameter(1, eventId).setParameter(2, to).executeUpdate();
        em.createNativeQuery("delete from individual_travel where event_id = ? and person_id = ?")
                .setParameter(1, eventId).setParameter(2, to).executeUpdate();
        em.createNativeQuery("update car set driver_id = ? where id = ?")
                .setParameter(1, to).setParameter(2, car).executeUpdate();
    }

    private void checkChangeable(CurrentUser user, Event event) {
        DriverRules.checkChangeable(MatchDayRoutes.missing(event), event.status, event.end, clock.now(),
                user.allTeams());
        event.season.checkWritable();
    }

    // ------------------------------------------------------------------ Storage

    private Stored open(UUID teamId, UUID seasonId, UUID swapId) {
        return openOf(teamId, seasonId).stream().filter(s -> s.id().equals(swapId)).findFirst()
                .orElseThrow(() -> DomainError.notFound(GONE_TEXT));
    }

    private List<Stored> openOf(UUID teamId, UUID seasonId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select id, team_id, season_id, event_id, requester_id, partner_id, partner_event_id, status,
                               created_at
                        from driver_swap where team_id = ? and season_id = ? and status = 'OPEN'
                        order by created_at, id""")
                .setParameter(1, teamId).setParameter(2, seasonId).getResultList();
        return rows.stream().map(r -> new Stored((UUID) r[0], (UUID) r[1], (UUID) r[2], (UUID) r[3], (UUID) r[4],
                (UUID) r[5], (UUID) r[6], (String) r[7], Routes.instant(r[8]))).toList();
    }

    private void close(UUID swapId, String status) {
        em.createNativeQuery("update driver_swap set status = ?, decided_at = ? where id = ?")
                .setParameter(1, status).setParameter(2, clock.now()).setParameter(3, swapId).executeUpdate();
    }

    private Optional<UUID> driverCar(UUID eventId, UUID driver) {
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery("select id from car where event_id = ? and driver_id = ?")
                .setParameter(1, eventId).setParameter(2, driver).getResultList();
        return rows.stream().findFirst();
    }

    private static Event matchDay(UUID eventId) {
        return Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> DomainError.notFound("Diesen Termin gibt es nicht."));
    }

    private void send(CurrentUser user, UUID recipient, UUID eventId, String text) {
        if (!recipient.equals(user.personId())) {
            notifications.send(List.of(new Message(NotificationType.DRIVER_LIST_CHANGED, recipient, eventId, text)));
        }
    }

    private static String label(UUID eventId) {
        return label(matchDay(eventId));
    }

    private static String label(Event event) {
        return NotificationRules.label(event.title, event.start);
    }

    private static String name(UUID personId) {
        Person person = Person.findById(personId);
        return person == null ? "" : person.firstName + " " + person.lastName;
    }
}
