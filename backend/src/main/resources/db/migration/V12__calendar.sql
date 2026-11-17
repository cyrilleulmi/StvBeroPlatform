-- F-18 · Calendar subscription (CAL)

-- Personal address of the iCalendar feed (CAL-01, CAL-06). At most one per person. The token is the only
-- credential of the feed: it grants read access to the feed and nothing else. Renewing replaces it (CAL-07).
create table calendar_subscription (
    person_id  uuid primary key references person (id) on delete cascade,
    token      varchar(64) not null unique,
    created_at timestamptz not null
);
