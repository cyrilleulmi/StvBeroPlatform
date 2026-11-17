package ch.stvbero.platform.core.season;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.line.LineService;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.core.team.TeamMembership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creating seasons, carrying them over and switching the current one (SAI-03, SAI-04, SAI-06). */
@ApplicationScoped
public class SeasonService {

    @Inject
    EntityManager em;

    /** All seasons, newest first. */
    public List<SeasonDto> list() {
        Season current = Season.current().orElse(null);
        return Season.<Season>list("order by startDate desc").stream().map(s -> SeasonDto.of(s, current)).toList();
    }

    @Transactional
    public SeasonDto create(CurrentUser user, NewSeason input) {
        user.checkAdmin();
        Season source = input.copyFrom() == null ? null
                : Season.<Season>findByIdOptional(input.copyFrom())
                .orElseThrow(() -> DomainError.notFound("Die Saison zum Übernehmen gibt es nicht."));

        List<SeasonRules.Period> existing = Season.<Season>listAll().stream()
                .map(s -> new SeasonRules.Period(s.name, s.startDate, s.endDate)).toList();
        SeasonRules.checkNewSeason(new SeasonRules.Period(input.name(), input.startDate(), input.endDate()), existing);

        Season season = new Season();
        season.id = UUID.randomUUID();
        season.name = input.name().strip();
        season.startDate = input.startDate();
        season.endDate = input.endDate();
        season.current = false;
        season.persist();
        // The same season created twice at the same time (double click): 409 instead of 500 (SAI-01).
        Unique.save(em, "season_name_key",
                () -> DomainError.conflict("Name vergeben", "Es gibt schon eine Saison «" + season.name + "».", "SAI-01"));

        if (source != null) {
            carryOver(source, season);
        }
        return SeasonDto.of(season, Season.current().orElse(null));
    }

    /** Carry-over: teams, team memberships and lines of the previous season as a starting point (SAI-04, LIN-11). */
    private void carryOver(Season source, Season target) {
        // The team keeps its name from the previous season in the new one (SAI-02: names apply per season).
        List<TeamSeason> teams = TeamSeason.list("season.id", source.id);
        for (TeamSeason ts : teams) {
            ts.team.addToSeason(target, ts.name);
        }
        List<TeamMembership> memberships = TeamMembership.list("season.id", source.id);
        Map<TeamMembership, TeamMembership> copies = new LinkedHashMap<>();
        for (TeamMembership membership : memberships) {
            TeamMembership copy = TeamMembership.create(membership.person, membership.team, target, membership.teamRole);
            copy.persist();
            copies.put(membership, copy);
        }
        LineService.carryOver(source, target, copies);
    }

    /** Makes the season the current one. There is always exactly one current season (SAI-03). */
    @Transactional
    public void activate(CurrentUser user, UUID id) {
        user.checkAdmin();
        Season season = Season.<Season>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Saison gibt es nicht."));
        if (season.current) {
            return;
        }
        Season.update("current = false where current = true");
        em.flush();
        em.refresh(season);
        season.current = true;
    }
}
