-- F-00 · Foundation: persons, accounts, sessions, seasons

create table person (
    id         uuid primary key,
    first_name varchar(60)  not null,
    last_name  varchar(60)  not null,
    email      varchar(254),
    admin      boolean      not null default false
);

-- Account and person are separate (PTF-09). Today every account belongs to exactly one person.
create table account (
    id         uuid primary key,
    person_id  uuid        not null unique references person (id) on delete cascade,
    -- Key for signing in in test mode (/dev-login). The production login method is open (O-01).
    login_key  varchar(40) unique
);

create table session (
    token       varchar(64) primary key,
    account_id  uuid        not null references account (id) on delete cascade,
    created_at  timestamptz not null
);

create table season (
    id         uuid primary key,
    name       varchar(20) not null unique,
    start_date date        not null,
    end_date   date        not null,
    is_current boolean     not null default false,
    constraint season_period check (start_date <= end_date)
);

-- There is exactly one current season
create unique index season_one_current on season (is_current) where is_current;
