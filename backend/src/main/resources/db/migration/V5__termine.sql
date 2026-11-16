-- F-03 · Termine und Serien (TER)

-- Regel, die wiederkehrende Termine erzeugt (TER-05). Gilt für die Teams in serie_team.
create table serie (
    id                   uuid primary key,
    saison_id            uuid         not null references saison (id) on delete cascade,
    typ                  varchar(20)  not null,
    titel                varchar(80)  not null,
    wochentag            varchar(20)  not null,
    beginn_zeit          time         not null,
    ende_zeit            time         not null,
    gueltig_von          date         not null,
    gueltig_bis          date         not null,
    ort_name             varchar(120),
    ort_adresse          varchar(200),
    ort_maps_url         varchar(500),
    ort_breite           double precision,
    ort_laenge           double precision,
    antwortfrist_stunden integer      not null,
    constraint serie_zeitraum check (gueltig_bis >= gueltig_von),
    constraint serie_zeit check (ende_zeit > beginn_zeit)
);

create table serie_team (
    serie_id uuid not null references serie (id) on delete cascade,
    team_id  uuid not null references team (id) on delete cascade,
    primary key (serie_id, team_id)
);

-- Zeitraum, der von einer Serie ausgenommen ist, z.B. Ferien (TER-07).
create table ausnahme (
    id       uuid primary key,
    serie_id uuid         not null references serie (id) on delete cascade,
    von      date         not null,
    bis      date         not null,
    grund    varchar(200) not null,
    constraint ausnahme_zeitraum check (bis >= von)
);

create index ausnahme_serie on ausnahme (serie_id);

-- Etwas, wofür man sich an- oder abmeldet (TER-02, TER-04). Gespeichert wird nur GEPLANT oder ABGESAGT;
-- «vorbei» ergibt sich aus ende und der Backend-Uhr.
create table termin (
    id                   uuid primary key,
    saison_id            uuid         not null references saison (id) on delete cascade,
    typ                  varchar(20)  not null,
    titel                varchar(80)  not null,
    beginn               timestamptz  not null,
    ende                 timestamptz  not null,
    status               varchar(20)  not null,
    absagegrund          varchar(200),
    -- Beginn wurde gegenüber dem ursprünglichen Datum geändert (TER-06)
    verschoben           boolean      not null default false,
    ort_name             varchar(120),
    ort_adresse          varchar(200),
    ort_maps_url         varchar(500),
    ort_breite           double precision,
    ort_laenge           double precision,
    hinweise             varchar(500),
    antwortfrist_stunden integer      not null,
    treffpunkt_zeit      timestamptz,
    treffpunkt_ort       varchar(120),
    -- Von einer Serie erzeugt: Serie und Datum in der Serie. Einzeln geänderte Termine übernehmen
    -- spätere Änderungen der Serie nicht mehr (TER-06, TER-08).
    serie_id             uuid references serie (id) on delete set null,
    serie_datum          date,
    einzeln_geaendert    boolean      not null default false,
    constraint termin_zeit check (ende > beginn),
    constraint termin_status check (status in ('GEPLANT', 'ABGESAGT'))
);

create index termin_beginn on termin (beginn);
-- Eine Serie erzeugt pro Datum höchstens einen Termin
create unique index termin_serie_datum on termin (serie_id, serie_datum) where serie_id is not null;

-- Ein Termin gehört zu einem oder mehreren Teams (TER-01, TER-03).
create table termin_team (
    termin_id uuid not null references termin (id) on delete cascade,
    team_id   uuid not null references team (id) on delete cascade,
    primary key (termin_id, team_id)
);

create index termin_team_team on termin_team (team_id);

-- Antwort einer Person auf einen Termin (ANM). Die Tabelle entsteht mit F-03, damit eine Ausnahme
-- Termine mit Antworten absagt statt löscht (TER-07). Gesetzt werden Antworten ab F-04.
create table antwort (
    termin_id       uuid         not null references termin (id) on delete cascade,
    person_id       uuid         not null references person (id) on delete cascade,
    status          varchar(20)  not null,
    kommentar       varchar(200),
    geaendert_am    timestamptz  not null,
    eingetragen_von uuid references person (id) on delete set null,
    kurzfristig     boolean      not null default false,
    primary key (termin_id, person_id),
    constraint antwort_status check (status in ('DABEI', 'NICHT_DABEI', 'VIELLEICHT'))
);
