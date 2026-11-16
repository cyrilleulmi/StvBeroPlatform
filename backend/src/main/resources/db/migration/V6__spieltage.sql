-- F-05 · Spieltage und Aufgebot (AUF)

-- Einzelne Partie eines Spieltags (AUF-01, AUF-14). Ein Spieltag umfasst ein oder mehrere Spiele (Turnierform).
create table spiel (
    id                          uuid primary key,
    termin_id                   uuid        not null references termin (id) on delete cascade,
    gegner                      varchar(80) not null,
    anspielzeit                 timestamptz not null,
    heim                        boolean     not null,
    -- Heim:Gast, z.B. 5:3 (AUF-15)
    resultat                    varchar(5),
    quelle                      varchar(20) not null,
    su_spiel_id                 integer,
    bei_su_nicht_mehr_vorhanden boolean     not null default false,
    constraint spiel_quelle check (quelle in ('MANUELL', 'SWISS_UNIHOCKEY'))
);

create index spiel_termin on spiel (termin_id, anspielzeit);
create unique index spiel_su on spiel (su_spiel_id) where su_spiel_id is not null;

-- Ein Aufgebot pro Spieltag (AUF-02). Ohne Zeile: noch kein Aufgebot. Veröffentlicht, sobald
-- veroeffentlicht_am gesetzt ist (AUF-23).
create table aufgebot (
    termin_id          uuid primary key references termin (id) on delete cascade,
    veroeffentlicht_am timestamptz
);

-- Aufgebotene Spielerinnen (AUF-21, AUF-22).
create table aufgebot_eintrag (
    termin_id uuid not null references aufgebot (termin_id) on delete cascade,
    person_id uuid not null references person (id) on delete cascade,
    primary key (termin_id, person_id)
);
