package ch.stvbero.platform.core.variant;

import ch.stvbero.platform.core.event.EventType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * What a variant is (VAR-01, VAR-02): one of the four standard variants every team has, or a variant of the
 * team's own.
 */
@Schema(name = "VariantKind")
public enum VariantKind {
    PRACTICE(EventType.PRACTICE),
    MATCH_DAY(EventType.MATCH_DAY),
    /** Match day whose matches come from the sync with swiss unihockey. */
    SU_MATCH_DAY(EventType.MATCH_DAY),
    OTHER(EventType.OTHER),
    CUSTOM(null);

    private final EventType eventType;

    VariantKind(EventType eventType) {
        this.eventType = eventType;
    }

    /** Event type of a standard variant; {@code null} for a variant of one's own, which chooses it. */
    public EventType eventType() {
        return eventType;
    }

    /** The standard variant of an event without a chosen variant (VAR-03). */
    public static VariantKind standardFor(EventType type, boolean imported) {
        return switch (type) {
            case PRACTICE -> PRACTICE;
            case OTHER -> OTHER;
            case MATCH_DAY -> imported ? SU_MATCH_DAY : MATCH_DAY;
        };
    }
}
