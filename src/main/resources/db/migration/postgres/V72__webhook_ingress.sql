create table if not exists webhook_receipts (
    provider varchar(32) not null,
    receipt_key varchar(192) not null,
    received_at timestamptz not null,
    expires_at timestamptz not null,
    primary key (provider, receipt_key)
);

create index if not exists idx_webhook_receipts_expiry on webhook_receipts(expires_at);

create table if not exists webhook_inbox (
    id bigint generated always as identity primary key,
    provider varchar(32) not null,
    receipt_key varchar(192) not null,
    payload jsonb not null,
    status varchar(24) not null check (status in ('pending', 'processing', 'completed', 'failed')),
    received_at timestamptz not null,
    processed_at timestamptz,
    last_error varchar(1000),
    unique (provider, receipt_key)
);

create index if not exists idx_webhook_inbox_pickup on webhook_inbox(status, received_at);
