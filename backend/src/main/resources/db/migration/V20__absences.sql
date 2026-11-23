-- Absences (ABS): a person is away for a longer time (holidays, injury). Practices in this time are declined
-- by themselves, with the reason as comment (ABS-03). V20 leaves room for V18/V19 of the open driver list branches.

create table absence (
    id         uuid primary key,
    person_id  uuid         not null references person (id) on delete cascade,
    first_day  date         not null,
    last_day   date         not null,
    reason     varchar(20)  not null,
    note       varchar(120),
    created_at timestamptz  not null,
    constraint absence_days check (last_day >= first_day),
    constraint absence_reason check (reason in ('VACATION', 'INJURY', 'OTHER'))
);

create index absence_person on absence (person_id, last_day);

-- A response set by an absence remembers it (ABS-04): changing or deleting the absence changes these responses,
-- a response given by hand afterwards no longer belongs to the absence.
alter table response add column absence_id uuid references absence (id) on delete set null;

create index response_absence on response (absence_id) where absence_id is not null;
