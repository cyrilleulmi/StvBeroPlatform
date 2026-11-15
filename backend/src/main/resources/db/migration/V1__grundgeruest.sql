-- F-00 · Grundgerüst: Personen, Konten, Sitzungen, Saisons

create table person (
    id        uuid primary key,
    vorname   varchar(60)  not null,
    nachname  varchar(60)  not null,
    email     varchar(254),
    admin     boolean      not null default false
);

-- Konto und Person sind getrennt (PTF-09). Heute gehört zu jedem Konto genau eine Person.
create table konto (
    id         uuid primary key,
    person_id  uuid        not null unique references person (id) on delete cascade,
    -- Schlüssel für die Anmeldung im Testmodus (/dev-login). Produktives Verfahren offen (O-01).
    schluessel varchar(40) unique
);

create table sitzung (
    token       varchar(64) primary key,
    konto_id    uuid        not null references konto (id) on delete cascade,
    erstellt_am timestamptz not null
);

create table saison (
    id      uuid primary key,
    name    varchar(20) not null unique,
    von     date        not null,
    bis     date        not null,
    aktuell boolean     not null default false,
    constraint saison_zeitraum check (von <= bis)
);

-- Es gibt genau eine aktuelle Saison
create unique index saison_eine_aktuelle on saison (aktuell) where aktuell;
