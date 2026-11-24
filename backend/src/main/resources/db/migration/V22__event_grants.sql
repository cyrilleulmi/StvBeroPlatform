-- Special permissions per event («Sonderberechtigungen», RR-09 to RR-14): coaches and admins let a person manage
-- a single event like a coach. A grant on a series applies to all events of the series, also later ones.
create table event_grant (
    event_id   uuid not null references event (id) on delete cascade,
    person_id  uuid not null references person (id) on delete cascade,
    granted_by uuid references person (id) on delete set null,
    granted_at timestamptz not null,
    primary key (event_id, person_id)
);
create table series_grant (
    series_id  uuid not null references series (id) on delete cascade,
    person_id  uuid not null references person (id) on delete cascade,
    granted_by uuid references person (id) on delete set null,
    granted_at timestamptz not null,
    primary key (series_id, person_id)
);

-- Notification to the person who gets a special permission (BEN-08)
alter table notification drop constraint notification_type;
alter table notification add constraint notification_type check (type in ('REMINDER', 'LATE_CANCELLATION',
    'CALL_UP_PUBLISHED', 'CALL_UP_CHANGED', 'CALLED_UP_PLAYER_DECLINED', 'MATCH_CHANGED', 'EVENT_CANCELLED',
    'EVENT_RESCHEDULED', 'DRIVER_LIST_CHANGED', 'EVENT_GRANTED'));
