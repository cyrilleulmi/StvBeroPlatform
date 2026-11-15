-- Nachbesserung #21 · Der Teamname gilt pro Saison (SAI-02).
-- Wird ein Team in der aktuellen Saison umbenannt, behält das Archiv den damaligen Namen.

alter table team_saison add column name varchar(60);
update team_saison ts set name = t.name from team t where t.id = ts.team_id;
alter table team_saison alter column name set not null;
alter table team drop column name;
