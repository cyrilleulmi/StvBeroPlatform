-- Batch «club data»: links to swiss unihockey per season (SUH-02, SUH-11), warnings for changed matches (SUH-12),
-- match day settings per team for the timeline (TML) and the season driver plan (DRV-14).

-- A team is linked per season with one or more teams at swiss unihockey (SUH-11): the team at swiss unihockey
-- changes from season to season. The state of the last sync is kept per link, so that the app can say when
-- swiss unihockey could not be reached (SUH-09, SUH-13).
create table su_link (
    id              uuid primary key,
    team_id         uuid        not null references team (id) on delete cascade,
    season_id       uuid        not null references season (id) on delete cascade,
    su_team_id      integer     not null,
    -- Name of the team at swiss unihockey when it was linked, e.g. «Damen KF 1. Liga»
    su_team_name    varchar(120),
    created_at      timestamptz not null,
    last_sync_at    timestamptz,
    last_sync_ok    boolean,
    last_sync_error varchar(500),
    constraint su_link_team_id_positive check (su_team_id > 0),
    -- A team at swiss unihockey belongs to at most one team of the app per season
    constraint su_link_once_per_season unique (season_id, su_team_id)
);

create index su_link_team on su_link (team_id, season_id);

-- Existing links (F-07) applied to every season of the team.
insert into su_link (id, team_id, season_id, su_team_id, created_at)
select gen_random_uuid(), t.id, ts.season_id, t.su_team_id, now()
from team t join team_season ts on ts.team_id = t.id
where t.su_team_id is not null;

alter table team drop column su_team_id;

-- Match day settings of a team (TML-02, TML-03, AUF-32): how long a match takes, how long before the first match
-- the team wants to arrive, and where it usually meets.
alter table team add column match_duration_minutes integer not null default 50;
alter table team add column arrival_lead_minutes integer not null default 70;
alter table team add column meeting_point_location varchar(120);
alter table team add constraint team_match_day_settings_range check (
    match_duration_minutes between 10 and 240 and arrival_lead_minutes between 0 and 240);

-- What an imported match looked like before swiss unihockey changed it (SUH-12). Set by the sync at the first
-- change and kept until a coach or admin confirms the change; su_changed_at is the time of the last change.
alter table match add column su_changed_at timestamptz;
alter table match add column su_previous_start timestamptz;
alter table match add column su_previous_opponent varchar(80);
alter table match add column su_previous_home boolean;
alter table match add column su_previous_venue varchar(120);
