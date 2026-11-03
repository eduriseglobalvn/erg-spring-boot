-- N7: poison Modulith publications stop being resubmitted after a bounded number of
-- completion attempts. ModuleEventRecoveryJob moves them here (same columns as
-- event_publication plus quarantined_at) so they no longer block newer failures and an
-- operator can inspect or replay them deliberately.
create table if not exists event_publication_quarantine (
    id uuid not null primary key,
    listener_id text not null,
    event_type text not null,
    serialized_event text not null,
    publication_date timestamptz not null,
    status text,
    completion_attempts int,
    last_resubmission_date timestamptz,
    quarantined_at timestamptz not null default now()
);

create index if not exists event_publication_quarantine_by_quarantined_at_idx
    on event_publication_quarantine (quarantined_at);
create index if not exists event_publication_failed_attempts_idx
    on event_publication (publication_date)
    where completion_date is null and status = 'FAILED';
