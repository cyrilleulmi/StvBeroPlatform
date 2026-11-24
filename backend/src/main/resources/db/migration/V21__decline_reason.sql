-- Reason required with «nicht dabei» (ANM-05, VAR-12): a setting of the event variant, off by default.
alter table event_variant add column decline_reason_required boolean not null default false;
