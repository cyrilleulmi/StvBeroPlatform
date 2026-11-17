-- F-17 · Team overview with data from swiss unihockey (SUH)

-- League data of a linked team, cached by the daily sync or a manual reload. The page never calls
-- swiss unihockey while rendering (F-17, criterion 6). One row per team; it belongs to one season.
-- Table and matches are stored as JSON as delivered, they are only read and never queried.
create table league_snapshot (
    team_id     uuid primary key references team (id) on delete cascade,
    season_id   uuid not null references season (id) on delete cascade,
    league      varchar(200),
    group_name  varchar(200),
    standings   text not null default '[]',
    matches     text not null default '[]',
    -- Last successful load («Stand»), null if never loaded in this season
    loaded_at   timestamptz,
    -- Last failed attempt after the last successful load (SUH-09), null if the last attempt worked
    failed_at   timestamptz
);
