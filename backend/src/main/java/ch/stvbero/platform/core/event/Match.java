package ch.stvbero.platform.core.event;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Einzelne Partie eines Spieltags mit Gegner, Anspielzeit, Heim/Auswärts und Resultat (AUF-14, AUF-15). */
@Entity
@Table(name = "match")
public class Match extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id")
    public Event event;

    public String opponent;

    @Column(name = "start_time")
    public Instant startTime;

    public boolean home;

    /** Heim:Gast, z.B. «5:3». */
    public String result;

    @Enumerated(EnumType.STRING)
    public MatchSource source = MatchSource.MANUAL;

    @Column(name = "su_match_id")
    public Integer suMatchId;

    @Column(name = "missing_at_su")
    public boolean missingAtSu;

    public static Match create(Event event) {
        Match s = new Match();
        s.id = UUID.randomUUID();
        s.event = event;
        return s;
    }

    /** Spiele eines Spieltags nach Anspielzeit (AUF-14). */
    public static List<Match> of(UUID eventId) {
        return list("event.id = ?1 order by startTime, opponent, id", eventId);
    }

    public MatchDto dto() {
        return new MatchDto(id, opponent, EventReader.time(startTime), home, result, source, suMatchId,
                missingAtSu);
    }
}
