-- F-02 · Lines (LIN)

-- Line or goalies of a team in a season (LIN-03, LIN-05, LIN-11).
-- The table is called line_group because "group" is a reserved word in SQL.
create table line_group (
    id          uuid primary key,
    team_id     uuid        not null,
    season_id   uuid        not null,
    type        varchar(20) not null,
    name        varchar(30) not null,
    -- Only lines have a sort order (LIN-09)
    sort_order  integer,
    constraint line_group_team_season foreign key (team_id, season_id)
        references team_season (team_id, season_id) on delete cascade,
    constraint line_group_sort_order_only_line check ((type = 'LINE') = (sort_order is not null))
);

create index line_group_team_season_idx on line_group (team_id, season_id);
-- Goalies exist exactly once per team and season (LIN-05)
create unique index line_group_goalies_once on line_group (team_id, season_id) where type = 'GOALIES';
-- Names are unique per team and season (assumption on LIN-09)
create unique index line_group_name_unique on line_group (team_id, season_id, lower(name));

-- Assignment of a player (through her team membership) to a group (LIN-01).
-- A separate table, so that «several lines per player» can come later without rework (LIN-04).
create table group_assignment (
    team_membership_id uuid not null references team_membership (id) on delete cascade,
    group_id           uuid not null references line_group (id) on delete cascade,
    primary key (team_membership_id, group_id)
);

create index group_assignment_group on group_assignment (group_id);
-- LIN-02 as a rule: at most one group per player and team. The team membership applies per team
-- and season, so this index is enough. If the rule is dropped (LIN-04), only this index is removed.
create unique index group_assignment_one_group on group_assignment (team_membership_id);

-- Existing teams get their goalies (LIN-05).
insert into line_group (id, team_id, season_id, type, name, sort_order)
select gen_random_uuid(), team_id, season_id, 'GOALIES', 'Goalies', null from team_season;
