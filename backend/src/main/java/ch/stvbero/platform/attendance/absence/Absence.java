package ch.stvbero.platform.attendance.absence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A longer time in which a person is away, e.g. holidays or an injury (ABS). Days inclusive. */
@Entity
@Table(name = "absence")
public class Absence extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "person_id")
    public UUID personId;

    @Column(name = "first_day")
    public LocalDate firstDay;

    @Column(name = "last_day")
    public LocalDate lastDay;

    @Enumerated(EnumType.STRING)
    public AbsenceReason reason;

    public String note;

    @Column(name = "created_at")
    public Instant createdAt;

    public static Absence create(UUID personId) {
        Absence absence = new Absence();
        absence.id = UUID.randomUUID();
        absence.personId = personId;
        return absence;
    }

    /** All absences of a person, by first day. */
    public static List<Absence> of(UUID personId) {
        return list("personId = ?1 order by firstDay, id", personId);
    }

    AbsenceRules.Period period() {
        return new AbsenceRules.Period(id, firstDay, lastDay, AbsenceRules.comment(reason, note));
    }
}
