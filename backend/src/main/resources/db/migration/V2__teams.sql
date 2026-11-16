-- F-01 · Persons, teams, seasons (PTF, SAI)

create table team (
    id          uuid primary key,
    name        varchar(60) not null,
    -- Link to swiss unihockey (F-07)
    su_team_id  integer
);

-- Teams belong to seasons (SAI-01, SAI-04). A team can exist over several seasons.
create table team_season (
    team_id   uuid not null references team (id) on delete cascade,
    season_id uuid not null references season (id) on delete cascade,
    primary key (team_id, season_id)
);

-- Link between person, team and team role in a season (PTF-10).
-- The team role is stored as text, so further roles can be added without rework (PTF-06).
create table team_membership (
    id         uuid primary key,
    person_id  uuid        not null references person (id) on delete cascade,
    team_id    uuid        not null references team (id) on delete cascade,
    season_id  uuid        not null references season (id) on delete cascade,
    team_role  varchar(30) not null,
    -- Exactly one team role per person, team and season (PTF-04)
    constraint team_membership_one_role unique (person_id, team_id, season_id)
);

create index team_membership_season_team on team_membership (season_id, team_id);
create index team_membership_person on team_membership (person_id);
