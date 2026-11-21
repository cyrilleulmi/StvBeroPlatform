package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.season.Season;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Link of a team to a team at swiss unihockey in one season (SUH-02, SUH-11). A team can have several links per
 * season, and other ones in the next season. The state of the last sync is kept per link (SUH-13).
 */
@Entity
@Table(name = "su_link")
public class SuLink extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id")
    public Season season;

    @Column(name = "su_team_id")
    public int suTeamId;

    /** Name of the team at swiss unihockey when it was linked, e.g. «Damen KF 1. Liga». */
    @Column(name = "su_team_name")
    public String suTeamName;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "last_sync_at")
    public Instant lastSyncAt;

    /** {@code null}: never synced. */
    @Column(name = "last_sync_ok")
    public Boolean lastSyncOk;

    @Column(name = "last_sync_error")
    public String lastSyncError;

    public static SuLink create(Team team, Season season, int suTeamId, String suTeamName, Instant now) {
        SuLink link = new SuLink();
        link.id = UUID.randomUUID();
        link.team = team;
        link.season = season;
        link.suTeamId = suTeamId;
        link.suTeamName = suTeamName;
        link.createdAt = now;
        return link;
    }

    /** Links of a team in a season, the oldest first. The first one is the primary link (league page, F-17). */
    public static List<SuLink> of(UUID teamId, UUID seasonId) {
        return list("team.id = ?1 and season.id = ?2 order by createdAt, suTeamId", teamId, seasonId);
    }

    /** The primary link of a team in a season. */
    public static Optional<SuLink> primary(UUID teamId, UUID seasonId) {
        return of(teamId, seasonId).stream().findFirst();
    }

    /** Team ID at swiss unihockey of the primary link per team of a season. */
    public static Map<UUID, Integer> primaryIds(UUID seasonId) {
        Map<UUID, Integer> ids = new LinkedHashMap<>();
        SuLink.<SuLink>list("season.id = ?1 order by createdAt, suTeamId", seasonId)
                .forEach(link -> ids.putIfAbsent(link.team.id, link.suTeamId));
        return ids;
    }
}
