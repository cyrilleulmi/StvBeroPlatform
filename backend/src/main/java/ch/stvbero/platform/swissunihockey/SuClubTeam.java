package ch.stvbero.platform.swissunihockey;

/**
 * A team of the club as swiss unihockey lists it for a season (SUH-02), e.g. team ID 428361 «Damen KF 1. Liga».
 */
public record SuClubTeam(int suTeamId, String name) {
}
