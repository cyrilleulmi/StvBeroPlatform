-- F-06 · Benachrichtigungen (BEN)

-- Benachrichtigung in der App (E-56). Weitere Kanäle (E-Mail, Push) senden dieselben Einträge (BEN-10, BEN-11).
create table benachrichtigung (
    id             uuid primary key,
    -- Stabile Reihenfolge bei gleichem Zeitpunkt (feste Uhr in Tests)
    laufnummer     bigint generated always as identity,
    art            varchar(30)  not null,
    empfaenger_id  uuid         not null references person (id) on delete cascade,
    text           varchar(500) not null,
    termin_id      uuid references termin (id) on delete cascade,
    erstellt_am    timestamptz  not null,
    gelesen_am     timestamptz,
    constraint benachrichtigung_art check (art in ('ERINNERUNG', 'KURZFRISTIGE_ABSAGE', 'AUFGEBOT_VEROEFFENTLICHT',
        'AUFGEBOT_GEAENDERT', 'AUFGEBOTENE_ABGEMELDET', 'SPIEL_GEAENDERT', 'TERMIN_ABGESAGT', 'TERMIN_VERSCHOBEN'))
);

create index benachrichtigung_empfaenger on benachrichtigung (empfaenger_id, erstellt_am desc, laufnummer desc);

-- Höchstens eine Erinnerung pro Person und Termin (BEN-13), unabhängig vom Kanal. Auch zwei gleichzeitige
-- Prüfungen erinnern nur einmal.
create table erinnerung (
    termin_id     uuid        not null references termin (id) on delete cascade,
    person_id     uuid        not null references person (id) on delete cascade,
    erinnert_am   timestamptz not null,
    primary key (termin_id, person_id)
);
