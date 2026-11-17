-- F-14 · Attendance check (PRS)

-- Switch «Präsenz erfassen», off by default (PRS-01, E-70). A series passes it on to its events.
alter table event add column attendance_check boolean not null default false;
alter table series add column attendance_check boolean not null default false;

-- Attendance of an event: who recorded it and when (PRS-04). Without a row: nothing recorded yet.
create table attendance_record (
    event_id    uuid primary key references event (id) on delete cascade,
    recorded_at timestamptz not null,
    recorded_by uuid references person (id) on delete set null
);

-- Recorded attendance per player (PRS-03). Kept when the check is switched off (PRS-08).
create table attendance_entry (
    event_id  uuid        not null references attendance_record (event_id) on delete cascade,
    person_id uuid        not null references person (id) on delete cascade,
    status    varchar(20) not null,
    primary key (event_id, person_id),
    constraint attendance_entry_status check (status in ('PRESENT', 'ABSENT', 'EXCUSED'))
);

create index attendance_entry_person on attendance_entry (person_id);
