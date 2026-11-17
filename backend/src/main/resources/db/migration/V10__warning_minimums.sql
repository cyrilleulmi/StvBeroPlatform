-- F-16 · Warnings for events and call-ups

-- Minimums per team below which coaches see a warning. Defaults: 1 goalie, 10 field players for match days,
-- 6 for practices, 3 per line. A value of 0 switches the warning off.
alter table team add column min_goalies integer not null default 1;
alter table team add column min_field_players_match_day integer not null default 10;
alter table team add column min_field_players_practice integer not null default 6;
alter table team add column min_per_line integer not null default 3;
alter table team add constraint team_minimums_range check (
    min_goalies between 0 and 99 and min_field_players_match_day between 0 and 99
    and min_field_players_practice between 0 and 99 and min_per_line between 0 and 99);
