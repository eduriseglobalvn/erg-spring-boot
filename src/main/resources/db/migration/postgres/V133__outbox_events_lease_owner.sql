-- C15: lease ownership for the transactional outbox. A worker records a dispatch result
-- only while it still owns the row (id + locked_by), so an instance that lost its lease
-- can no longer overwrite the state written by the instance that reclaimed the row.
alter table outbox_events add column if not exists locked_by varchar(96);
