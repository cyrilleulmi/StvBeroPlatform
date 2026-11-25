package ch.stvbero.platform.attendance.absence;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamListener;
import ch.stvbero.platform.attendance.event.ResponseStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Absences (ABS): a person enters a longer time away; her upcoming practices in this time are declined with
 * the reason as comment (ABS-03). The responses follow the absence when it changes or is deleted (ABS-05), and
 * practices created or moved later into the time are declined as well (ABS-04).
 */
@ApplicationScoped
public class AbsenceService implements TeamListener {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    // ------------------------------------------------------------------ Own absences

    /** Own absences that are not over yet, by first day (ABS-06). */
    public List<AbsenceDto> mine(CurrentUser user) {
        LocalDate today = today();
        Map<UUID, Integer> declined = declinedCounts(user.personId());
        return Absence.of(user.personId()).stream().filter(a -> !a.lastDay.isBefore(today))
                .map(a -> dto(a, declined.getOrDefault(a.id, 0))).toList();
    }

    /** Own absences that touch the period, for the calendar (ABS-07). */
    public List<AbsenceDto> inPeriod(UUID personId, LocalDate from, LocalDate to) {
        Map<UUID, Integer> declined = declinedCounts(personId);
        return Absence.of(personId).stream().filter(a -> !a.lastDay.isBefore(from) && !a.firstDay.isAfter(to))
                .map(a -> dto(a, declined.getOrDefault(a.id, 0))).toList();
    }

    @Transactional
    public AbsenceDto create(CurrentUser user, AbsenceInput input) {
        String note = blankToNull(input.note());
        AbsenceRules.check(input.from(), input.to(), input.reason(), note, today());
        AbsenceRules.checkNoOverlap(input.from(), input.to(), periods(user.personId(), null));
        Absence absence = Absence.create(user.personId());
        absence.firstDay = input.from();
        absence.lastDay = input.to();
        absence.reason = input.reason();
        absence.note = note;
        absence.createdAt = clock.now();
        absence.persist();
        em.flush();
        AbsenceRules.Period period = absence.period();
        // A new absence wins over «dabei» and «vielleicht» given before (ABS-03)
        applyForPerson(user.personId(), practice -> AbsenceRules.covers(period, practice));
        return dto(absence, declinedCounts(user.personId()).getOrDefault(absence.id, 0));
    }

    @Transactional
    public AbsenceDto update(CurrentUser user, UUID absenceId, AbsenceInput input) {
        Absence absence = own(user, absenceId);
        String note = blankToNull(input.note());
        AbsenceRules.check(input.from(), input.to(), input.reason(), note, today());
        AbsenceRules.checkNoOverlap(input.from(), input.to(), periods(user.personId(), absence.id));
        AbsenceRules.Period before = absence.period();
        absence.firstDay = input.from();
        absence.lastDay = input.to();
        absence.reason = input.reason();
        absence.note = note;
        em.flush();
        AbsenceRules.Period after = absence.period();
        // Only practices the absence covers newly override a response given by hand (ABS-05)
        applyForPerson(user.personId(),
                practice -> AbsenceRules.covers(after, practice) && !AbsenceRules.covers(before, practice));
        return dto(absence, declinedCounts(user.personId()).getOrDefault(absence.id, 0));
    }

    /** Deletes the absence; upcoming practices it declined are «offen» again, past ones keep «nicht dabei» (ABS-05). */
    @Transactional
    public void delete(CurrentUser user, UUID absenceId) {
        Absence absence = own(user, absenceId);
        em.createNativeQuery("""
                        delete from response r using event e
                        where r.event_id = e.id and r.absence_id = ?1 and e.starts_at > ?2 and e.status = 'PLANNED'""")
                .setParameter(1, absence.id).setParameter(2, clock.now()).executeUpdate();
        absence.delete();
        em.flush();
        applyForPerson(user.personId(), practice -> false);
    }

    /** The absence of the caller; someone else's answers 404, so its existence is not revealed (RR-07). */
    private static Absence own(CurrentUser user, UUID absenceId) {
        return Absence.<Absence>findByIdOptional(absenceId).filter(a -> a.personId.equals(user.personId()))
                .orElseThrow(() -> DomainError.notFound("Diese Abwesenheit gibt es nicht."));
    }

    // ------------------------------------------------------------------ Applying absences to responses

    /** The practices of a new team in the time of an absence are declined as well (ABS-04). */
    @Override
    @Transactional
    public void memberJoined(UUID personId) {
        applyForPerson(personId, practice -> false);
    }

    /**
     * Brings the responses of all upcoming practices of the person in line with her absences (ABS-03 to ABS-05).
     * {@code override}: practices where a «dabei» or «vielleicht» given by hand is replaced.
     */
    @Transactional
    public void applyForPerson(UUID personId, Predicate<AbsenceRules.Practice> override) {
        Instant now = clock.now();
        List<AbsenceRules.Period> periods = periods(personId, null);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select e.id, e.starts_at, e.ends_at from event e
                        where e.type = 'PRACTICE' and e.status = 'PLANNED' and e.starts_at > ?1
                          and exists (select 1 from event_team et
                                      join team_membership m on m.team_id = et.team_id and m.season_id = e.season_id
                                      where et.event_id = e.id and m.person_id = ?2)""")
                .setParameter(1, now).setParameter(2, personId).getResultList();
        Map<UUID, AbsenceRules.Practice> practices = new LinkedHashMap<>();
        for (Object[] row : rows) {
            practices.put((UUID) row[0], new AbsenceRules.Practice((UUID) row[0], instant(row[1]), instant(row[2])));
        }
        Map<UUID, AbsenceRules.Current> current = currentOfPerson(personId, now);
        List<AbsenceRules.Action> actions = new ArrayList<>();
        for (AbsenceRules.Practice practice : practices.values()) {
            AbsenceRules.decide(practice, periods, current.get(practice.eventId()), override.test(practice))
                    .ifPresent(actions::add);
        }
        // Responses of an absence on upcoming events that are no longer practices of the person
        current.forEach((eventId, response) -> {
            if (response.absenceId() != null && !practices.containsKey(eventId)) {
                actions.add(new AbsenceRules.Reopen(eventId));
            }
        });
        actions.forEach(action -> execute(action, personId, now));
    }

    /**
     * After events were created or changed (single event, series, move, other teams): declines the practices
     * that fall into an absence of a member, and reopens the ones that no longer do (ABS-04). A response given
     * by hand stays.
     */
    @Transactional
    public void applyForEvents(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }
        em.flush();
        Instant now = clock.now();
        @SuppressWarnings("unchecked")
        List<Object[]> events = em.createNativeQuery("""
                        select e.id, e.starts_at, e.ends_at, e.type, e.status from event e where e.id in (:ids)""")
                .setParameter("ids", eventIds).getResultList();
        for (Object[] row : events) {
            UUID eventId = (UUID) row[0];
            Instant start = instant(row[1]);
            if (!"PLANNED".equals(row[4]) || !start.isAfter(now)) {
                continue;
            }
            AbsenceRules.Practice practice = new AbsenceRules.Practice(eventId, start, instant(row[2]));
            boolean isPractice = "PRACTICE".equals(row[3]);
            Map<UUID, AbsenceRules.Current> current = currentOfEvent(eventId);
            Set<UUID> persons = new HashSet<>(current.keySet());
            if (isPractice) {
                persons.addAll(membersWithAbsence(eventId, practice));
            }
            for (UUID person : persons) {
                AbsenceRules.Current response = current.get(person);
                Optional<AbsenceRules.Action> action = isPractice && isMember(eventId, person)
                        ? AbsenceRules.decide(practice, periods(person, null), response, false)
                        : (response != null && response.absenceId() != null
                                ? Optional.of(new AbsenceRules.Reopen(eventId)) : Optional.empty());
                action.ifPresent(a -> execute(a, person, now));
            }
        }
    }

    private void execute(AbsenceRules.Action action, UUID personId, Instant now) {
        switch (action) {
            case AbsenceRules.Decline d -> em.createNativeQuery("""
                            insert into response (event_id, person_id, status, comment, changed_at, entered_by,
                                                  late_cancellation, absence_id)
                            values (?1, ?2, 'DECLINED', ?3, ?4, null, false, ?5)
                            on conflict (event_id, person_id) do update
                            set status = excluded.status, comment = excluded.comment, changed_at = excluded.changed_at,
                                entered_by = null, late_cancellation = false, absence_id = excluded.absence_id
                            where response.absence_id is distinct from excluded.absence_id
                               or response.comment is distinct from excluded.comment
                               or response.status <> 'DECLINED'""")
                    .setParameter(1, d.eventId()).setParameter(2, personId).setParameter(3, d.comment())
                    .setParameter(4, now).setParameter(5, d.absenceId()).executeUpdate();
            case AbsenceRules.Reopen r -> em.createNativeQuery("""
                            delete from response where event_id = ?1 and person_id = ?2 and absence_id is not null""")
                    .setParameter(1, r.eventId()).setParameter(2, personId).executeUpdate();
        }
    }

    // ------------------------------------------------------------------ Queries

    private List<AbsenceRules.Period> periods(UUID personId, UUID without) {
        return Absence.of(personId).stream().filter(a -> !a.id.equals(without)).map(Absence::period).toList();
    }

    /** Responses of the person on upcoming planned events. */
    private Map<UUID, AbsenceRules.Current> currentOfPerson(UUID personId, Instant now) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select r.event_id, r.status, r.absence_id from response r join event e on e.id = r.event_id
                        where r.person_id = ?1 and e.starts_at > ?2 and e.status = 'PLANNED'""")
                .setParameter(1, personId).setParameter(2, now).getResultList();
        Map<UUID, AbsenceRules.Current> current = new HashMap<>();
        rows.forEach(row -> current.put((UUID) row[0],
                new AbsenceRules.Current(ResponseStatus.valueOf((String) row[1]), (UUID) row[2])));
        return current;
    }

    private Map<UUID, AbsenceRules.Current> currentOfEvent(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select person_id, status, absence_id from response where event_id = ?1")
                .setParameter(1, eventId).getResultList();
        Map<UUID, AbsenceRules.Current> current = new HashMap<>();
        rows.forEach(row -> current.put((UUID) row[0],
                new AbsenceRules.Current(ResponseStatus.valueOf((String) row[1]), (UUID) row[2])));
        return current;
    }

    /** Members of the event's teams with an absence on a day the practice touches. */
    private Set<UUID> membersWithAbsence(UUID eventId, AbsenceRules.Practice practice) {
        LocalDate first = LocalDate.ofInstant(practice.start(), BackendClock.ZONE);
        LocalDate last = LocalDate.ofInstant(practice.end(), BackendClock.ZONE);
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery("""
                        select distinct a.person_id from absence a
                        where a.first_day <= ?2 and a.last_day >= ?1
                          and exists (select 1 from event e join event_team et on et.event_id = e.id
                                      join team_membership m on m.team_id = et.team_id and m.season_id = e.season_id
                                      where e.id = ?3 and m.person_id = a.person_id)""")
                .setParameter(1, first).setParameter(2, last).setParameter(3, eventId).getResultList();
        return new HashSet<>(rows);
    }

    private boolean isMember(UUID eventId, UUID personId) {
        Number count = (Number) em.createNativeQuery("""
                        select count(*) from event e join event_team et on et.event_id = e.id
                        join team_membership m on m.team_id = et.team_id and m.season_id = e.season_id
                        where e.id = ?1 and m.person_id = ?2""")
                .setParameter(1, eventId).setParameter(2, personId).getSingleResult();
        return count.longValue() > 0;
    }

    /** Upcoming practices declined by each absence of the person. */
    private Map<UUID, Integer> declinedCounts(UUID personId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select r.absence_id, count(*) from response r join event e on e.id = r.event_id
                        where r.person_id = ?1 and r.absence_id is not null and e.starts_at > ?2
                          and e.status = 'PLANNED'
                        group by r.absence_id""")
                .setParameter(1, personId).setParameter(2, clock.now()).getResultList();
        Map<UUID, Integer> counts = new HashMap<>();
        rows.forEach(row -> counts.put((UUID) row[0], ((Number) row[1]).intValue()));
        return counts;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.now(), BackendClock.ZONE);
    }

    private static AbsenceDto dto(Absence a, int declined) {
        return new AbsenceDto(a.id, a.firstDay, a.lastDay, a.reason, a.note, declined);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        throw new IllegalStateException("Unexpected time value " + value);
    }
}
