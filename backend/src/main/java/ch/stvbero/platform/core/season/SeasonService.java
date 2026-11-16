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

/** Saisons anlegen, übernehmen und wechseln (SAI-03, SAI-04, SAI-06). */
@ApplicationScoped
public class SeasonService {

    @Inject
    EntityManager em;

    /** Alle Saisons, neueste zuerst. */
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

        Season newValue = new Season();
        newValue.id = UUID.randomUUID();
        newValue.name = input.name().strip();
        newValue.startDate = input.startDate();
        newValue.endDate = input.endDate();
        newValue.current = false;
        newValue.persist();
        // Gleichzeitiges Anlegen derselben Saison (Doppelklick): 409 statt 500 (SAI-01).
        Unique.save(em, "season_name_key",
                () -> DomainError.conflict("Name vergeben", "Es gibt schon eine Saison «" + newValue.name + "».", "SAI-01"));

        if (source != null) {
            apply(source, newValue);
        }
        return SeasonDto.of(newValue, Season.current().orElse(null));
    }

    /** Teams, Teamzugehörigkeiten und Linien der Vorsaison als Ausgangslage (SAI-04, LIN-11). */
    private void apply(Season source, Season target) {
        // Das Team behält in der neuen Saison seinen Namen aus der Vorsaison (SAI-02: Namen gelten pro Saison).
        List<TeamSeason> teams = TeamSeason.list("season.id", source.id);
        for (TeamSeason ts : teams) {
            ts.team.addToSeason(target, ts.name);
        }
        List<TeamMembership> memberships = TeamMembership.list("season.id", source.id);
        Map<TeamMembership, TeamMembership> copies = new LinkedHashMap<>();
        for (TeamMembership z : memberships) {
            TeamMembership copy = TeamMembership.create(z.person, z.team, target, z.teamRole);
            copy.persist();
            copies.put(z, copy);
        }
        LineService.apply(source, target, copies);
    }

    /** Zur aktuellen Saison machen. Es gibt immer genau eine aktuelle Saison (SAI-03). */
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
