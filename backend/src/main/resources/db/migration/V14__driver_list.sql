-- F-20 · Driver list for match days (DRV)

-- A car of a match day: the driver and the number of free seats (DRV-02). A person drives at most one car per
-- match day.
create table car (
    id         uuid primary key,
    event_id   uuid        not null references event (id) on delete cascade,
    driver_id  uuid        not null references person (id) on delete cascade,
    seats      integer     not null,
    created_at timestamptz not null,
    constraint car_seats check (seats between 1 and 8)
);

create unique index car_driver on car (event_id, driver_id);

-- Passenger in a car (DRV-03). A person sits in at most one car per match day.
create table car_passenger (
    car_id    uuid        not null references car (id) on delete cascade,
    event_id  uuid        not null references event (id) on delete cascade,
    person_id uuid        not null references person (id) on delete cascade,
    added_at  timestamptz not null,
    primary key (car_id, person_id)
);

create unique index car_passenger_event on car_passenger (event_id, person_id);

-- Route from the meeting point to the venue, calculated once per match day (DRV-05). «places» holds the places
-- it was calculated for; if meeting point or venue change, the route is calculated again (DRV-08). A value
-- entered by hand has the status MANUAL (DRV-07).
create table route (
    event_id         uuid primary key references event (id) on delete cascade,
    places           varchar(2000) not null,
    status           varchar(20)   not null,
    distance_meters  integer,
    duration_seconds integer,
    attempted_at     timestamptz   not null,
    constraint route_status check (status in ('CALCULATED', 'NOT_FOUND', 'UNAVAILABLE', 'MANUAL')),
    constraint route_distance check (distance_meters is null or distance_meters >= 0)
);

-- Notification about a change to a car (DRV-11)
alter table notification drop constraint notification_type;
alter table notification add constraint notification_type check (type in ('REMINDER', 'LATE_CANCELLATION',
    'CALL_UP_PUBLISHED', 'CALL_UP_CHANGED', 'CALLED_UP_PLAYER_DECLINED', 'MATCH_CHANGED', 'EVENT_CANCELLED',
    'EVENT_RESCHEDULED', 'DRIVER_LIST_CHANGED'));
