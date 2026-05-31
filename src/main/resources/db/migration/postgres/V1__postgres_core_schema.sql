CREATE TABLE IF NOT EXISTS users (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    email varchar(255),
    password_hash varchar(255),
    full_name varchar(255),
    avatar_url varchar(255),
    status varchar(255),
    provider varchar(255),
    provider_id varchar(255),
    account_type varchar(255),
    google_sub varchar(255),
    google_email varchar(255),
    google_email_verified boolean,
    last_login_provider varchar(255),
    tenant_id varchar(255),
    phone varchar(255),
    bio varchar(255),
    gender varchar(255),
    date_of_birth varchar(255),
    address varchar(255),
    city varchar(255),
    district varchar(255),
    job_title varchar(255),
    region varchar(255),
    extended_profile varchar(255),
    is_profile_completed boolean,
    last_login_at timestamp with time zone,
    login_count bigint
);

CREATE TABLE IF NOT EXISTS roles (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name varchar(255),
    description varchar(255),
    is_default boolean
);

CREATE TABLE IF NOT EXISTS permission_groups (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name varchar(255) NOT NULL UNIQUE,
    label varchar(255),
    display_order integer
);

CREATE TABLE IF NOT EXISTS permissions (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name varchar(255) NOT NULL UNIQUE,
    group_name varchar(255),
    label varchar(255),
    description varchar(255)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id varchar(24) NOT NULL,
    role_id varchar(24) NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id varchar(24) NOT NULL,
    permission_id varchar(24) NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS education_units (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name varchar(255),
    slug varchar(255),
    type varchar(255),
    parent_id varchar(24),
    logo_url varchar(255),
    description varchar(255),
    status varchar(255),
    owner_user_id varchar(255),
    created_by varchar(255),
    source varchar(255),
    signed_at timestamp with time zone,
    activated_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id varchar(24) NOT NULL,
    session_id varchar(255),
    device_id varchar(255),
    device_name varchar(255),
    portal varchar(255),
    ip_address varchar(255),
    user_agent varchar(255),
    refresh_token_hash varchar(255),
    access_token_hash varchar(255),
    tenant_id varchar(255),
    last_active_at timestamp with time zone,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    revoked_reason varchar(255)
);

CREATE TABLE IF NOT EXISTS auth_pins (
    id varchar(24) PRIMARY KEY,
    email varchar(255),
    code varchar(255),
    purpose varchar(255),
    expires_at timestamp with time zone,
    used_at timestamp with time zone,
    created_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS auth_login_attempts (
    id varchar(24) PRIMARY KEY,
    tenant_id varchar(255),
    user_id varchar(24),
    attempted_email varchar(255),
    attempted_email_hash varchar(255),
    ip_address varchar(255),
    country_code varchar(255),
    country_name varchar(255),
    continent_code varchar(255),
    user_agent varchar(255),
    device_id varchar(255),
    device_name varchar(255),
    portal varchar(255),
    result varchar(255),
    reason varchar(255),
    created_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS profiles (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id varchar(24) NOT NULL,
    full_name varchar(255),
    bio varchar(255),
    phone varchar(255),
    date_of_birth timestamp with time zone,
    gender varchar(255),
    address varchar(255),
    city varchar(255),
    district varchar(255),
    social_links_json varchar(255),
    avatar_url varchar(255),
    is_profile_completed boolean,
    teaching_philosophy varchar(255),
    specialties_json varchar(255),
    rating double precision,
    internal_note varchar(255)
);

CREATE TABLE IF NOT EXISTS social_accounts (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id varchar(24) NOT NULL,
    provider varchar(255),
    provider_id varchar(255),
    email varchar(255),
    access_token varchar(255),
    refresh_token varchar(255),
    expires_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS certificates (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id varchar(24) NOT NULL,
    name varchar(255),
    issued_by varchar(255),
    issue_date timestamp with time zone,
    expiry_date timestamp with time zone,
    image_url varchar(255)
);

CREATE TABLE IF NOT EXISTS user_activities (
    id varchar(24) PRIMARY KEY,
    user_id varchar(24) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    action varchar(255),
    target_user_id varchar(24),
    ip_address varchar(255),
    user_agent varchar(255),
    description varchar(255),
    created_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS user_permissions (
    id varchar(255) PRIMARY KEY,
    user_id varchar(24) NOT NULL,
    permission varchar(255) NOT NULL,
    grant_type varchar(255),
    reason varchar(255),
    expires_at timestamp with time zone,
    created_by varchar(255),
    created_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS user_access_scopes (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id varchar(24) NOT NULL,
    education_unit_id varchar(24) NOT NULL,
    modules varchar(255),
    role varchar(255)
);

CREATE TABLE IF NOT EXISTS work_shifts (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id varchar(24) NOT NULL,
    school_id varchar(255),
    room varchar(255),
    teaching_subject varchar(255),
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    type varchar(255),
    status varchar(255),
    note varchar(255),
    remuneration double precision,
    confirmed_by varchar(255),
    actual_start_time timestamp with time zone,
    actual_end_time timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_education_units_parent ON education_units(parent_id);
CREATE INDEX IF NOT EXISTS idx_education_units_type_status ON education_units(type, status);
CREATE INDEX IF NOT EXISTS idx_education_units_name ON education_units(name);
CREATE INDEX IF NOT EXISTS idx_user_sessions_session_tenant ON user_sessions(session_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_access_token_tenant ON user_sessions(access_token_hash, tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_refresh_token_tenant ON user_sessions(refresh_token_hash, tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_tenant_active ON user_sessions(user_id, tenant_id, revoked_at);
CREATE INDEX IF NOT EXISTS idx_auth_login_attempts_user_created ON auth_login_attempts(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_auth_login_attempts_email_created ON auth_login_attempts(attempted_email, created_at);
CREATE INDEX IF NOT EXISTS idx_auth_login_attempts_ip_result ON auth_login_attempts(ip_address, result);
CREATE INDEX IF NOT EXISTS idx_user_access_scopes_user ON user_access_scopes(user_id);
CREATE INDEX IF NOT EXISTS idx_user_access_scopes_education_unit ON user_access_scopes(education_unit_id);
CREATE INDEX IF NOT EXISTS idx_user_access_scopes_user_education_unit ON user_access_scopes(user_id, education_unit_id);
