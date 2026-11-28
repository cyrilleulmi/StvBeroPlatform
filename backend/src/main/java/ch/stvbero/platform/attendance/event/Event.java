package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Something people respond to (TER-02). Belongs to one or more teams (TER-01, TER-03) and to the season its
 * date falls into. Only PLANNED or CANCELLED is stored.
 */
@Entity
@Table(name = "event")
public class Event extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id")
    public Season season;

    @Enumerated(EnumType.STRING)
    public EventType type;

    public String title;

    @Column(name = "starts_at")
    public Instant start;

    @Column(name = "ends_at")
    public Instant end;

    @Enumerated(EnumType.STRING)
    public EventStatus status = EventStatus.PLANNED;

    @Column(name = "cancellation_reason")
    public String cancellationReason;

    public boolean rescheduled;

    @Embedded
    public Location location;

    public String notes;

    @Column(name = "response_deadline_hours")
    public int responseDeadlineHours = EventRules.DEFAULT_RESPONSE_DEADLINE;

    @Column(name = "meeting_point_time")
    public Instant meetingPointTime;

    @Column(name = "meeting_point_location")
    public String meetingPointLocation;

    /** Assumed number of people who travel to this match day (DRV-29); missing: the number of the team. */
    @Column(name = "expected_travellers")
    public Integer expectedTravellers;

    /** The meeting time was set by the automatic departure, not by hand (TML-09). */
    @Column(name = "meeting_point_time_auto")
    public boolean meetingPointTimeAuto;

    /** Variant chosen for this event (VAR-03); {@code null}: the standard variant of its type. */
    @Column(name = "variant_id")
    public UUID variantId;

    @ManyToOne
    @JoinColumn(name = "series_id")
    public Series series;

    /** Date in the series that created this event. Stays the same when the event is rescheduled. */
    @Column(name = "series_date")
    public LocalDate seriesDate;

    /** Individually changed: changes to the series no longer apply to this event (TER-06). */
    @Column(name = "individually_changed")
    public boolean individuallyChanged;

    /** Switch «Präsenz erfassen», off by default (PRS-01, E-70). */
    @Column(name = "attendance_check")
    public boolean attendanceCheck;

    /** Pinned to the start page of the members of its teams since then (TER-26); {@code null}: not pinned. */
    @Column(name = "pinned_at")
    public Instant pinnedAt;

    /** Who pinned it (TER-26); {@code null} when not pinned or the person was deleted. */
    @Column(name = "pinned_by")
    public UUID pinnedBy;

    @ManyToMany
    @JoinTable(name = "event_team",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    public Set<Team> teams = new LinkedHashSet<>();

    /** Teams invited without obligation («freiwillig», TER-15). A subset of {@link #teams}. */
    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "event_optional_team", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "team_id")
    public Set<UUID> optionalTeamIds = new LinkedHashSet<>();

    public static Event create() {
        Event event = new Event();
        event.id = UUID.randomUUID();
        return event;
    }

    public boolean isPinned() {
        return pinnedAt != null;
    }

    public boolean isOver(Instant now) {
        return !end.isAfter(now);
    }
}
