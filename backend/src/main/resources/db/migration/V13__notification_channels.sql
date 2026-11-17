-- F-19 · Notifications by e-mail and push (BEN-10, BEN-11, E-71)

-- The person's choice for e-mails. No row: the default, i.e. on if the person has an e-mail address.
create table notification_preference (
    person_id uuid primary key references person (id) on delete cascade,
    email     boolean not null
);

-- One device that receives push (Web Push, VAPID). A person can have several devices. The endpoint is unique:
-- if another person signs in on the same device, the device moves to her.
create table push_subscription (
    id         uuid primary key,
    person_id  uuid          not null references person (id) on delete cascade,
    endpoint   varchar(1000) not null unique,
    p256dh     varchar(200)  not null,
    auth       varchar(100)  not null,
    label      varchar(100)  not null,
    created_at timestamptz   not null
);

create index push_subscription_person on push_subscription (person_id);

-- One e-mail or one push of a notification (outbox). Written in the transaction of the triggering change and
-- delivered after it, so a failed delivery never blocks the change; failed attempts are retried.
create table delivery (
    id                   uuid primary key,
    sequence_number      bigint generated always as identity,
    notification_id      uuid          not null references notification (id) on delete cascade,
    channel              varchar(10)   not null,
    push_subscription_id uuid references push_subscription (id) on delete set null,
    -- E-mail address or push endpoint at the time of the notification
    address              varchar(1000) not null,
    subject              varchar(200)  not null,
    text                 varchar(500)  not null,
    -- Path in the app the e-mail button and the tap on the push lead to, e.g. /termine/<id>
    path                 varchar(200)  not null,
    status               varchar(10)   not null,
    attempts             integer       not null default 0,
    next_attempt_at      timestamptz,
    last_error           varchar(500),
    created_at           timestamptz   not null,
    sent_at              timestamptz,
    constraint delivery_channel check (channel in ('EMAIL', 'PUSH')),
    constraint delivery_status check (status in ('PENDING', 'SENT', 'FAILED', 'DISCARDED')),
    -- At most one e-mail and one push per device for one notification (F-19, criterion 10)
    constraint delivery_once unique (notification_id, channel, address)
);

create index delivery_due on delivery (next_attempt_at) where status = 'PENDING';
