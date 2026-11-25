-- TER-22, TER-23: a series may run without an end; the app keeps creating its events one year ahead.
-- generated_until: the last day up to which the events of the series were created.
alter table series alter column valid_until drop not null;
alter table series add column generated_until date;
update series set generated_until = valid_until;

-- PTF-15: gender and year of birth, optional, to sort and filter people when they are put into a team.
alter table person add column gender varchar(10);
alter table person add constraint person_gender check (gender in ('FEMALE', 'MALE'));
alter table person add column birth_year integer;
alter table person add constraint person_birth_year check (birth_year between 1900 and 2100);
