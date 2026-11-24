-- Driving time overwritten by hand and the service that calculated the route (DRV-21, DRV-22)
alter table route add column manual_duration_seconds integer;
alter table route add column manual_duration_by uuid references person (id) on delete set null;
alter table route add column manual_duration_at timestamptz;
alter table route add column provider varchar(30);
alter table route add constraint route_manual_duration check (manual_duration_seconds is null or manual_duration_seconds > 0);
alter table route add constraint route_provider check (provider is null or provider in ('GOOGLE_MAPS', 'OPEN_ROUTE_SERVICE', 'OPEN_STREET_MAP', 'SIMULATOR'));

-- Swaps of trips between two members of a team (DRV-25)
create table driver_swap (
    id                uuid primary key,
    team_id           uuid        not null references team (id) on delete cascade,
    season_id         uuid        not null references season (id) on delete cascade,
    event_id          uuid        not null references event (id) on delete cascade,
    requester_id      uuid        not null references person (id) on delete cascade,
    partner_id        uuid        not null references person (id) on delete cascade,
    partner_event_id  uuid references event (id) on delete cascade,
    status            varchar(20) not null,
    created_by        uuid references person (id) on delete set null,
    created_at        timestamptz not null,
    decided_at        timestamptz,
    constraint driver_swap_status check (status in ('OPEN', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    constraint driver_swap_people check (requester_id <> partner_id),
    constraint driver_swap_events check (partner_event_id is null or partner_event_id <> event_id)
);
create index driver_swap_team on driver_swap (team_id, season_id);

-- Other trips without an event in the app (DRV-28)
create table extra_trip (
    id               uuid primary key,
    team_id          uuid         not null references team (id) on delete cascade,
    season_id        uuid         not null references season (id) on delete cascade,
    driver_id        uuid         not null references person (id) on delete cascade,
    trip_date        date         not null,
    title            varchar(80)  not null,
    distance_meters  integer      not null,
    created_by       uuid references person (id) on delete set null,
    created_at       timestamptz  not null,
    constraint extra_trip_distance check (distance_meters > 0)
);
create index extra_trip_team on extra_trip (team_id, season_id);
create index extra_trip_driver on extra_trip (season_id, driver_id);

-- History of the driver plan (DRV-26). Entries of a match day carry the event; other trips carry the team.
create table driver_plan_entry (
    id          uuid primary key,
    seq         bigint generated always as identity,
    team_id     uuid references team (id) on delete cascade,
    season_id   uuid         not null references season (id) on delete cascade,
    event_id    uuid references event (id) on delete cascade,
    kind        varchar(30)  not null,
    actor_id    uuid references person (id) on delete set null,
    text        varchar(500) not null,
    created_at  timestamptz  not null,
    constraint driver_plan_entry_kind check (kind in ('CAR_ADDED', 'CAR_REMOVED', 'SWAPPED', 'TAKEN_OVER',
        'SWAP_REQUESTED', 'SWAP_DECLINED', 'SWAP_WITHDRAWN', 'TRIP_ADDED', 'TRIP_REMOVED')),
    constraint driver_plan_entry_owner check (team_id is not null or event_id is not null)
);
create index driver_plan_entry_event on driver_plan_entry (event_id);
create index driver_plan_entry_team on driver_plan_entry (team_id, season_id);
