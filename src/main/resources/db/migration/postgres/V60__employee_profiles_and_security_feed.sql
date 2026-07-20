create table if not exists employee_profiles (
    id varchar(36) primary key,
    tenant_id varchar(36) not null,
    user_id varchar(36) not null,
    employee_code varchar(100) not null,
    department_id varchar(36),
    job_title varchar(150),
    employment_type varchar(50),
    joined_at date,
    terminated_at date,
    experience_years numeric(5,2),
    experience_summary varchar(2000),
    manager_id varchar(36),
    work_location varchar(255),
    emergency_contact jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_employee_profiles_user foreign key (user_id) references users(id) on delete cascade,
    constraint fk_employee_profiles_manager foreign key (manager_id) references users(id) on delete set null,
    constraint uq_employee_profiles_tenant_user unique (tenant_id, user_id),
    unique (tenant_id, employee_code),
    constraint ck_employee_profiles_experience_years check (experience_years is null or experience_years >= 0),
    constraint ck_employee_profiles_employment_dates check (
        terminated_at is null or joined_at is null or terminated_at >= joined_at
    )
);

create index if not exists idx_employee_profiles_tenant_department
    on employee_profiles (tenant_id, department_id, employee_code);

create index if not exists idx_employee_profiles_tenant_manager
    on employee_profiles (tenant_id, manager_id);

alter table security_events add column if not exists platform varchar(32);
alter table security_events add column if not exists result varchar(32);
alter table security_events add column if not exists risk_code varchar(100);
alter table security_events add column if not exists session_id varchar(36);
alter table security_events add column if not exists metadata jsonb not null default '{}'::jsonb;

create index if not exists idx_security_events_tenant_user_risk_time
    on security_events (tenant_id, user_id, risk_code, occurred_at desc);

create index if not exists idx_security_events_tenant_session_time
    on security_events (tenant_id, session_id, occurred_at desc);
