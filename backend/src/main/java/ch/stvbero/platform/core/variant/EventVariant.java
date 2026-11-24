package ch.stvbero.platform.core.variant;

import ch.stvbero.platform.core.event.EventType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * How a kind of event looks and behaves in a team (VAR): name, colour, call-up or responses only, the sections
 * of the event page and their order, automatic departure and the defaults for new events. Applies to the team in
 * every season (VAR-10).
 */
@Entity
@Table(name = "event_variant")
public class EventVariant extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "team_id")
    public UUID teamId;

    @Enumerated(EnumType.STRING)
    public VariantKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    public EventType eventType;

    public String name;

    @Enumerated(EnumType.STRING)
    public VariantColor color;

    /** A call-up is made (VAR-06). Only has an effect for match days. */
    @Column(name = "call_up")
    public boolean callUp;

    /** The meeting time follows the suggested departure (VAR-08, TML-09). Only has an effect for match days. */
    @Column(name = "auto_departure")
    public boolean autoDeparture = true;

    @Column(name = "response_deadline_hours")
    public int responseDeadlineHours = 24;

    @Column(name = "attendance_check")
    public boolean attendanceCheck;

    /** «nicht dabei» needs a reason (VAR-12, ANM-05). */
    @Column(name = "decline_reason_required")
    public boolean declineReasonRequired;

    /** Shown sections in their order, comma separated ({@link Section}). */
    public String sections;

    @Column(name = "sort_order")
    public int sortOrder;

    @Column(name = "created_at")
    public Instant createdAt;

    public List<Section> sectionList() {
        return VariantRules.parseSections(sections);
    }

    public boolean standard() {
        return kind != VariantKind.CUSTOM;
    }

    public EventVariantDto dto() {
        return new EventVariantDto(id, teamId, kind, eventType, name, color, callUp, autoDeparture,
                responseDeadlineHours, attendanceCheck, declineReasonRequired, sectionList(), standard());
    }

    /** All variants of a team: the standard ones first, then the team's own in the order they were added. */
    public static List<EventVariant> of(UUID teamId) {
        return list("teamId = ?1 order by sortOrder, createdAt, id", teamId);
    }
}
