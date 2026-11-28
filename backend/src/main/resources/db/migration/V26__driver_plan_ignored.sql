-- A match day can be left out of the driver list of the season (DRV-35): its trips do not count and
-- «Fair verteilen» gives it no drivers.
alter table event add column ignored_in_driver_plan boolean not null default false;
