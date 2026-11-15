package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.Team;
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
 * Etwas, wofür man sich an- oder abmeldet (TER-02). Gehört zu einem oder mehreren Teams (TER-01, TER-03)
 * und zur Saison, in die sein Datum fällt. Gespeichert wird nur GEPLANT oder ABGESAGT.
 */
@Entity
@Table(name = "termin")
public class Termin extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "saison_id")
    public Saison saison;

    @Enumerated(EnumType.STRING)
    public TerminTyp typ;

    public String titel;

    public Instant beginn;

    public Instant ende;

    @Enumerated(EnumType.STRING)
    public TerminStatus status = TerminStatus.GEPLANT;

    public String absagegrund;

    public boolean verschoben;

    @Embedded
    public Ort ort;

    public String hinweise;

    @Column(name = "antwortfrist_stunden")
    public int antwortfristStunden = TerminRegeln.ANTWORTFRIST_STANDARD;

    @Column(name = "treffpunkt_zeit")
    public Instant treffpunktZeit;

    @Column(name = "treffpunkt_ort")
    public String treffpunktOrt;

    @ManyToOne
    @JoinColumn(name = "serie_id")
    public Serie serie;

    /** Datum in der Serie, das diesen Termin erzeugt hat. Bleibt beim Verschieben gleich. */
    @Column(name = "serie_datum")
    public LocalDate serieDatum;

    /** Einzeln geändert: Änderungen an der Serie gelten für diesen Termin nicht mehr (TER-06). */
    @Column(name = "einzeln_geaendert")
    public boolean einzelnGeaendert;

    @ManyToMany
    @JoinTable(name = "termin_team",
            joinColumns = @JoinColumn(name = "termin_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    public Set<Team> teams = new LinkedHashSet<>();

    public static Termin neu() {
        Termin t = new Termin();
        t.id = UUID.randomUUID();
        return t;
    }

    public boolean istVorbei(Instant jetzt) {
        return !ende.isAfter(jetzt);
    }
}
