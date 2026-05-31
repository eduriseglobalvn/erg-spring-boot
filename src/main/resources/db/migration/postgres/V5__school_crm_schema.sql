
CREATE TABLE IF NOT EXISTS crm_school_profiles (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id varchar(24) NOT NULL,
    owner_user_id varchar(255),
    lead_owner_user_id varchar(255),
    manager_user_id varchar(255),
    source varchar(255),
    campaign varchar(255),
    introduced_by_user_id varchar(255),
    introduced_by_name varchar(255),
    introduced_by_phone varchar(255),
    introduced_by_note varchar(1000),
    province varchar(255),
    district varchar(255),
    address varchar(1000),
    note varchar(2000)
);

CREATE TABLE IF NOT EXISTS crm_school_contacts (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id varchar(24) NOT NULL,
    contact_type varchar(255),
    full_name varchar(255),
    position varchar(255),
    phone varchar(255),
    email varchar(255),
    zalo varchar(255),
    is_primary boolean,
    note varchar(1000)
);

CREATE TABLE IF NOT EXISTS crm_school_deals (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id varchar(24) NOT NULL,
    owner_user_id varchar(255) NOT NULL,
    stage varchar(255),
    expected_revenue numeric(18,2),
    signed_revenue numeric(18,2),
    probability numeric(5,2),
    lost_reason varchar(255),
    lost_note varchar(1000),
    signed_at timestamp with time zone,
    lost_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS crm_school_activities (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id varchar(24) NOT NULL,
    deal_id varchar(24),
    actor_user_id varchar(255),
    type varchar(255),
    content varchar(4000),
    next_follow_up_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS crm_referrals (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id varchar(24) NOT NULL,
    referrer_user_id varchar(255),
    referrer_name varchar(255),
    referrer_phone varchar(255),
    referral_type varchar(255),
    reward_amount numeric(18,2),
    status varchar(255),
    note varchar(1000)
);

CREATE INDEX IF NOT EXISTS idx_crm_school_profiles_school ON crm_school_profiles(school_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_profiles_owner ON crm_school_profiles(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_profiles_referrer ON crm_school_profiles(introduced_by_user_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_contacts_school ON crm_school_contacts(school_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_contacts_type ON crm_school_contacts(school_id, contact_type);
CREATE INDEX IF NOT EXISTS idx_crm_school_contacts_phone ON crm_school_contacts(phone);
CREATE INDEX IF NOT EXISTS idx_crm_school_deals_school ON crm_school_deals(school_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_deals_owner_stage ON crm_school_deals(owner_user_id, stage);
CREATE INDEX IF NOT EXISTS idx_crm_school_deals_created ON crm_school_deals(owner_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_crm_school_activities_school ON crm_school_activities(school_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_activities_actor ON crm_school_activities(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_activities_follow_up ON crm_school_activities(next_follow_up_at);
CREATE INDEX IF NOT EXISTS idx_crm_referrals_school ON crm_referrals(school_id);
CREATE INDEX IF NOT EXISTS idx_crm_referrals_user ON crm_referrals(referrer_user_id);

