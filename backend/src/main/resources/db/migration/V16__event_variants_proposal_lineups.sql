-- Batch «event variants» (E-86 to E-88)

-- Event variants per team (VAR-01 to VAR-11). The four standard variants (kind <> 'CUSTOM') exist once per team;
-- their ID is derived from team and kind, so that it is the same wherever they are created.
create table event_variant (
    id                      uuid primary key,
    team_id                 uuid        not null references team (id) on delete cascade,
    kind                    varchar(20) not null,
    event_type              varchar(20) not null,
    name                    varchar(40) not null,
    color                   varchar(20) not null,
    call_up                 boolean     not null default false,
    auto_departure          boolean     not null default true,
    response_deadline_hours integer     not null default 24,
    attendance_check        boolean     not null default false,
    sections                varchar(200) not null,
    sort_order              integer     not null,
    created_at              timestamptz not null default now()
);
create unique index event_variant_standard on event_variant (team_id, kind) where kind <> 'CUSTOM';
create unique index event_variant_name on event_variant (team_id, lower(name));

insert into event_variant (id, team_id, kind, event_type, name, color, sections, sort_order)
select md5(t.id::text || d.kind)::uuid, t.id, d.kind, d.event_type, d.name, d.color, d.sections, d.sort_order
from team t
cross join (values
    ('PRACTICE', 'PRACTICE', 'Training', 'GREEN', 'RESPONSES,ATTENDANCE,DETAILS', 1),
    ('MATCH_DAY', 'MATCH_DAY', 'Spieltag', 'VIOLET',
     'RESPONSES,MATCHES,CALL_UP,TIMELINE,DRIVER_LIST,LINEUPS,ATTENDANCE,DETAILS', 2),
    ('SU_MATCH_DAY', 'MATCH_DAY', 'swiss unihockey Spieltag', 'BLUE',
     'RESPONSES,MATCHES,CALL_UP,TIMELINE,DRIVER_LIST,LINEUPS,ATTENDANCE,DETAILS', 3),
    ('OTHER', 'OTHER', 'Anlass', 'PINK', 'RESPONSES,ATTENDANCE,DETAILS', 4)
) as d (kind, event_type, name, color, sections, sort_order);

-- A database that is already in use keeps its call-ups: teams with a call-up keep making one (VAR-06).
update event_variant v set call_up = true
where v.event_type = 'MATCH_DAY'
  and exists (select 1 from call_up c join event_team et on et.event_id = c.event_id where et.team_id = v.team_id);

-- Variant chosen for an event (VAR-03); null: the standard variant of its type.
alter table event add column variant_id uuid references event_variant (id) on delete set null;

-- Meeting time set by the automatic departure (TML-09), not by hand.
alter table event add column meeting_point_time_auto boolean not null default false;

-- Driving (DRV-18): whether a person can drive, and the places of her car including herself.
alter table person add column can_drive boolean not null default true;
alter table person add column car_seats integer not null default 5;

-- Travels on her own (DRV-20, ANM-13)
create table individual_travel (
    event_id   uuid        not null references event (id) on delete cascade,
    person_id  uuid        not null references person (id) on delete cascade,
    created_at timestamptz not null,
    primary key (event_id, person_id)
);

-- Special line-ups (LIN-13 to LIN-17)
create table special_lineup (
    id         uuid primary key,
    team_id    uuid        not null references team (id) on delete cascade,
    season_id  uuid        not null references season (id) on delete cascade,
    name       varchar(40) not null,
    sort_order integer     not null,
    created_at timestamptz not null
);
create unique index special_lineup_name on special_lineup (team_id, season_id, lower(name));

create table special_lineup_place (
    lineup_id uuid        not null references special_lineup (id) on delete cascade,
    position  integer     not null,
    label     varchar(30),
    person_id uuid references person (id) on delete set null,
    primary key (lineup_id, position)
);
