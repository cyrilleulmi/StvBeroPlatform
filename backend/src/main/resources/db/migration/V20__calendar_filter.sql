-- Calendar subscription with filters (CAL-10 to CAL-15): which events the feed delivers. The defaults keep the
-- behaviour of F-18 (everything of the own teams). «dabei» is always delivered and has no switch (CAL-11).
alter table calendar_subscription
    add column include_practice  boolean not null default true,
    add column include_match_day boolean not null default true,
    add column include_other     boolean not null default true,
    add column include_pending   boolean not null default true,
    add column include_maybe     boolean not null default true,
    add column include_declined  boolean not null default true,
    add column include_optional  boolean not null default true;

-- Teams whose events the person does not want in the feed (CAL-13). An event stays in the feed as long as one
-- of the person's teams in it is not excluded.
create table calendar_excluded_team (
    person_id uuid not null references calendar_subscription (person_id) on delete cascade,
    team_id   uuid not null references team (id) on delete cascade,
    primary key (person_id, team_id)
);
