package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.season.Season;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A team of the club (PTF-02). Belongs to one or more seasons.
 * The name applies per season ({@link TeamSeason}), so that the archive stays unchanged (SAI-02).
 */
@Entity
@Table(name = "team")
public class Team extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Minimums for the warnings (F-16), see {@link WarningMinimums}. */
    @Column(name = "min_goalies")
    public int minGoalies = WarningMinimums.DEFAULTS.goalies();

    @Column(name = "min_field_players_match_day")
    public int minFieldPlayersMatchDay = WarningMinimums.DEFAULTS.fieldPlayersMatchDay();

    @Column(name = "min_field_players_practice")
    public int minFieldPlayersPractice = WarningMinimums.DEFAULTS.fieldPlayersPractice();

    @Column(name = "min_per_line")
    public int minPerLine = WarningMinimums.DEFAULTS.perLine();

    /** Match day settings (TML-02, TML-03, AUF-32), see {@link MatchDaySettings}. */
    @Column(name = "match_duration_minutes")
    public int matchDurationMinutes = MatchDaySettings.DEFAULTS.matchDurationMinutes();

    @Column(name = "arrival_lead_minutes")
    public int arrivalLeadMinutes = MatchDaySettings.DEFAULTS.arrivalLeadMinutes();

    @Column(name = "meeting_point_location")
    public String meetingPointLocation;

    @OneToMany(mappedBy = "team", cascade = CascadeType.PERSIST)
    public List<TeamSeason> seasons = new ArrayList<>();

    public MatchDaySettings matchDaySettings() {
        return new MatchDaySettings(matchDurationMinutes, arrivalLeadMinutes, meetingPointLocation);
    }

    public void setMatchDaySettings(MatchDaySettings settings) {
        matchDurationMinutes = settings.matchDurationMinutes();
        arrivalLeadMinutes = settings.arrivalLeadMinutes();
        meetingPointLocation = settings.meetingPoint() == null || settings.meetingPoint().isBlank()
                ? null : settings.meetingPoint().strip();
    }

    /** Team ID at swiss unihockey in this season: the primary link (SUH-11), or {@code null}. */
    public Integer suTeamIdIn(Season season) {
        return SuLink.primary(id, season.id).map(link -> link.suTeamId).orElse(null);
    }

    public WarningMinimums minimums() {
        return new WarningMinimums(minGoalies, minFieldPlayersMatchDay, minFieldPlayersPractice, minPerLine);
    }

    public void setMinimums(WarningMinimums minimums) {
        minGoalies = minimums.goalies();
        minFieldPlayersMatchDay = minimums.fieldPlayersMatchDay();
        minFieldPlayersPractice = minimums.fieldPlayersPractice();
        minPerLine = minimums.perLine();
    }

    public Optional<TeamSeason> in(Season season) {
        return seasons.stream().filter(ts -> ts.season.id.equals(season.id)).findFirst();
    }

    public boolean belongsTo(Season season) {
        return in(season).isPresent();
    }

    /** Name in this season. */
    public String nameIn(Season season) {
        return in(season).map(ts -> ts.name)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht."));
    }

    /**
     * Adds the team to the season with the given name. It gets its goalies there (LIN-05).
     * If it already belongs to the season, everything stays as it is.
     */
    public void addToSeason(Season season, String name) {
        if (!belongsTo(season)) {
            TeamSeason ts = TeamSeason.create(this, season, name);
            seasons.add(ts);
            ts.persist();
            Group.goalies(this, season).persist();
        }
    }
}
