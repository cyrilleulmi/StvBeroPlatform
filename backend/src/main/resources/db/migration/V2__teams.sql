-- F-01 · Personen, Teams, Saisons (PTF, SAI)

create table team (
    id          uuid primary key,
    name        varchar(60) not null,
    -- Verknüpfung mit swiss unihockey (F-07)
    su_team_id  integer
);

-- Teams gehören zu Saisons (SAI-01, SAI-04). Ein Team kann über mehrere Saisons bestehen.
create table team_saison (
    team_id   uuid not null references team (id) on delete cascade,
    saison_id uuid not null references saison (id) on delete cascade,
    primary key (team_id, saison_id)
);

-- Verbindung von Person, Team und Funktion in einer Saison (PTF-10).
-- Funktion als Text, damit weitere Funktionen ohne Umbau dazukommen (PTF-06).
create table teamzugehoerigkeit (
    id         uuid primary key,
    person_id  uuid        not null references person (id) on delete cascade,
    team_id    uuid        not null references team (id) on delete cascade,
    saison_id  uuid        not null references saison (id) on delete cascade,
    funktion   varchar(30) not null,
    -- Pro Person, Team und Saison genau eine Funktion (PTF-04)
    constraint teamzugehoerigkeit_eine_funktion unique (person_id, team_id, saison_id)
);

create index teamzugehoerigkeit_saison_team on teamzugehoerigkeit (saison_id, team_id);
create index teamzugehoerigkeit_person on teamzugehoerigkeit (person_id);
