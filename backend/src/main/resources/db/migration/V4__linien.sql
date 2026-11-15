-- F-02 · Linien (LIN)

-- Linie oder Goalies eines Teams in einer Saison (LIN-03, LIN-05, LIN-11).
create table gruppe (
    id          uuid primary key,
    team_id     uuid        not null,
    saison_id   uuid        not null,
    art         varchar(20) not null,
    name        varchar(30) not null,
    -- Nur Linien haben eine Reihenfolge (LIN-09)
    reihenfolge integer,
    constraint gruppe_team_saison foreign key (team_id, saison_id)
        references team_saison (team_id, saison_id) on delete cascade,
    constraint gruppe_reihenfolge_nur_linie check ((art = 'LINIE') = (reihenfolge is not null))
);

create index gruppe_team_saison_idx on gruppe (team_id, saison_id);
-- Goalies gibt es pro Team und Saison genau einmal (LIN-05)
create unique index gruppe_goalies_einmal on gruppe (team_id, saison_id) where art = 'GOALIES';
-- Namen sind pro Team und Saison eindeutig (Annahme zu LIN-09)
create unique index gruppe_name_eindeutig on gruppe (team_id, saison_id, lower(name));

-- Zuweisung einer Spielerin (über ihre Teamzugehörigkeit) zu einer Gruppe (LIN-01).
-- Als eigene Tabelle, damit «mehrere Linien pro Spielerin» später ohne Umbau geht (LIN-04).
create table gruppenzuweisung (
    teamzugehoerigkeit_id uuid not null references teamzugehoerigkeit (id) on delete cascade,
    gruppe_id             uuid not null references gruppe (id) on delete cascade,
    primary key (teamzugehoerigkeit_id, gruppe_id)
);

create index gruppenzuweisung_gruppe on gruppenzuweisung (gruppe_id);
-- LIN-02 als Regel: höchstens eine Gruppe pro Spielerin und Team. Die Teamzugehörigkeit gilt pro Team
-- und Saison, deshalb genügt dieser Index. Fällt die Regel (LIN-04), wird nur er entfernt.
create unique index gruppenzuweisung_eine_gruppe on gruppenzuweisung (teamzugehoerigkeit_id);

-- Bestehende Teams bekommen ihre Goalies (LIN-05).
insert into gruppe (id, team_id, saison_id, art, name, reihenfolge)
select gen_random_uuid(), team_id, saison_id, 'GOALIES', 'Goalies', null from team_saison;
