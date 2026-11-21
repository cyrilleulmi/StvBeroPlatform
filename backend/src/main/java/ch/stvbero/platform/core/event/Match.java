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

/** A single match of a match day with opponent, start time, home/away and result (AUF-14, AUF-15). */
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

    /** Home:away, e.g. «5:3». */
    public String result;

    @Enumerated(EnumType.STRING)
    public MatchSource source = MatchSource.MANUAL;

    @Column(name = "su_match_id")
    public Integer suMatchId;

    @Column(name = "missing_at_su")
    public boolean missingAtSu;

    /**
     * swiss unihockey changed this match and no coach or admin has confirmed it yet (SUH-12): time of the last
     * change. The {@code suPrevious…} fields hold the values before the first unconfirmed change.
     */
    @Column(name = "su_changed_at")
    public Instant suChangedAt;

    @Column(name = "su_previous_start")
    public Instant suPreviousStart;

    @Column(name = "su_previous_opponent")
    public String suPreviousOpponent;

    @Column(name = "su_previous_home")
    public Boolean suPreviousHome;

    @Column(name = "su_previous_venue")
    public String suPreviousVenue;

    public static Match create(Event event) {
        Match match = new Match();
        match.id = UUID.randomUUID();
        match.event = event;
        return match;
    }

    /** Matches of a match day by start time (AUF-14). */
    public static List<Match> of(UUID eventId) {
        return list("event.id = ?1 order by startTime, opponent, id", eventId);
    }

    public MatchDto dto() {
        return dto(null);
    }

    /** With the rank of the opponent in the league table, if known (AUF-34). */
    public MatchDto dto(Integer opponentRank) {
        return new MatchDto(id, opponent, EventReader.time(startTime), home, result, source, suMatchId,
                missingAtSu, suChange(), opponentRank);
    }

    /** The unconfirmed change by swiss unihockey, or {@code null} (SUH-12). */
    public SuChangeDto suChange() {
        if (suChangedAt == null) {
            return null;
        }
        List<SuChangeDto.Item> changes = MatchRules.suChanges(
                new MatchRules.SuState(suPreviousStart, suPreviousOpponent, suPreviousHome, suPreviousVenue),
                new MatchRules.SuState(startTime, opponent, home,
                        event.location == null ? null : event.location.name));
        return changes.isEmpty() ? null : new SuChangeDto(EventReader.time(suChangedAt), changes);
    }

    /** Remembers the values before a change by swiss unihockey, unless an unconfirmed change is already noted. */
    public void noteSuChange(Instant now, String venueBefore) {
        if (suChangedAt == null) {
            suPreviousStart = startTime;
            suPreviousOpponent = opponent;
            suPreviousHome = home;
            suPreviousVenue = venueBefore;
        }
        suChangedAt = now;
    }

    /** A coach or admin has seen the change (SUH-12), or swiss unihockey changed everything back. */
    public void clearSuChange() {
        suChangedAt = null;
        suPreviousStart = null;
        suPreviousOpponent = null;
        suPreviousHome = null;
        suPreviousVenue = null;
    }
}
