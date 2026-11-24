package ch.stvbero.platform.attendance.variant;

import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.core.team.TeamSeason;
import io.quarkus.hibernate.orm.panache.Panache;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds the variant that applies to an event (VAR-03): the chosen one, otherwise the standard variant of the
 * event type in the team that comes first by name. Loads everything for a list of events at once.
 */
public final class Variants {

    private final Map<UUID, EventVariant> byId = new HashMap<>();
    private final Map<UUID, Map<VariantKind, EventVariant>> standard = new HashMap<>();
    /** Match days with at least one match from swiss unihockey. */
    private final Set<UUID> imported = new HashSet<>();
    /** Team name per season and team, for «the team that comes first by name». */
    private final Map<UUID, Map<UUID, String>> names = new HashMap<>();

    private Variants() {
    }

    public static Variants of(Collection<Event> events) {
        Variants variants = new Variants();
        Set<UUID> teams = new LinkedHashSet<>();
        Set<UUID> seasons = new LinkedHashSet<>();
        Set<UUID> matchDays = new LinkedHashSet<>();
        for (Event event : events) {
            event.teams.forEach(team -> teams.add(team.id));
            seasons.add(event.season.id);
            if (event.type == EventType.MATCH_DAY && event.variantId == null) {
                matchDays.add(event.id);
            }
        }
        if (!teams.isEmpty()) {
            for (EventVariant variant : EventVariant.<EventVariant>list("teamId in ?1", teams)) {
                variants.byId.put(variant.id, variant);
                if (variant.standard()) {
                    variants.standard.computeIfAbsent(variant.teamId, key -> new HashMap<>()).put(variant.kind, variant);
                }
            }
            TeamSeason.<TeamSeason>list("season.id in ?1 and team.id in ?2", seasons, teams)
                    .forEach(ts -> variants.names.computeIfAbsent(ts.season.id, key -> new HashMap<>())
                            .put(ts.team.id, ts.name));
        }
        if (!matchDays.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<UUID> rows = Panache.getEntityManager().createNativeQuery(
                            "select distinct event_id from match where event_id in (:ids) and source = 'SWISS_UNIHOCKEY'")
                    .setParameter("ids", matchDays).getResultList();
            variants.imported.addAll(rows);
        }
        return variants;
    }

    public static EventVariant of(Event event) {
        return of(List.of(event)).resolve(event);
    }

    /** The variant of the event. Never {@code null}: a team without stored variants gets the defaults. */
    public EventVariant resolve(Event event) {
        EventVariant chosen = event.variantId == null ? null : byId.get(event.variantId);
        if (chosen != null && chosen.eventType == event.type) {
            return chosen;
        }
        VariantKind kind = VariantKind.standardFor(event.type, imported.contains(event.id));
        UUID team = leadingTeam(event);
        EventVariant found = team == null ? null : standard.getOrDefault(team, Map.of()).get(kind);
        return found != null ? found : defaults(team, kind);
    }

    /** The team of the event that comes first by name in the season of the event. */
    private UUID leadingTeam(Event event) {
        Map<UUID, String> inSeason = names.getOrDefault(event.season.id, Map.of());
        return event.teams.stream().map(team -> team.id)
                .min(Comparator.comparing((UUID id) -> inSeason.getOrDefault(id, ""), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.naturalOrder()))
                .orElse(null);
    }

    /** A standard variant as it is created for a new team; not stored. */
    static EventVariant defaults(UUID teamId, VariantKind kind) {
        EventVariant variant = new EventVariant();
        variant.id = VariantRules.standardId(teamId == null ? new UUID(0, 0) : teamId, kind);
        variant.teamId = teamId;
        variant.kind = kind;
        variant.eventType = kind.eventType();
        variant.name = VariantRules.defaultName(kind);
        variant.color = VariantRules.defaultColor(kind);
        variant.sections = VariantRules.joinSections(VariantRules.defaultSections(kind.eventType()));
        variant.sortOrder = kind.ordinal() + 1;
        variant.createdAt = Instant.EPOCH;
        return variant;
    }
}
