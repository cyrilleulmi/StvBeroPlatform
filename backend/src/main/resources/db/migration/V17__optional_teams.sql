-- Teams that are invited to an event without obligation («freiwillig», TER-15): their members respond if they
-- like, are not reminded and do not count as «offen». The same per series, copied to its events.
create table event_optional_team (
    event_id uuid not null references event (id) on delete cascade,
    team_id  uuid not null references team (id) on delete cascade,
    primary key (event_id, team_id)
);
create table series_optional_team (
    series_id uuid not null references series (id) on delete cascade,
    team_id   uuid not null references team (id) on delete cascade,
    primary key (series_id, team_id)
);
