package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Regel, die wiederkehrende Termine erzeugt, z.B. «jeden Dienstag 18:30» (TER-05). */
@Entity
@Table(name = "series")
public class Series extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id")
    public Season season;

    @Enumerated(EnumType.STRING)
    public SeriesType type;

    public String title;

    @Enumerated(EnumType.STRING)
    public Weekday weekday;

    @Column(name = "start_time")
    public LocalTime startTime;

    @Column(name = "end_time")
    public LocalTime endTime;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_until")
    public LocalDate validUntil;

    @Embedded
    public Location location;

    @Column(name = "response_deadline_hours")
    public int responseDeadlineHours = EventRules.DEFAULT_RESPONSE_DEADLINE;

    @ManyToMany
    @JoinTable(name = "series_team",
            joinColumns = @JoinColumn(name = "series_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    public Set<Team> teams = new LinkedHashSet<>();

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<SeriesException> exceptions = new ArrayList<>();

    public List<EventRules.Period> exceptionPeriods() {
        return exceptions.stream().map(a -> new EventRules.Period(a.startDate, a.endDate)).toList();
    }
}
