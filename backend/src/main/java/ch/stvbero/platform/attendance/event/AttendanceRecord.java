package ch.stvbero.platform.attendance.event;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recorded attendance of an event (PRS-03, PRS-04): who recorded it last and when, and the status per player.
 * Kept when the check is switched off (PRS-08).
 */
@Entity
@Table(name = "attendance_record")
public class AttendanceRecord extends PanacheEntityBase {

    @Id
    @Column(name = "event_id")
    public UUID eventId;

    @Column(name = "recorded_at")
    public Instant recordedAt;

    @Column(name = "recorded_by")
    public UUID recordedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attendance_entry", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "person_id")
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    public Map<UUID, AttendanceStatus> entries = new HashMap<>();
}
