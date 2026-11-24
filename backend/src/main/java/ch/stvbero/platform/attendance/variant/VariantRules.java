package ch.stvbero.platform.attendance.variant;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.EventType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Domain rules of the event variants (VAR). Without a database, so that unit tests can check them. */
public final class VariantRules {

    /** At most this many variants per team, the four standard ones included (VAR-02). */
    public static final int MAX_PER_TEAM = 12;

    public static final String TOO_MANY = "Ein Team hat höchstens " + MAX_PER_TEAM + " Terminvarianten.";
    public static final String STANDARD_STAYS = "Die Standardvarianten gehören zu jedem Team und lassen sich nicht löschen.";
    public static final String NAME_TAKEN = "Diesen Namen hat schon eine andere Variante dieses Teams.";
    public static final String DETAILS_STAY = "Der Abschnitt «Details» lässt sich nicht ausblenden.";
    public static final String NO_CALL_UP = "Für diese Terminvariante gibt es kein Aufgebot. Es zählen die An- und Abmeldungen.";

    private static final List<Section> MATCH_DAY_SECTIONS = List.of(Section.RESPONSES, Section.MATCHES,
            Section.CALL_UP, Section.TIMELINE, Section.DRIVER_LIST, Section.LINEUPS, Section.ATTENDANCE,
            Section.DETAILS);
    private static final List<Section> PLAIN_SECTIONS = List.of(Section.RESPONSES, Section.ATTENDANCE,
            Section.DETAILS);

    /** Makes sure every team has its four standard variants (VAR-01). Changes nothing for teams that have them. */
    public static final String ENSURE_STANDARD_SQL = """
            insert into event_variant (id, team_id, kind, event_type, name, color, sections, sort_order)
            select md5(t.id::text || d.kind)::uuid, t.id, d.kind, d.event_type, d.name, d.color, d.sections, d.sort_order
            from team t
            cross join (values
                ('PRACTICE', 'PRACTICE', 'Training', 'GREEN', 'RESPONSES,ATTENDANCE,DETAILS', 1),
                ('MATCH_DAY', 'MATCH_DAY', 'Spieltag', 'VIOLET',
                 'RESPONSES,MATCHES,CALL_UP,TIMELINE,DRIVER_LIST,LINEUPS,ATTENDANCE,DETAILS', 2),
                ('SU_MATCH_DAY', 'MATCH_DAY', 'swiss unihockey Spieltag', 'BLUE',
                 'RESPONSES,MATCHES,CALL_UP,TIMELINE,DRIVER_LIST,LINEUPS,ATTENDANCE,DETAILS', 3),
                ('OTHER', 'OTHER', 'Anlass', 'PINK', 'RESPONSES,ATTENDANCE,DETAILS', 4)
            ) as d (kind, event_type, name, color, sections, sort_order)
            on conflict do nothing""";

    private VariantRules() {
    }

    /** ID of a standard variant: derived from team and kind, the same as in the database (md5 as UUID). */
    public static UUID standardId(UUID teamId, VariantKind kind) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5")
                    .digest((teamId.toString() + kind.name()).getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                    + hex.substring(16, 20) + "-" + hex.substring(20));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sections an event of this type can show (VAR-07). */
    public static List<Section> allowedSections(EventType type) {
        return type == EventType.MATCH_DAY ? MATCH_DAY_SECTIONS : PLAIN_SECTIONS;
    }

    /** Default sections of a variant: all the type has, «Anmeldungen» first (VAR-07). */
    public static List<Section> defaultSections(EventType type) {
        return allowedSections(type);
    }

    public static VariantColor defaultColor(VariantKind kind) {
        return switch (kind) {
            case PRACTICE -> VariantColor.GREEN;
            case MATCH_DAY -> VariantColor.VIOLET;
            case SU_MATCH_DAY -> VariantColor.BLUE;
            case OTHER -> VariantColor.PINK;
            case CUSTOM -> VariantColor.GREY;
        };
    }

    public static String defaultName(VariantKind kind) {
        return switch (kind) {
            case PRACTICE -> "Training";
            case MATCH_DAY -> "Spieltag";
            case SU_MATCH_DAY -> "swiss unihockey Spieltag";
            case OTHER -> "Anlass";
            case CUSTOM -> "Variante";
        };
    }

    /**
     * The sections as stored: each at most once, only those the type has, and «Details» among them (VAR-07).
     * {@code null} or empty: the defaults.
     */
    public static List<Section> checkSections(EventType type, List<Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return defaultSections(type);
        }
        Set<Section> unique = new LinkedHashSet<>(sections);
        if (unique.size() != sections.size()) {
            throw DomainError.invalidField("sections", "Jeder Abschnitt kommt höchstens einmal vor.");
        }
        List<Section> allowed = allowedSections(type);
        if (!allowed.containsAll(unique)) {
            throw DomainError.invalidField("sections", "Diesen Abschnitt gibt es für diese Art von Termin nicht.");
        }
        if (!unique.contains(Section.DETAILS)) {
            throw DomainError.invalidField("sections", DETAILS_STAY);
        }
        return List.copyOf(unique);
    }

    public static String joinSections(List<Section> sections) {
        return sections.stream().map(Section::name).collect(Collectors.joining(","));
    }

    /** Reads stored sections; unknown names are skipped, and «Details» is always there. */
    public static List<Section> parseSections(String stored) {
        List<Section> sections = new ArrayList<>();
        if (stored != null) {
            Arrays.stream(stored.split(",")).map(String::strip).filter(name -> !name.isEmpty()).forEach(name -> {
                try {
                    Section section = Section.valueOf(name);
                    if (!sections.contains(section)) {
                        sections.add(section);
                    }
                } catch (IllegalArgumentException unknown) {
                    // a section that no longer exists
                }
            });
        }
        if (!sections.contains(Section.DETAILS)) {
            sections.add(Section.DETAILS);
        }
        return List.copyOf(sections);
    }

    /** A team has at most {@link #MAX_PER_TEAM} variants (VAR-02). */
    public static void checkRoom(long existing) {
        if (existing >= MAX_PER_TEAM) {
            throw DomainError.conflict("Zu viele Varianten", TOO_MANY, "VAR-02");
        }
    }

    /** Standard variants cannot be deleted (VAR-01). */
    public static void checkDeletable(VariantKind kind) {
        if (kind != VariantKind.CUSTOM) {
            throw DomainError.conflict("Standardvariante", STANDARD_STAYS, "VAR-01");
        }
    }

    /** The call-up exists only where the variant makes one (VAR-06, AUF-33). */
    public static void checkCallUp(boolean callUp) {
        if (!callUp) {
            throw DomainError.conflict("Kein Aufgebot", NO_CALL_UP, "VAR-06");
        }
    }

    /** A variant chosen for an event fits its type and belongs to one of its teams (VAR-03). */
    public static void checkFits(EventType variantType, EventType eventType, boolean teamOfEvent) {
        if (!teamOfEvent) {
            throw DomainError.invalidField("variantId", "Diese Variante gehört zu keinem Team dieses Termins.");
        }
        if (variantType != eventType) {
            throw DomainError.invalidField("variantId", "Diese Variante passt nicht zur Art des Termins.");
        }
    }
}
