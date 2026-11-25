package ch.stvbero.platform.core.season;

/**
 * Extension point of the carry-over (SAI-04): a module copies what it keeps per season into the new season. Called
 * in the transaction of the carry-over, after teams, team memberships and lines exist in the new season.
 */
public interface SeasonCarryOver {

    void carryOver(Season source, Season target);
}
