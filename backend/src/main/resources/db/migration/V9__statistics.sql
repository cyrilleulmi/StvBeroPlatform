-- F-15 · Attendance statistics (STA)

-- Day the person joined the team in this season (STA-07): a player counts only for events from this day on.
-- Existing memberships count from the start of their season.
alter table team_membership add column joined_on date;
update team_membership m set joined_on = s.start_date from season s where s.id = m.season_id;
alter table team_membership alter column joined_on set not null;
