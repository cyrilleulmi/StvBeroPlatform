-- Pinned events («angeheftet», TER-26 to TER-28, E-114): a coach pins an event to the start page of the members
-- of its teams until it is over. Who pinned it and when are kept, like the special permissions (V22).
alter table event add column pinned_at timestamptz;
alter table event add column pinned_by uuid references person (id) on delete set null;
