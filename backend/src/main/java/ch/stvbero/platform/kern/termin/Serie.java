package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.Team;
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
@Table(name = "serie")
public class Serie extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "saison_id")
    public Saison saison;

    @Enumerated(EnumType.STRING)
    public SerienTyp typ;

    public String titel;

    @Enumerated(EnumType.STRING)
    public Wochentag wochentag;

    @Column(name = "beginn_zeit")
    public LocalTime beginnZeit;

    @Column(name = "ende_zeit")
    public LocalTime endeZeit;

    @Column(name = "gueltig_von")
    public LocalDate gueltigVon;

    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    @Embedded
    public Ort ort;

    @Column(name = "antwortfrist_stunden")
    public int antwortfristStunden = TerminRegeln.ANTWORTFRIST_STANDARD;

    @ManyToMany
    @JoinTable(name = "serie_team",
            joinColumns = @JoinColumn(name = "serie_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    public Set<Team> teams = new LinkedHashSet<>();

    @OneToMany(mappedBy = "serie", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Ausnahme> ausnahmen = new ArrayList<>();

    public List<TerminRegeln.Zeitraum> ausnahmeZeitraeume() {
        return ausnahmen.stream().map(a -> new TerminRegeln.Zeitraum(a.von, a.bis)).toList();
    }
}
