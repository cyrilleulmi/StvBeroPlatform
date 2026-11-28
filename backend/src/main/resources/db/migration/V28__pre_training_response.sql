-- Response to the pre-training of a practice (ANM-14, ANM-15, E-119): whether a person who is «dabei» at the
-- practice is at the pre-training too. Empty: not said yet. It only counts while the response to the practice is
-- ATTENDING; any other response to the practice empties it.
alter table response add column pre_training_status varchar(20);
alter table response add constraint response_pre_training_status
    check (pre_training_status is null or pre_training_status in ('ATTENDING', 'DECLINED'));
