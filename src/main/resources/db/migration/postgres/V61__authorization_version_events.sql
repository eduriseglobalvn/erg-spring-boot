alter table users
    add column if not exists authz_version bigint not null default 1;

