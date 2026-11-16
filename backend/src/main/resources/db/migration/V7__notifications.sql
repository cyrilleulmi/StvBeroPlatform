-- F-06 · Notifications (BEN)

-- Notification in the app (E-56). Further channels (e-mail, push) send the same entries (BEN-10, BEN-11).
create table notification (
    id              uuid primary key,
    -- Stable order for the same instant (fixed clock in tests)
    sequence_number bigint generated always as identity,
    type            varchar(30)  not null,
    recipient_id    uuid         not null references person (id) on delete cascade,
    text            varchar(500) not null,
    event_id        uuid references event (id) on delete cascade,
    created_at      timestamptz  not null,
    read_at         timestamptz,
    constraint notification_type check (type in ('REMINDER', 'LATE_CANCELLATION', 'CALL_UP_PUBLISHED',
        'CALL_UP_CHANGED', 'CALLED_UP_PLAYER_DECLINED', 'MATCH_CHANGED', 'EVENT_CANCELLED', 'EVENT_RESCHEDULED'))
);

create index notification_recipient on notification (recipient_id, created_at desc, sequence_number desc);

-- At most one reminder per person and event (BEN-13), independent of the channel. Even two concurrent
-- checks remind only once.
create table reminder (
    event_id    uuid        not null references event (id) on delete cascade,
    person_id   uuid        not null references person (id) on delete cascade,
    reminded_at timestamptz not null,
    primary key (event_id, person_id)
);
