-- Follow-up fix #21 · The team name applies per season (SAI-02).
-- If a team is renamed in the current season, the archive keeps the name it had then.

alter table team_season add column name varchar(60);
update team_season ts set name = t.name from team t where t.id = ts.team_id;
alter table team_season alter column name set not null;
alter table team drop column name;
