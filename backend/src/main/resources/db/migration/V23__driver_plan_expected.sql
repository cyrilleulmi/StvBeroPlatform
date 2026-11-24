-- Assumed number of people who travel to a match day (DRV-29): per team and season, and per match day.
-- Missing: all players and coaches of the team.
alter table team_season add column expected_travellers integer;
alter table team_season add constraint team_season_expected_travellers
    check (expected_travellers is null or expected_travellers between 1 and 60);
alter table event add column expected_travellers integer;
alter table event add constraint event_expected_travellers
    check (expected_travellers is null or expected_travellers between 1 and 60);
