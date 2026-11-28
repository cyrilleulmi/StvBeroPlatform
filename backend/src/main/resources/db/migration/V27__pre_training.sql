-- Pre-training of a practice (TER-29 to TER-31, E-117): a title and a start before the practice. On the series it
-- is a time of day, on the event a moment. Both columns are set or both are empty.
alter table series add column pre_training_title varchar(60);
alter table series add column pre_training_time time;
alter table series add constraint series_pre_training_both
    check ((pre_training_title is null) = (pre_training_time is null));

alter table event add column pre_training_title varchar(60);
alter table event add column pre_training_starts_at timestamptz;
alter table event add constraint event_pre_training_both
    check ((pre_training_title is null) = (pre_training_starts_at is null));
