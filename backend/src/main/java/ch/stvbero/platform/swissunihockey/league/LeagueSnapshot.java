package ch.stvbero.platform.swissunihockey.league;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached league data of a linked team (F-17, criterion 6). Written by the daily sync and the manual reload, read
 * by the league page. Table and matches are kept as JSON, as delivered.
 */
@Entity
@Table(name = "league_snapshot")
public class LeagueSnapshot extends PanacheEntityBase {

    @Id
    @Column(name = "team_id")
    public UUID teamId;

    @Column(name = "season_id")
    public UUID seasonId;

    public String league;

    @Column(name = "group_name")
    public String group;

    /** JSON list of {@link ch.stvbero.platform.swissunihockey.SuStanding}. */
    public String standings = "[]";

    /** JSON list of {@link ch.stvbero.platform.swissunihockey.SuMatch}. */
    public String matches = "[]";

    /** Last successful load («Stand»), {@code null} if never loaded in this season. */
    @Column(name = "loaded_at")
    public Instant loadedAt;

    /** Last failed attempt after the last successful load (SUH-09), {@code null} if the last attempt worked. */
    @Column(name = "failed_at")
    public Instant failedAt;
}
