-- F-05 · Match days and call-up (AUF)

-- Single match of a match day (AUF-01, AUF-14). A match day has one or more matches (tournament).
create table match (
    id            uuid primary key,
    event_id      uuid        not null references event (id) on delete cascade,
    opponent      varchar(80) not null,
    start_time    timestamptz not null,
    home          boolean     not null,
    -- home:away, e.g. 5:3 (AUF-15)
    result        varchar(5),
    source        varchar(20) not null,
    su_match_id   integer,
    missing_at_su boolean     not null default false,
    constraint match_source check (source in ('MANUAL', 'SWISS_UNIHOCKEY'))
);

create index match_event on match (event_id, start_time);
create unique index match_su on match (su_match_id) where su_match_id is not null;

-- One call-up per match day (AUF-02). Without a row: no call-up yet. Published as soon as
-- published_at is set (AUF-23).
create table call_up (
    event_id     uuid primary key references event (id) on delete cascade,
    published_at timestamptz
);

-- Called-up players (AUF-21, AUF-22).
create table call_up_entry (
    event_id  uuid not null references call_up (event_id) on delete cascade,
    person_id uuid not null references person (id) on delete cascade,
    primary key (event_id, person_id)
);
