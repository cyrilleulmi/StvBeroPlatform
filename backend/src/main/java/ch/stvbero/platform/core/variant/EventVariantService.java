package ch.stvbero.platform.core.variant;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Event variants of a team (VAR): coaches (of any team, E-69) and admins change them; members of the team and
 * everyone who manages events read them. Enforced here in the backend (RR-07).
 */
@ApplicationScoped
public class EventVariantService {

    private static final String NAME_INDEX = "event_variant_name";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Variants of the team: the standard ones first, then the team's own. */
    @Transactional
    public List<EventVariantDto> list(CurrentUser user, UUID teamId) {
        Team team = find(teamId);
        if (!user.allTeams() && TeamMembership.count("person.id = ?1 and team.id = ?2", user.personId(), team.id) == 0) {
            throw DomainError.forbidden();
        }
        ensureStandard();
        return EventVariant.of(team.id).stream().map(EventVariant::dto).toList();
    }

    /** A variant of the team's own (VAR-02). */
    @Transactional
    public EventVariantDto create(CurrentUser user, UUID teamId, EventVariantInput input) {
        user.checkAllTeams();
        Team team = find(teamId);
        ensureStandard();
        if (input.eventType() == null) {
            throw DomainError.invalidField("eventType", "Bitte die Art des Termins wählen.");
        }
        VariantRules.checkRoom(EventVariant.count("teamId", team.id));
        EventVariant variant = new EventVariant();
        variant.id = UUID.randomUUID();
        variant.teamId = team.id;
        variant.kind = VariantKind.CUSTOM;
        variant.eventType = input.eventType();
        variant.sortOrder = 10;
        variant.createdAt = clock.now();
        apply(variant, input, true);
        variant.persist();
        Unique.save(em, NAME_INDEX, () -> DomainError.ruleViolation("name", "Name vergeben", VariantRules.NAME_TAKEN, "VAR-02"));
        return variant.dto();
    }

    /** Change a variant (VAR-04 to VAR-09). The type of a variant stays. */
    @Transactional
    public EventVariantDto update(CurrentUser user, UUID teamId, UUID variantId, EventVariantInput input) {
        user.checkAllTeams();
        EventVariant variant = find(teamId, variantId);
        apply(variant, input, false);
        Unique.save(em, NAME_INDEX, () -> DomainError.ruleViolation("name", "Name vergeben", VariantRules.NAME_TAKEN, "VAR-02"));
        return variant.dto();
    }

    /** Delete a variant of the team's own; its events fall back to the standard variant of their type (VAR-02). */
    @Transactional
    public void delete(CurrentUser user, UUID teamId, UUID variantId) {
        user.checkAllTeams();
        EventVariant variant = find(teamId, variantId);
        VariantRules.checkDeletable(variant.kind);
        variant.delete();
    }

    /** Creates the standard variants of teams that do not have them yet (VAR-01). */
    public void ensureStandard() {
        em.flush();
        em.createNativeQuery(VariantRules.ENSURE_STANDARD_SQL).executeUpdate();
    }

    private static void apply(EventVariant variant, EventVariantInput input, boolean created) {
        variant.name = input.name().strip();
        variant.color = input.color();
        boolean matchDay = variant.eventType == EventType.MATCH_DAY;
        if (input.callUp() != null) {
            variant.callUp = matchDay && input.callUp();
        }
        if (input.autoDeparture() != null) {
            variant.autoDeparture = input.autoDeparture();
        }
        if (input.responseDeadlineHours() != null) {
            variant.responseDeadlineHours = input.responseDeadlineHours();
        } else if (created) {
            variant.responseDeadlineHours = EventRules.DEFAULT_RESPONSE_DEADLINE;
        }
        if (input.attendanceCheck() != null) {
            variant.attendanceCheck = input.attendanceCheck();
        }
        if (input.declineReasonRequired() != null) {
            variant.declineReasonRequired = input.declineReasonRequired();
        }
        if (input.sections() != null || created) {
            variant.sections = VariantRules.joinSections(VariantRules.checkSections(variant.eventType, input.sections()));
        }
    }

    private EventVariant find(UUID teamId, UUID variantId) {
        Team team = find(teamId);
        ensureStandard();
        return EventVariant.<EventVariant>find("id = ?1 and teamId = ?2", variantId, team.id).firstResultOptional()
                .orElseThrow(() -> DomainError.notFound("Diese Terminvariante gibt es nicht."));
    }

    private static Team find(UUID teamId) {
        return Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }
}
