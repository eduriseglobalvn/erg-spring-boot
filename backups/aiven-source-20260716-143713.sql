--
-- PostgreSQL database dump
--

\restrict AeELXRsYDuSodcrZvAfiAoRbGjZNHFZXYH3ZGdSrCWi8J27WPDvXmVMcbfnOyhD

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS '';


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: cleanup_stale_delivery_attempts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.cleanup_stale_delivery_attempts() RETURNS TABLE(deleted_count bigint, retention_days_used integer)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_retention_days INTEGER;
    v_enabled        BOOLEAN;
    v_cutoff         TIMESTAMPTZ;
    v_deleted        BIGINT;
BEGIN
    SELECT retention_days, enabled
      INTO v_retention_days, v_enabled
      FROM notification_retention_config
     WHERE table_name = 'notification_delivery_attempts'
       AND enabled = true;

    IF NOT FOUND OR NOT v_enabled THEN
        RETURN QUERY SELECT 0::BIGINT, 0::INTEGER;
        RETURN;
    END IF;

    v_cutoff := date_trunc('day', now()) - (v_retention_days || ' days')::INTERVAL;

    DELETE FROM notification_delivery_attempts
     WHERE created_at < v_cutoff;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;

    RETURN QUERY SELECT v_deleted, v_retention_days;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: academic_classes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_classes (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    school_id character varying(255) NOT NULL,
    name character varying(255),
    grade character varying(255),
    academic_year character varying(255),
    status character varying(255),
    homeroom_teacher_id character varying(255)
);


--
-- Name: account_verification_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.account_verification_codes (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    user_id character varying(24) NOT NULL,
    tenant_id character varying(255),
    purpose character varying(32) NOT NULL,
    code_hash character varying(255) NOT NULL,
    target_email character varying(255),
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    last_sent_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: assignment_recipients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignment_recipients (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    assignment_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    student_id character varying(36) NOT NULL,
    student_user_id character varying(36),
    class_id character varying(36),
    source_mode character varying(32) DEFAULT 'class'::character varying NOT NULL
);

ALTER TABLE ONLY public.assignment_recipients FORCE ROW LEVEL SECURITY;


--
-- Name: assignment_resources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignment_resources (
    assignment_id character varying(36) NOT NULL,
    quiz_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    quiz_version_id character varying(36),
    quiz_version_label character varying(64),
    order_index integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL
);

ALTER TABLE ONLY public.assignment_resources FORCE ROW LEVEL SECURITY;


--
-- Name: attempt_answers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attempt_answers (
    attempt_id character varying(36) NOT NULL,
    quiz_version_question_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    question_kind character varying(64),
    raw_answer jsonb,
    correct boolean,
    partially_right boolean DEFAULT false NOT NULL,
    awarded_points numeric(8,2) DEFAULT 0 NOT NULL,
    max_points numeric(8,2) DEFAULT 0 NOT NULL,
    graded_by character varying(16),
    graded_at timestamp with time zone,
    needs_manual_grade boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY public.attempt_answers FORCE ROW LEVEL SECURITY;


--
-- Name: attempt_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attempt_events (
    id character varying(36) NOT NULL,
    attempt_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    quiz_id character varying(36),
    package_id character varying(128),
    type character varying(48) NOT NULL,
    question_id character varying(64),
    answer jsonb,
    result jsonb,
    meta jsonb,
    created_at timestamp with time zone,
    received_at timestamp with time zone NOT NULL
);

ALTER TABLE ONLY public.attempt_events FORCE ROW LEVEL SECURITY;


--
-- Name: auth_login_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_login_attempts (
    id character varying(36) NOT NULL,
    tenant_id character varying(255),
    user_id character varying(36),
    attempted_email character varying(255),
    attempted_email_hash character varying(255),
    ip_address character varying(255),
    country_code character varying(255),
    country_name character varying(255),
    continent_code character varying(255),
    user_agent character varying(255),
    device_id character varying(255),
    device_name character varying(255),
    portal character varying(255),
    result character varying(255),
    reason character varying(255),
    created_at timestamp with time zone
);


--
-- Name: certificates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.certificates (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id character varying(36) NOT NULL,
    name character varying(255),
    issued_by character varying(255),
    issue_date timestamp with time zone,
    expiry_date timestamp with time zone,
    image_url character varying(255)
);


--
-- Name: content_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_assets (
    id character varying(96) NOT NULL,
    tenant_id character varying(96) NOT NULL,
    asset_type character varying(64) NOT NULL,
    title character varying(500),
    storage_provider character varying(64),
    storage_key text,
    storage_url text,
    mime_type character varying(255),
    byte_size bigint,
    checksum_sha256 character varying(64),
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by character varying(96),
    updated_by character varying(96),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: content_item_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_item_assets (
    id character varying(96) NOT NULL,
    tenant_id character varying(96) NOT NULL,
    content_item_id character varying(96) NOT NULL,
    content_asset_id character varying(96) NOT NULL,
    asset_role character varying(64) DEFAULT 'primary'::character varying NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by character varying(96),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: content_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_items (
    id character varying(96) NOT NULL,
    tenant_id character varying(96) NOT NULL,
    content_type character varying(64) NOT NULL,
    title character varying(500) NOT NULL,
    description text,
    status character varying(32) DEFAULT 'draft'::character varying NOT NULL,
    visibility character varying(32) DEFAULT 'private'::character varying NOT NULL,
    owner_id character varying(96),
    created_by character varying(96),
    updated_by character varying(96),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: crm_referrals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crm_referrals (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id character varying(36) NOT NULL,
    referrer_user_id character varying(36),
    referrer_name character varying(255),
    referrer_phone character varying(255),
    referral_type character varying(255),
    reward_amount numeric(18,2),
    status character varying(255),
    note character varying(1000)
);


--
-- Name: crm_school_activities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crm_school_activities (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id character varying(36) NOT NULL,
    deal_id character varying(36),
    actor_user_id character varying(36),
    type character varying(255),
    content character varying(4000),
    next_follow_up_at timestamp with time zone
);


--
-- Name: crm_school_contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crm_school_contacts (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id character varying(36) NOT NULL,
    contact_type character varying(255),
    full_name character varying(255),
    "position" character varying(255),
    phone character varying(255),
    email character varying(255),
    zalo character varying(255),
    is_primary boolean,
    note character varying(1000)
);


--
-- Name: crm_school_deals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crm_school_deals (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id character varying(36) NOT NULL,
    owner_user_id character varying(36) NOT NULL,
    stage character varying(255),
    expected_revenue numeric(18,2),
    signed_revenue numeric(18,2),
    probability numeric(5,2),
    lost_reason character varying(255),
    lost_note character varying(1000),
    signed_at timestamp with time zone,
    lost_at timestamp with time zone
);


--
-- Name: crm_school_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crm_school_profiles (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    school_id character varying(36) NOT NULL,
    owner_user_id character varying(36),
    lead_owner_user_id character varying(36),
    manager_user_id character varying(36),
    source character varying(255),
    campaign character varying(255),
    introduced_by_user_id character varying(36),
    introduced_by_name character varying(255),
    introduced_by_phone character varying(255),
    introduced_by_note character varying(1000),
    province character varying(255),
    district character varying(255),
    address character varying(1000),
    note character varying(2000)
);


--
-- Name: curriculum_levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_levels (
    id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    subject_id character varying(36) NOT NULL,
    code character varying(96) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: curriculum_subjects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_subjects (
    id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    code character varying(96) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: curriculum_topics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_topics (
    id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    level_id character varying(36) NOT NULL,
    code character varying(96) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: education_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.education_units (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name character varying(255),
    slug character varying(255),
    type character varying(255),
    parent_id character varying(36),
    logo_url character varying(255),
    description character varying(255),
    status character varying(255),
    owner_user_id character varying(255),
    created_by character varying(255),
    source character varying(255),
    signed_at timestamp with time zone,
    activated_at timestamp with time zone,
    tenant_id character varying(36) NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: employee_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_profiles (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    employee_code character varying(100) NOT NULL,
    department_id character varying(36),
    job_title character varying(150),
    employment_type character varying(50),
    joined_at date,
    terminated_at date,
    experience_years numeric(5,2),
    experience_summary character varying(2000),
    manager_id character varying(36),
    work_location character varying(255),
    emergency_contact jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_employee_profiles_employment_dates CHECK (((terminated_at IS NULL) OR (joined_at IS NULL) OR (terminated_at >= joined_at))),
    CONSTRAINT ck_employee_profiles_experience_years CHECK (((experience_years IS NULL) OR (experience_years >= (0)::numeric)))
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: lms_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_assignments (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    academic_class_id character varying(255) NOT NULL,
    quiz_id character varying(255),
    subject_id character varying(255),
    student_ids jsonb,
    due_at timestamp with time zone,
    teacher_note character varying(255),
    status character varying(255),
    assigned_by character varying(255),
    recipient_mode character varying(255),
    attempt_limit integer,
    max_duration_minutes integer,
    start_at timestamp with time zone,
    title character varying(500),
    idempotency_key character varying(255),
    recipient_count integer
);


--
-- Name: lms_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_attempts (
    id character varying(36) NOT NULL,
    tenant_id character varying(255),
    assignment_id character varying(255),
    quiz_id character varying(255),
    student_id character varying(255),
    package_id character varying(255),
    package_hash character varying(255),
    quiz_version character varying(255),
    status character varying(255),
    answers jsonb,
    events jsonb,
    client jsonb,
    score double precision,
    max_score double precision,
    percent double precision,
    passed boolean,
    started_at timestamp with time zone,
    submitted_at timestamp with time zone,
    updated_at timestamp with time zone,
    quiz_version_id character varying(36),
    class_id character varying(36),
    school_id character varying(36),
    mode character varying(32),
    grading_mode character varying(32),
    submitted_count integer,
    total_questions integer,
    last_active_at timestamp with time zone,
    client_runtime character varying(32),
    start_idempotency_key character varying(255)
);


--
-- Name: lms_levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_levels (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    subject_id character varying(255),
    name character varying(255),
    code character varying(255),
    sort_order integer,
    status character varying(255)
);


--
-- Name: lms_questions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_questions (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    scope jsonb,
    subject_id character varying(255),
    level_id character varying(255),
    topic_id character varying(255),
    type character varying(255) NOT NULL,
    stem character varying(4000),
    choices jsonb,
    answer jsonb,
    metadata jsonb,
    status character varying(255),
    archived_at timestamp with time zone,
    category_id character varying(255),
    objective character varying(2000),
    difficulty character varying(64),
    tags jsonb,
    content jsonb,
    mastery_rate double precision,
    usage_count integer,
    schools_using integer,
    owner_user_id character varying(36),
    schema_version integer,
    deleted_at timestamp with time zone,
    settings jsonb
);


--
-- Name: lms_quiz_questions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_quiz_questions (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id character varying(255) NOT NULL,
    quiz_id character varying(36) NOT NULL,
    question_id character varying(36) NOT NULL,
    order_index integer DEFAULT 0 NOT NULL,
    source character varying(32) DEFAULT 'manual'::character varying NOT NULL,
    points numeric(8,2),
    settings jsonb,
    metadata jsonb
);


--
-- Name: lms_quizzes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_quizzes (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    scope jsonb,
    title character varying(255),
    kind character varying(255),
    subject_id character varying(255),
    level_id character varying(255),
    topic_ids jsonb,
    question_ids jsonb,
    slides jsonb,
    settings jsonb,
    result jsonb,
    theme jsonb,
    theme_id character varying(255),
    status character varying(255),
    version integer,
    package_hash character varying(255),
    published_at timestamp with time zone,
    portal character varying(255),
    subtitle character varying(255),
    category_id character varying(255),
    current_version_id character varying(36),
    latest_draft_id character varying(36),
    owner_user_id character varying(36),
    content_scope jsonb,
    schema_version integer,
    idempotency_key character varying(255),
    deleted_at timestamp with time zone
);


--
-- Name: lms_students; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_students (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    school_id character varying(255),
    academic_class_id character varying(255),
    student_code character varying(255),
    full_name character varying(255),
    username character varying(255),
    auth_user_id character varying(255),
    email character varying(255),
    gender character varying(255),
    birthday timestamp with time zone,
    phone character varying(255),
    address character varying(1000),
    parent_name character varying(255),
    parent_phone character varying(255),
    parent_email character varying(255),
    parent_relationship character varying(255),
    enrollment_date timestamp with time zone,
    note character varying(1000),
    status character varying(255),
    metrics jsonb
);


--
-- Name: lms_subjects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_subjects (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    scope jsonb,
    name character varying(255),
    code character varying(255),
    status character varying(255)
);


--
-- Name: lms_topics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lms_topics (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255),
    level_id character varying(255),
    name character varying(255),
    code character varying(255),
    sort_order integer,
    status character varying(255)
);


--
-- Name: media_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_assets (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    sha256 character varying(64) NOT NULL,
    storage_key character varying(1000),
    url character varying(2000),
    mime_type character varying(255),
    byte_size bigint,
    width integer,
    height integer,
    original_name character varying(500),
    status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    uploaded_by character varying(36),
    ref_count integer DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY public.media_assets FORCE ROW LEVEL SECURITY;


--
-- Name: media_references; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_references (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    media_id character varying(36) NOT NULL,
    owner_type character varying(32) NOT NULL,
    owner_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.media_references FORCE ROW LEVEL SECURITY;


--
-- Name: notification_delivery_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_delivery_attempts (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    outbox_id character varying(36),
    notification_id character varying(36) NOT NULL,
    device_registration_id character varying(36),
    target_kind character varying(32) NOT NULL,
    target_ref_hash character varying(128),
    provider character varying(32) DEFAULT 'firebase'::character varying NOT NULL,
    provider_message_id character varying(255),
    http_status integer,
    firebase_error_code character varying(120),
    status character varying(32) NOT NULL,
    duration_ms bigint,
    response_sample character varying(500)
);


--
-- Name: notification_device_registrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_device_registrations (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    user_id character varying(36) NOT NULL,
    portal character varying(32) NOT NULL,
    platform character varying(40) NOT NULL,
    firebase_installation_id character varying(180),
    registration_token text,
    registration_token_hash character varying(128),
    endpoint_hash character varying(128),
    service_worker_scope character varying(255),
    browser_name character varying(80),
    browser_version character varying(80),
    os_name character varying(80),
    user_agent_hash character varying(128),
    locale character varying(16),
    timezone character varying(64),
    permission_state character varying(24) DEFAULT 'default'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    last_seen_at timestamp with time zone,
    token_updated_at timestamp with time zone,
    revoked_at timestamp with time zone
);


--
-- Name: notification_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_messages (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    event_type character varying(80) NOT NULL,
    source_type character varying(80),
    source_id character varying(80),
    title character varying(180) NOT NULL,
    body_preview character varying(500) NOT NULL,
    action_url character varying(500),
    priority character varying(24) DEFAULT 'normal'::character varying NOT NULL,
    channel character varying(24) DEFAULT 'push'::character varying NOT NULL,
    visibility character varying(24) DEFAULT 'private'::character varying NOT NULL,
    collapse_key character varying(120),
    dedupe_key character varying(180),
    payload_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by character varying(36),
    scheduled_at timestamp with time zone,
    expires_at timestamp with time zone
);


--
-- Name: notification_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_outbox (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    notification_id character varying(36) NOT NULL,
    target_kind character varying(32) NOT NULL,
    target_ref character varying(255) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(24) DEFAULT 'pending'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 8 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    locked_by character varying(80),
    locked_at timestamp with time zone,
    last_error_code character varying(120),
    last_error_message character varying(500)
);


--
-- Name: notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_preferences (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    user_id character varying(36) NOT NULL,
    portal character varying(32) NOT NULL,
    event_type character varying(80) NOT NULL,
    push_enabled boolean DEFAULT true NOT NULL,
    inbox_enabled boolean DEFAULT true NOT NULL,
    email_enabled boolean DEFAULT false NOT NULL,
    quiet_hours jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: notification_recipients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_recipients (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    notification_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    portal character varying(32) NOT NULL,
    role_key character varying(64),
    status character varying(24) DEFAULT 'unread'::character varying NOT NULL,
    read_at timestamp with time zone,
    archived_at timestamp with time zone,
    delivery_policy character varying(24) DEFAULT 'push_and_inbox'::character varying NOT NULL
);


--
-- Name: notification_retention_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_retention_config (
    id character varying(36) NOT NULL,
    table_name character varying(128) NOT NULL,
    retention_days integer DEFAULT 90 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_nrc_retention_days CHECK ((retention_days >= 1))
);


--
-- Name: notification_topic_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_topic_memberships (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(64) NOT NULL,
    device_registration_id character varying(36) NOT NULL,
    topic_name character varying(255) NOT NULL,
    membership_status character varying(24) DEFAULT 'active'::character varying NOT NULL,
    subscribed_at timestamp with time zone,
    unsubscribed_at timestamp with time zone,
    last_sync_at timestamp with time zone
);


--
-- Name: permission_bindings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_bindings (
    id character varying(36) NOT NULL,
    permission_id character varying(36) NOT NULL,
    surface_type character varying(32) NOT NULL,
    surface_identifier character varying(512) NOT NULL,
    description character varying(1000),
    enforced boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_permission_bindings_surface_type CHECK (((surface_type)::text = ANY ((ARRAY['REST'::character varying, 'GRAPHQL'::character varying, 'ROUTE'::character varying, 'SERVICE'::character varying])::text[])))
);


--
-- Name: permission_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_groups (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name character varying(255) NOT NULL,
    label character varying(255),
    display_order integer
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name character varying(255) NOT NULL,
    group_name character varying(255),
    label character varying(255),
    description character varying(255),
    resource character varying(255),
    action character varying(128),
    portal character varying(128),
    supported_scope character varying(128),
    is_system boolean DEFAULT false NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deprecated_at timestamp with time zone
);


--
-- Name: profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profiles (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id character varying(36) NOT NULL,
    full_name character varying(255),
    bio character varying(255),
    phone character varying(255),
    date_of_birth timestamp with time zone,
    gender character varying(255),
    address character varying(255),
    city character varying(255),
    district character varying(255),
    social_links_json character varying(255),
    avatar_url character varying(255),
    is_profile_completed boolean,
    teaching_philosophy character varying(255),
    specialties_json character varying(255),
    rating double precision,
    internal_note character varying(255)
);


--
-- Name: question_bank_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_bank_categories (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    subject_id character varying(36) NOT NULL,
    level_id character varying(36) NOT NULL,
    label character varying(255)
);

ALTER TABLE ONLY public.question_bank_categories FORCE ROW LEVEL SECURITY;


--
-- Name: question_bank_levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_bank_levels (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    subject_id character varying(36) NOT NULL,
    label character varying(255),
    description character varying(2000),
    order_index integer
);

ALTER TABLE ONLY public.question_bank_levels FORCE ROW LEVEL SECURITY;


--
-- Name: question_bank_subjects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_bank_subjects (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    label character varying(255),
    description character varying(2000),
    company_scope_label character varying(255)
);

ALTER TABLE ONLY public.question_bank_subjects FORCE ROW LEVEL SECURITY;


--
-- Name: question_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_types (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    label character varying(255) NOT NULL,
    authoring_type character varying(64) NOT NULL,
    runtime_kind character varying(64) NOT NULL,
    grading_policy character varying(32) DEFAULT 'auto'::character varying NOT NULL,
    is_auto_gradable boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL
);


--
-- Name: quiz_bank_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_bank_items (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    title character varying(500),
    kind character varying(64),
    status character varying(64),
    subject_id character varying(36),
    level_id character varying(36),
    question_ids jsonb,
    source_mode character varying(64),
    duration_label character varying(128),
    owner_user_id character varying(36)
);

ALTER TABLE ONLY public.quiz_bank_items FORCE ROW LEVEL SECURITY;


--
-- Name: quiz_drafts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_drafts (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    settings jsonb,
    player_template jsonb,
    tree jsonb,
    schema_version integer DEFAULT 1 NOT NULL,
    doc_version bigint DEFAULT 0 NOT NULL,
    base_version_id character varying(36),
    updated_by character varying(36)
);

ALTER TABLE ONLY public.quiz_drafts FORCE ROW LEVEL SECURITY;


--
-- Name: quiz_packages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_packages (
    id character varying(128) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_id character varying(36) NOT NULL,
    quiz_version_id character varying(36) NOT NULL,
    quiz_version character varying(64),
    tenant_id character varying(255) NOT NULL,
    content_hash character varying(128),
    signature character varying(1000),
    public_key_id character varying(128),
    grading_mode character varying(32) DEFAULT 'client-first'::character varying NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    package_format character varying(32) DEFAULT 'single'::character varying NOT NULL,
    assets_manifest jsonb,
    package_json jsonb,
    generated_at timestamp with time zone,
    expires_at timestamp with time zone
);

ALTER TABLE ONLY public.quiz_packages FORCE ROW LEVEL SECURITY;


--
-- Name: quiz_version_questions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_version_questions (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_version_id character varying(36) NOT NULL,
    section_id character varying(36),
    tenant_id character varying(255) NOT NULL,
    runtime_question_id character varying(64) NOT NULL,
    source_slide_id character varying(64),
    bank_question_id character varying(36),
    kind character varying(64) NOT NULL,
    authoring_type character varying(64),
    points numeric(8,2) DEFAULT 0 NOT NULL,
    is_auto_gradable boolean DEFAULT true NOT NULL,
    grading_policy character varying(32) DEFAULT 'auto'::character varying NOT NULL,
    order_index integer DEFAULT 0 NOT NULL,
    body jsonb,
    answer_key jsonb
);

ALTER TABLE ONLY public.quiz_version_questions FORCE ROW LEVEL SECURITY;


--
-- Name: quiz_version_sections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_version_sections (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_version_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    title character varying(500),
    order_index integer DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY public.quiz_version_sections FORCE ROW LEVEL SECURITY;


--
-- Name: quiz_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_versions (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    version_label character varying(64) NOT NULL,
    version_number integer NOT NULL,
    authoring_snapshot jsonb,
    runtime_quiz jsonb,
    answer_key jsonb,
    grading_mode character varying(32) DEFAULT 'client-first'::character varying NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    content_hash character varying(128),
    published_by character varying(36),
    published_at timestamp with time zone,
    is_active boolean DEFAULT false NOT NULL,
    idempotency_key character varying(255)
);

ALTER TABLE ONLY public.quiz_versions FORCE ROW LEVEL SECURITY;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id character varying(36) NOT NULL,
    permission_id character varying(36) NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    name character varying(255),
    description character varying(255),
    is_default boolean,
    updated_by character varying(36)
);


--
-- Name: school_academic_years; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_academic_years (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    school_id character varying(36) NOT NULL,
    start_year integer NOT NULL,
    end_year integer NOT NULL,
    status character varying(20) DEFAULT 'PLANNED'::character varying NOT NULL,
    starts_on date,
    ends_on date,
    version bigint DEFAULT 0 NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_school_year_dates CHECK (((ends_on IS NULL) OR (starts_on IS NULL) OR (ends_on >= starts_on))),
    CONSTRAINT ck_school_year_range CHECK ((end_year = (start_year + 1))),
    CONSTRAINT ck_school_year_status CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'CURRENT'::character varying, 'CLOSED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: school_grades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_grades (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    school_id character varying(36) NOT NULL,
    academic_year_id character varying(36) NOT NULL,
    grade_code character varying(32) NOT NULL,
    label character varying(100) NOT NULL,
    sort_order integer NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    source character varying(20) DEFAULT 'DEFAULT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_school_grade_source CHECK (((source)::text = ANY ((ARRAY['DEFAULT'::character varying, 'MANUAL'::character varying, 'IMPORT'::character varying])::text[]))),
    CONSTRAINT ck_school_grade_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: school_operational_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_operational_profiles (
    school_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    school_type character varying(32) NOT NULL,
    address character varying(500),
    district character varying(255),
    principal_name character varying(255),
    contact_phone character varying(50),
    contact_email character varying(255),
    onboarding_status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    current_academic_year_id character varying(36),
    version bigint DEFAULT 0 NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_school_profile_status CHECK (((onboarding_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ONBOARDING'::character varying, 'ACTIVE'::character varying, 'PAUSED'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT ck_school_profile_type CHECK (((school_type)::text = ANY ((ARRAY['PRIMARY'::character varying, 'SECONDARY'::character varying, 'HIGH_SCHOOL'::character varying, 'UNIVERSITY'::character varying, 'TRAINING_CENTER'::character varying])::text[])))
);


--
-- Name: security_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_events (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36),
    event_type character varying(100) NOT NULL,
    severity character varying(20) NOT NULL,
    portal character varying(50),
    device_id character varying(255),
    ip_address character varying(100),
    reason character varying(500),
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    platform character varying(32),
    result character varying(32),
    risk_code character varying(100),
    session_id character varying(36),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: student_group_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_group_members (
    group_id character varying(36) NOT NULL,
    student_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL
);

ALTER TABLE ONLY public.student_group_members FORCE ROW LEVEL SECURITY;


--
-- Name: student_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_groups (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    name character varying(255),
    class_id character varying(36),
    owner_user_id character varying(36),
    student_count integer DEFAULT 0 NOT NULL,
    note text,
    color character varying(64)
);

ALTER TABLE ONLY public.student_groups FORCE ROW LEVEL SECURITY;


--
-- Name: student_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_progress (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id character varying(255) NOT NULL,
    student_user_id character varying(36) NOT NULL,
    student_name character varying(500),
    quiz_id character varying(36) NOT NULL,
    quiz_title character varying(500),
    assignment_id character varying(36),
    class_id character varying(36),
    mode character varying(32),
    status character varying(32),
    current_question_index integer,
    current_question_title character varying(500),
    answered_count integer DEFAULT 0 NOT NULL,
    total_questions integer DEFAULT 0 NOT NULL,
    percent_complete numeric(6,2),
    score numeric(8,2),
    passed boolean,
    started_at timestamp with time zone,
    submitted_at timestamp with time zone,
    last_active_at timestamp with time zone
);

ALTER TABLE ONLY public.student_progress FORCE ROW LEVEL SECURITY;


--
-- Name: teaching_schedule_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teaching_schedule_batches (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    school_id character varying(36) NOT NULL,
    academic_year character varying(20),
    apply_from date NOT NULL,
    repeat_weeks integer NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    source character varying(30) DEFAULT 'LCMS_BULK_GRID'::character varying NOT NULL,
    idempotency_key character varying(255),
    payload_hash character varying(64),
    note text,
    created_by character varying(36),
    published_by character varying(36),
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_tsb_repeat_weeks CHECK (((repeat_weeks >= 1) AND (repeat_weeks <= 52))),
    CONSTRAINT chk_tsb_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: teaching_schedule_conflicts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teaching_schedule_conflicts (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    batch_id character varying(36),
    event_id character varying(36),
    conflicting_event_id character varying(36),
    conflict_type character varying(50) NOT NULL,
    severity character varying(20) DEFAULT 'ERROR'::character varying NOT NULL,
    message text,
    client_row_id character varying(100),
    resolved boolean DEFAULT false NOT NULL,
    resolved_by character varying(36),
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_tsc_conflict_type CHECK (((conflict_type)::text = ANY ((ARRAY['CLASS_TIME_CONFLICT'::character varying, 'TEACHER_TIME_CONFLICT'::character varying, 'ROOM_TIME_CONFLICT'::character varying])::text[]))),
    CONSTRAINT chk_tsc_severity CHECK (((severity)::text = ANY ((ARRAY['ERROR'::character varying, 'WARNING'::character varying])::text[])))
);


--
-- Name: teaching_schedule_event_teachers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teaching_schedule_event_teachers (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    event_id character varying(36) NOT NULL,
    teacher_user_id character varying(36) NOT NULL,
    role character varying(20) NOT NULL,
    display_name_snapshot character varying(255),
    color_key character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_tset_role CHECK (((role)::text = ANY ((ARRAY['MAIN'::character varying, 'ASSISTANT'::character varying])::text[])))
);


--
-- Name: teaching_schedule_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teaching_schedule_events (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    batch_id character varying(36) NOT NULL,
    school_id character varying(36) NOT NULL,
    academic_class_id character varying(36) NOT NULL,
    subject_id character varying(36) NOT NULL,
    level_id character varying(36),
    room_id character varying(36),
    room_name character varying(255),
    title character varying(500),
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    weekday smallint NOT NULL,
    session character varying(20),
    period_start smallint DEFAULT 1 NOT NULL,
    period_count smallint DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    note text,
    created_by character varying(36),
    updated_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_tse_period_count CHECK (((period_count >= 1) AND (period_count <= 10))),
    CONSTRAINT chk_tse_period_start CHECK ((period_start >= 1)),
    CONSTRAINT chk_tse_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT chk_tse_time_range CHECK ((start_at < end_at)),
    CONSTRAINT chk_tse_weekday CHECK (((weekday >= 1) AND (weekday <= 7)))
);


--
-- Name: teaching_schedule_rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teaching_schedule_rooms (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    school_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_tsr_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: topic_content_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.topic_content_items (
    id character varying(96) NOT NULL,
    tenant_id character varying(96) NOT NULL,
    topic_id character varying(96) NOT NULL,
    content_item_id character varying(96) NOT NULL,
    content_role character varying(64) NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    created_by character varying(96),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_access_scopes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_access_scopes (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id character varying(36) NOT NULL,
    education_unit_id character varying(36) NOT NULL,
    modules character varying(255),
    role character varying(255)
);


--
-- Name: user_activities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_activities (
    id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    action character varying(255),
    target_user_id character varying(36),
    ip_address character varying(255),
    user_agent character varying(255),
    description character varying(255),
    created_at timestamp with time zone,
    request_id character varying(100),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: user_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_permissions (
    id character varying(255) NOT NULL,
    user_id character varying(36) NOT NULL,
    permission character varying(255) NOT NULL,
    grant_type character varying(255),
    reason character varying(255),
    expires_at timestamp with time zone,
    created_by character varying(255),
    created_at timestamp with time zone
);


--
-- Name: user_role_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role_assignments (
    id character varying(36) NOT NULL,
    user_id character varying(24) NOT NULL,
    role_id character varying(24) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    scope_type character varying(32) NOT NULL,
    scope_id character varying(36),
    expires_at timestamp with time zone,
    assigned_by character varying(24) NOT NULL,
    revoked_at timestamp with time zone,
    revoked_by character varying(24),
    revoke_reason character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_user_role_assignments_expiry CHECK (((expires_at IS NULL) OR (expires_at > created_at))),
    CONSTRAINT ck_user_role_assignments_scope CHECK ((upper((scope_type)::text) = ANY (ARRAY['SYSTEM'::text, 'TENANT'::text, 'CENTER'::text, 'SCHOOL'::text, 'CLASS'::text]))),
    CONSTRAINT ck_user_role_assignments_scope_id CHECK (((upper((scope_type)::text) = ANY (ARRAY['SYSTEM'::text, 'TENANT'::text])) OR (scope_id IS NOT NULL)))
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL
);


--
-- Name: user_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_sessions (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id character varying(36) NOT NULL,
    session_id character varying(255),
    device_id character varying(255),
    device_name character varying(255),
    portal character varying(255),
    ip_address character varying(255),
    user_agent character varying(255),
    refresh_token_hash character varying(255),
    access_token_hash character varying(255),
    tenant_id character varying(255),
    last_active_at timestamp with time zone,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    revoked_reason character varying(255),
    device_fingerprint character varying(255),
    platform character varying(32) DEFAULT 'WEB_DESKTOP'::character varying
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    email character varying(255),
    password_hash character varying(255),
    full_name character varying(255),
    avatar_url character varying(255),
    status character varying(255),
    provider character varying(255),
    account_type character varying(255),
    last_login_provider character varying(255),
    tenant_id character varying(255),
    phone character varying(255),
    bio character varying(255),
    gender character varying(255),
    date_of_birth character varying(255),
    address character varying(255),
    city character varying(255),
    district character varying(255),
    job_title character varying(255),
    region character varying(255),
    extended_profile character varying(255),
    is_profile_completed boolean,
    last_login_at timestamp with time zone,
    login_count bigint,
    username character varying(254),
    must_change_password boolean DEFAULT false NOT NULL,
    recovery_email character varying(255),
    recovery_email_verified boolean DEFAULT false NOT NULL,
    authz_version bigint DEFAULT 1 NOT NULL
);


--
-- Name: work_shifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_shifts (
    id character varying(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    user_id character varying(36) NOT NULL,
    school_id character varying(255),
    room character varying(255),
    teaching_subject character varying(255),
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    type character varying(255),
    status character varying(255),
    note character varying(255),
    remuneration double precision,
    confirmed_by character varying(255),
    actual_start_time timestamp with time zone,
    actual_end_time timestamp with time zone
);


--
-- Data for Name: academic_classes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade, academic_year, status, homeroom_teacher_id) FROM stdin;
cls_ba_2025_6a1	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	sch_binh_an	Lop 6A1	grade-6	2025-2026	active	usr_teacher_ba
cls_cl_2025_6a1	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	sch_cat_lai	Lop 6A1	grade-6	2025-2026	active	usr_teacher_cl
sch_binh_an_g6a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A1	grade-6	2025-2026	active	\N
sch_binh_an_g6a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A2	grade-6	2025-2026	active	\N
sch_binh_an_g6a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A3	grade-6	2025-2026	active	\N
sch_binh_an_g6a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A4	grade-6	2025-2026	active	\N
sch_binh_an_g6a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A5	grade-6	2025-2026	active	\N
sch_binh_an_g6a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A6	grade-6	2025-2026	active	\N
sch_binh_an_g6a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A7	grade-6	2025-2026	active	\N
sch_binh_an_g6a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A8	grade-6	2025-2026	active	\N
sch_binh_an_g6a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	6A9	grade-6	2025-2026	active	\N
sch_binh_an_g7a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A1	grade-7	2025-2026	active	\N
sch_binh_an_g7a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A2	grade-7	2025-2026	active	\N
sch_binh_an_g7a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A3	grade-7	2025-2026	active	\N
sch_binh_an_g7a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A4	grade-7	2025-2026	active	\N
sch_binh_an_g7a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A5	grade-7	2025-2026	active	\N
sch_binh_an_g7a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A6	grade-7	2025-2026	active	\N
sch_binh_an_g7a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A7	grade-7	2025-2026	active	\N
sch_binh_an_g7a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A8	grade-7	2025-2026	active	\N
sch_binh_an_g7a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	7A9	grade-7	2025-2026	active	\N
sch_binh_an_g8a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A1	grade-8	2025-2026	active	\N
sch_binh_an_g8a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A2	grade-8	2025-2026	active	\N
sch_binh_an_g8a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A3	grade-8	2025-2026	active	\N
sch_binh_an_g8a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A4	grade-8	2025-2026	active	\N
sch_binh_an_g8a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A5	grade-8	2025-2026	active	\N
sch_binh_an_g8a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A6	grade-8	2025-2026	active	\N
sch_binh_an_g8a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A7	grade-8	2025-2026	active	\N
sch_binh_an_g8a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A8	grade-8	2025-2026	active	\N
sch_binh_an_g8a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	8A9	grade-8	2025-2026	active	\N
sch_binh_an_g9a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A1	grade-9	2025-2026	active	\N
sch_binh_an_g9a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A2	grade-9	2025-2026	active	\N
sch_binh_an_g9a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A3	grade-9	2025-2026	active	\N
sch_binh_an_g9a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A4	grade-9	2025-2026	active	\N
sch_binh_an_g9a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A5	grade-9	2025-2026	active	\N
sch_binh_an_g9a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A6	grade-9	2025-2026	active	\N
sch_binh_an_g9a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A7	grade-9	2025-2026	active	\N
sch_binh_an_g9a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A8	grade-9	2025-2026	active	\N
sch_binh_an_g9a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_binh_an	9A9	grade-9	2025-2026	active	\N
sch_cat_lai_g6a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A1	grade-6	2025-2026	active	\N
sch_cat_lai_g6a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A2	grade-6	2025-2026	active	\N
sch_cat_lai_g6a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A3	grade-6	2025-2026	active	\N
sch_cat_lai_g6a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A4	grade-6	2025-2026	active	\N
sch_cat_lai_g6a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A5	grade-6	2025-2026	active	\N
sch_cat_lai_g6a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A6	grade-6	2025-2026	active	\N
sch_cat_lai_g6a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A7	grade-6	2025-2026	active	\N
sch_cat_lai_g6a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A8	grade-6	2025-2026	active	\N
sch_cat_lai_g6a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	6A9	grade-6	2025-2026	active	\N
sch_cat_lai_g7a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A1	grade-7	2025-2026	active	\N
sch_cat_lai_g7a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A2	grade-7	2025-2026	active	\N
sch_cat_lai_g7a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A3	grade-7	2025-2026	active	\N
sch_cat_lai_g7a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A4	grade-7	2025-2026	active	\N
sch_cat_lai_g7a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A5	grade-7	2025-2026	active	\N
sch_cat_lai_g7a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A6	grade-7	2025-2026	active	\N
sch_cat_lai_g7a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A7	grade-7	2025-2026	active	\N
sch_cat_lai_g7a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A8	grade-7	2025-2026	active	\N
sch_cat_lai_g7a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	7A9	grade-7	2025-2026	active	\N
sch_cat_lai_g8a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A1	grade-8	2025-2026	active	\N
sch_cat_lai_g8a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A2	grade-8	2025-2026	active	\N
sch_cat_lai_g8a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A3	grade-8	2025-2026	active	\N
sch_cat_lai_g8a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A4	grade-8	2025-2026	active	\N
sch_cat_lai_g8a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A5	grade-8	2025-2026	active	\N
sch_cat_lai_g8a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A6	grade-8	2025-2026	active	\N
sch_cat_lai_g8a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A7	grade-8	2025-2026	active	\N
sch_cat_lai_g8a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A8	grade-8	2025-2026	active	\N
sch_cat_lai_g8a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	8A9	grade-8	2025-2026	active	\N
sch_cat_lai_g9a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A1	grade-9	2025-2026	active	\N
sch_cat_lai_g9a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A2	grade-9	2025-2026	active	\N
sch_cat_lai_g9a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A3	grade-9	2025-2026	active	\N
sch_cat_lai_g9a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A4	grade-9	2025-2026	active	\N
sch_cat_lai_g9a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A5	grade-9	2025-2026	active	\N
sch_cat_lai_g9a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A6	grade-9	2025-2026	active	\N
sch_cat_lai_g9a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A7	grade-9	2025-2026	active	\N
sch_cat_lai_g9a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A8	grade-9	2025-2026	active	\N
sch_cat_lai_g9a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_cat_lai	9A9	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g6a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A1	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A2	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A3	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A4	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A5	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A6	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A7	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A8	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g6a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	6A9	grade-6	2025-2026	active	\N
sch_truong_cong_dinh_g7a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A1	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A2	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A3	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A4	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A5	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A6	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A7	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A8	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g7a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	7A9	grade-7	2025-2026	active	\N
sch_truong_cong_dinh_g8a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A1	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A2	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A3	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A4	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A5	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A6	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A7	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A8	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g8a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	8A9	grade-8	2025-2026	active	\N
sch_truong_cong_dinh_g9a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A1	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A2	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A3	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A4	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A5	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a6	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A6	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a7	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A7	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a8	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A8	grade-9	2025-2026	active	\N
sch_truong_cong_dinh_g9a9	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_truong_cong_dinh	9A9	grade-9	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g1a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	1A1	grade-1	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g1a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	1A2	grade-1	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g1a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	1A3	grade-1	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g1a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	1A4	grade-1	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g1a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	1A5	grade-1	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g2a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	2A1	grade-2	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g2a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	2A2	grade-2	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g2a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	2A3	grade-2	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g2a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	2A4	grade-2	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g2a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	2A5	grade-2	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g3a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	3A1	grade-3	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g3a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	3A2	grade-3	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g3a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	3A3	grade-3	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g3a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	3A4	grade-3	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g3a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	3A5	grade-3	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g4a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	4A1	grade-4	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g4a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	4A2	grade-4	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g4a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	4A3	grade-4	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g4a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	4A4	grade-4	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g4a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	4A5	grade-4	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g5a1	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	5A1	grade-5	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g5a2	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	5A2	grade-5	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g5a3	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	5A3	grade-5	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g5a4	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	5A4	grade-5	2025-2026	active	\N
sch_nguyen_thi_minh_khai_g5a5	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sch_nguyen_thi_minh_khai	5A5	grade-5	2025-2026	active	\N
cls_ba_6a1_001	2026-07-11 04:26:18.867544+00	2026-07-11 04:26:18.867544+00	erg	sch_binh_an	6A1	6	2026-2027	ACTIVE	\N
cls_ba_6a2_001	2026-07-11 04:26:19.008265+00	2026-07-11 04:26:19.008265+00	erg	sch_binh_an	6A2	6	2026-2027	ACTIVE	\N
cls_ba_7a1_001	2026-07-11 04:26:19.148407+00	2026-07-11 04:26:19.148407+00	erg	sch_binh_an	7A1	7	2026-2027	ACTIVE	\N
cls_ba_7a2_001	2026-07-11 04:26:19.293489+00	2026-07-11 04:26:19.293489+00	erg	sch_binh_an	7A2	7	2026-2027	ACTIVE	\N
cls_ba_8a1_001	2026-07-11 04:26:19.438095+00	2026-07-11 04:26:19.438095+00	erg	sch_binh_an	8A1	8	2026-2027	ACTIVE	\N
cls_ba_9a1_001	2026-07-11 04:26:19.587309+00	2026-07-11 04:26:19.587309+00	erg	sch_binh_an	9A1	9	2026-2027	ACTIVE	\N
cls_cl_6a1_001	2026-07-11 04:26:19.72948+00	2026-07-11 04:26:19.72948+00	erg	sch_cat_lai	6A1	6	2026-2027	ACTIVE	\N
cls_cl_6a2_001	2026-07-11 04:26:19.86984+00	2026-07-11 04:26:19.86984+00	erg	sch_cat_lai	6A2	6	2026-2027	ACTIVE	\N
cls_cl_7a1_001	2026-07-11 04:26:20.282317+00	2026-07-11 04:26:20.282317+00	erg	sch_cat_lai	7A1	7	2026-2027	ACTIVE	\N
cls_cl_7a2_001	2026-07-11 04:26:20.425739+00	2026-07-11 04:26:20.425739+00	erg	sch_cat_lai	7A2	7	2026-2027	ACTIVE	\N
cls_cl_8a1_001	2026-07-11 04:26:20.565405+00	2026-07-11 04:26:20.565405+00	erg	sch_cat_lai	8A1	8	2026-2027	ACTIVE	\N
cls_cl_9a1_001	2026-07-11 04:26:20.705448+00	2026-07-11 04:26:20.705448+00	erg	sch_cat_lai	9A1	9	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_11	2026-07-13 09:07:11.776355+00	2026-07-13 09:07:11.776355+00	erg	019f5a8b-b528-7507-b347-52e990580f45	1A1	1	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_12	2026-07-13 09:07:23.974267+00	2026-07-13 09:07:23.974267+00	erg	019f5a8b-b528-7507-b347-52e990580f45	1A2	1	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_21	2026-07-13 09:07:35.972375+00	2026-07-13 09:07:35.972375+00	erg	019f5a8b-b528-7507-b347-52e990580f45	2A1	2	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_22	2026-07-13 09:07:48.130433+00	2026-07-13 09:07:48.130433+00	erg	019f5a8b-b528-7507-b347-52e990580f45	2A2	2	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_31	2026-07-13 09:08:00.251228+00	2026-07-13 09:08:00.251228+00	erg	019f5a8b-b528-7507-b347-52e990580f45	3A1	3	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_32	2026-07-13 09:12:42.755242+00	2026-07-13 09:12:42.755242+00	erg	019f5a8b-b528-7507-b347-52e990580f45	3A2	3	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_41	2026-07-13 09:12:53.59114+00	2026-07-13 09:12:53.59114+00	erg	019f5a8b-b528-7507-b347-52e990580f45	4A1	4	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_42	2026-07-13 09:13:04.467149+00	2026-07-13 09:13:04.467149+00	erg	019f5a8b-b528-7507-b347-52e990580f45	4A2	4	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_51	2026-07-13 09:13:15.347991+00	2026-07-13 09:13:15.347991+00	erg	019f5a8b-b528-7507-b347-52e990580f45	5A1	5	2026-2027	ACTIVE	\N
cls_demo_18z1bdq_52	2026-07-13 09:13:26.161262+00	2026-07-13 09:13:26.161262+00	erg	019f5a8b-b528-7507-b347-52e990580f45	5A2	5	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_61	2026-07-13 09:13:38.941546+00	2026-07-13 09:13:38.941546+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	6A1	6	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_62	2026-07-13 09:13:49.705853+00	2026-07-13 09:13:49.705853+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	6A2	6	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_71	2026-07-13 09:14:00.430072+00	2026-07-13 09:14:00.430072+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	7A1	7	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_72	2026-07-13 09:14:11.213412+00	2026-07-13 09:14:11.213412+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	7A2	7	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_81	2026-07-13 09:14:21.925643+00	2026-07-13 09:14:21.925643+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	8A1	8	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_82	2026-07-13 09:14:32.59225+00	2026-07-13 09:14:32.59225+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	8A2	8	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_91	2026-07-13 09:14:43.262135+00	2026-07-13 09:14:43.262135+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	9A1	9	2026-2027	ACTIVE	\N
cls_demo_1cwzsfk_92	2026-07-13 09:14:53.946191+00	2026-07-13 09:14:53.946191+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	9A2	9	2026-2027	ACTIVE	\N
cls_demo_aiw7ic_101	2026-07-13 09:15:06.714449+00	2026-07-13 09:15:06.714449+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	10A1	10	2026-2027	ACTIVE	\N
cls_demo_aiw7ic_102	2026-07-13 09:22:53.367649+00	2026-07-13 09:22:53.367649+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	10A2	10	2026-2027	ACTIVE	\N
cls_demo_aiw7ic_111	2026-07-13 09:23:04.255785+00	2026-07-13 09:23:04.255785+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	11A1	11	2026-2027	ACTIVE	\N
cls_demo_aiw7ic_112	2026-07-13 09:23:15.08194+00	2026-07-13 09:23:15.08194+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	11A2	11	2026-2027	ACTIVE	\N
cls_demo_aiw7ic_121	2026-07-13 09:23:25.98559+00	2026-07-13 09:23:25.98559+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	12A1	12	2026-2027	ACTIVE	\N
cls_demo_aiw7ic_122	2026-07-13 09:23:36.901076+00	2026-07-13 09:23:36.901076+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	12A2	12	2026-2027	ACTIVE	\N
cls_demo_1aqllkt_a1	2026-07-13 09:23:49.833866+00	2026-07-13 09:23:49.833866+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	K26-CN1A		2026-2027	ACTIVE	\N
cls_demo_1aqllkt_a2	2026-07-13 09:24:00.98647+00	2026-07-13 09:24:00.98647+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	K26-CN2A		2026-2027	ACTIVE	\N
cls_demo_1aqllkt_b1	2026-07-13 09:24:12.01151+00	2026-07-13 09:24:12.01151+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	K26-CN1B		2026-2027	ACTIVE	\N
cls_demo_1aqllkt_b2	2026-07-13 09:24:23.02184+00	2026-07-13 09:24:23.02184+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	K26-CN2B		2026-2027	ACTIVE	\N
cls_demo_24zqqe_a1	2026-07-13 10:48:40.385711+00	2026-07-13 10:48:40.385711+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	ERG-A1		2026-2027	ACTIVE	\N
cls_demo_24zqqe_a2	2026-07-13 10:51:28.546397+00	2026-07-13 10:51:28.546397+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	ERG-A2		2026-2027	ACTIVE	\N
cls_demo_24zqqe_b1	2026-07-13 10:51:35.121361+00	2026-07-13 10:51:35.121361+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	ERG-B1		2026-2027	ACTIVE	\N
cls_demo_24zqqe_b2	2026-07-13 10:51:41.693229+00	2026-07-13 10:51:41.693229+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	ERG-B2		2026-2027	ACTIVE	\N
\.


--
-- Data for Name: account_verification_codes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.account_verification_codes (id, created_at, updated_at, user_id, tenant_id, purpose, code_hash, target_email, attempts, max_attempts, expires_at, consumed_at, last_sent_at) FROM stdin;
\.


--
-- Data for Name: assignment_recipients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.assignment_recipients (id, created_at, updated_at, assignment_id, tenant_id, student_id, student_user_id, class_id, source_mode) FROM stdin;
\.


--
-- Data for Name: assignment_resources; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.assignment_resources (assignment_id, quiz_id, tenant_id, quiz_version_id, quiz_version_label, order_index, created_at) FROM stdin;
\.


--
-- Data for Name: attempt_answers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.attempt_answers (attempt_id, quiz_version_question_id, tenant_id, question_kind, raw_answer, correct, partially_right, awarded_points, max_points, graded_by, graded_at, needs_manual_grade) FROM stdin;
\.


--
-- Data for Name: attempt_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.attempt_events (id, attempt_id, tenant_id, quiz_id, package_id, type, question_id, answer, result, meta, created_at, received_at) FROM stdin;
\.


--
-- Data for Name: auth_login_attempts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.auth_login_attempts (id, tenant_id, user_id, attempted_email, attempted_email_hash, ip_address, country_code, country_name, continent_code, user_agent, device_id, device_name, portal, result, reason, created_at) FROM stdin;
019f4f6d-621f-7d3e-a099-b2e221b4d9c7	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	codex-runtime-check	Codex runtime check	lcms	SUCCESS	login_success	2026-07-11 04:26:36.447943+00
019f5022-6c79-75b3-8161-bbc5da6f249a	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	web	Browser on Desktop	lcms	SUCCESS	login_success	2026-07-11 07:44:21.113766+00
019f5025-1349-7284-9430-f54b9740433d	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	smoke-lms	Browser on Desktop	lms	SUCCESS	login_success	2026-07-11 07:47:14.889745+00
019f5274-a080-75e1-970e-0397d9ef6ac6	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-11 18:33:22.816629+00
019f5274-a60c-7793-809d-4b301004ec50	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-11 18:33:24.237338+00
019f5287-41f4-75a9-b416-d6917e3abe9e	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	taxonomy-smoke	Browser on Desktop	lcms	SUCCESS	login_success	2026-07-11 18:53:43.796671+00
019f52a7-b7b8-7c59-9149-f048ca2ecf82	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-11 19:29:11.096635+00
019f52a8-23c3-7a1e-beb8-b2954bd161df	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-11 19:29:38.755106+00
019f52a9-820d-7ac0-8b73-3a923631980c	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-11 19:31:08.429825+00
019f553f-b0e8-7fa8-9d2c-4d8b6cd789fb	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-12 07:34:25.257715+00
019f553f-b79d-75f1-8737-f003f02ae17f	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-12 07:34:26.973777+00
019f563d-df3b-75e0-b13b-650ba1c57565	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-12 12:12:03.260182+00
019f563d-e44f-7942-b944-7530cde9580b	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-12 12:12:04.559128+00
019f593c-9be7-7f3f-911e-765c68ae9c8e	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-13 02:09:32.135195+00
019f594f-4dfe-7bf3-add3-9fec10f1b42c	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-13 02:29:57.374236+00
019f5b1d-7cea-70bf-b02b-16def7c81e87	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-13 10:54:47.018248+00
019f5ead-b669-7dca-81de-13a12ef33864	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-14 03:31:10.569773+00
019f5eae-5362-7bbe-953b-e5a148a46c22	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-14 03:31:50.754461+00
019f5eae-8e90-79c0-b8d4-7f71b6ec7e07	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-7d554b0e-da68-4881-b44f-1e98a828ee51	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lms	SUCCESS	login_success	2026-07-14 03:32:05.904105+00
019f5eb8-2913-7044-b469-26bb54be59f0	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-14 03:42:35.284108+00
019f5f02-fa0e-72a2-9b71-2fb3e99bc7f7	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-14 05:04:18.446173+00
019f6368-df54-7298-8e18-c642d644eec1	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-15 01:34:05.140224+00
019f638c-2115-72ae-97b1-4cffa8dddb06	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-15 02:12:35.733187+00
019f6490-fa3e-70a1-9fd0-fa5463486f12	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	web	Chrome on Desktop	elearning	SUCCESS	login_success	2026-07-15 06:57:30.686746+00
019f65b8-0d3d-74af-a208-7664c74b4569	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-15 12:19:48.669693+00
019f65b8-83b9-7f05-a588-c54ba0b7f1af	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-15 12:20:19.001684+00
019f65d8-f799-7843-ae5a-4bbedaa842d7	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-7d554b0e-da68-4881-b44f-1e98a828ee51	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lms	SUCCESS	login_success	2026-07-15 12:55:45.817302+00
019f65ec-7427-71a1-83dc-fed6be66c181	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	SUCCESS	login_success	2026-07-15 13:17:02.887246+00
019f65ee-e30c-799a-8be1-fa93ebf9d523	erg	usr_super_admin	admin@erg.edu.vn	\N	0:0:0:0:0:0:0:1	\N	\N	\N	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lms	SUCCESS	login_success	2026-07-15 13:19:42.348397+00
\.


--
-- Data for Name: certificates; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.certificates (id, created_at, updated_at, user_id, name, issued_by, issue_date, expiry_date, image_url) FROM stdin;
\.


--
-- Data for Name: content_assets; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.content_assets (id, tenant_id, asset_type, title, storage_provider, storage_key, storage_url, mime_type, byte_size, checksum_sha256, status, metadata, created_by, updated_by, created_at, updated_at) FROM stdin;
ast_ic3_intro_deck	erg	GOOGLE_SLIDE	IC3 Intro to Computing Deck	metadata_only	\N	\N	application/vnd.google-apps.presentation	\N	\N	active	{"launchMode": "google_slide_embed", "canDownload": false, "selectedFileType": "PPTX"}	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
ast_tin_hoc_algo_pdf	erg	DOCUMENT	Algorithm Basics Worksheet	metadata_only	\N	\N	application/pdf	\N	\N	active	{"launchMode": "pdf_reader", "canDownload": true, "selectedFileType": "PDF"}	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
019f5966-8ff0-767c-8178-c3021d502e9a	erg	GOOGLE_SLIDE	Bài giảng 1	google_drive	\N	https://docs.google.com/presentation/d/1JibC9WV4X78CdzXEmrJA2j5v1arp7Q-ecOyBP9n296A/edit/embed?start=false&loop=false&delayms=3000	\N	\N	\N	active	{"launchMode": "google_slide_embed", "canDownload": false, "originalFileName": "Bài giảng 1", "selectedFileType": "PPTX"}	usr_super_admin	usr_super_admin	2026-07-13 02:55:21.584955+00	2026-07-13 02:55:21.584955+00
019f596f-fd15-7d9c-ac18-1f9cf5579213	erg	GOOGLE_SLIDE	Bài giảng 1	google_drive	\N	https://docs.google.com/presentation/d/1JibC9WV4X78CdzXEmrJA2j5v1arp7Q-ecOyBP9n296A/embed?start=false&loop=false&delayms=3000	\N	\N	\N	active	{"launchMode": "google_slide_embed", "canDownload": false, "originalFileName": "Bài giảng 1", "selectedFileType": "PPTX"}	usr_super_admin	usr_super_admin	2026-07-13 03:05:39.349011+00	2026-07-13 03:05:39.349011+00
\.


--
-- Data for Name: content_item_assets; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.content_item_assets (id, tenant_id, content_item_id, content_asset_id, asset_role, sort_order, metadata, created_by, created_at, updated_at) FROM stdin;
cia_ic3_intro_deck	erg	cnt_ic3_intro_deck	ast_ic3_intro_deck	primary	1	{"source": "canonical-seed"}	seed	2026-07-11 03:06:44.128568+00	2026-07-11 04:21:36.320291+00
cia_tin_hoc_algo_pdf	erg	cnt_tin_hoc_algo_pdf	ast_tin_hoc_algo_pdf	primary	1	{"source": "canonical-seed"}	seed	2026-07-11 03:06:44.128568+00	2026-07-11 04:21:36.320291+00
019f5966-9346-7757-abd3-02caaac6b62a	erg	019f5966-8db6-713e-89c7-2e1426b82052	019f5966-8ff0-767c-8178-c3021d502e9a	primary	1	{"source": "learning-resource-authoring", "selectedFileType": "PPTX"}	usr_super_admin	2026-07-13 02:55:22.438087+00	2026-07-13 02:55:22.438087+00
019f5970-002e-7c7c-a4c9-219a2bcec510	erg	019f596f-fb62-72c1-ad90-8531afa9d7c6	019f596f-fd15-7d9c-ac18-1f9cf5579213	primary	1	{"source": "learning-resource-authoring", "selectedFileType": "PPTX"}	usr_super_admin	2026-07-13 03:05:40.142208+00	2026-07-13 03:05:40.142208+00
\.


--
-- Data for Name: content_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.content_items (id, tenant_id, content_type, title, description, status, visibility, owner_id, created_by, updated_by, created_at, updated_at) FROM stdin;
cnt_ic3_safety_quiz	erg	EXERCISE	Internet Safety Practice	Seed exercise for safe internet behavior	published	public	seed	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
cnt_tin_hoc_algo_pdf	erg	LECTURE	Algorithm Basics Worksheet	Seed worksheet for algorithm basics	published	public	seed	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
cnt_ic3_intro_deck	erg	LECTURE	IC3 Intro to Computing Deck	Seed lecture for the IC3 introduction topic	published	public	seed	seed	usr_super_admin	2026-07-11 03:06:44.128568+00	2026-07-13 02:13:33.351725+00
019f5966-8db6-713e-89c7-2e1426b82052	erg	LECTURE	Bài giảng 1	\N	published	public	usr_super_admin	usr_super_admin	usr_super_admin	2026-07-13 02:55:21.014743+00	2026-07-13 02:55:21.014743+00
019f596f-fb62-72c1-ad90-8531afa9d7c6	erg	LECTURE	Bài giảng 1	\N	published	public	usr_super_admin	usr_super_admin	usr_super_admin	2026-07-13 03:05:38.91421+00	2026-07-13 03:05:38.91421+00
\.


--
-- Data for Name: crm_referrals; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.crm_referrals (id, created_at, updated_at, school_id, referrer_user_id, referrer_name, referrer_phone, referral_type, reward_amount, status, note) FROM stdin;
\.


--
-- Data for Name: crm_school_activities; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.crm_school_activities (id, created_at, updated_at, school_id, deal_id, actor_user_id, type, content, next_follow_up_at) FROM stdin;
\.


--
-- Data for Name: crm_school_contacts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.crm_school_contacts (id, created_at, updated_at, school_id, contact_type, full_name, "position", phone, email, zalo, is_primary, note) FROM stdin;
\.


--
-- Data for Name: crm_school_deals; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.crm_school_deals (id, created_at, updated_at, school_id, owner_user_id, stage, expected_revenue, signed_revenue, probability, lost_reason, lost_note, signed_at, lost_at) FROM stdin;
\.


--
-- Data for Name: crm_school_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.crm_school_profiles (id, created_at, updated_at, school_id, owner_user_id, lead_owner_user_id, manager_user_id, source, campaign, introduced_by_user_id, introduced_by_name, introduced_by_phone, introduced_by_note, province, district, address, note) FROM stdin;
\.


--
-- Data for Name: curriculum_levels; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.curriculum_levels (id, tenant_id, subject_id, code, name, description, status, sort_order, created_by, updated_by, created_at, updated_at) FROM stdin;
lvl_math_g6	erg	sub_math	grade-6	Grade 6 Mathematics	\N	active	1	\N	\N	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00
lvl_van_cb	erg	sub_ngu_van	co-ban	Văn Cơ Bản	\N	active	1	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
lvl_toan_cb	erg	sub_toan	co-ban	Toán Cơ Bản	\N	active	1	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
lvl_toan_nc	erg	sub_toan	nang-cao	Toán Nâng Cao	\N	active	2	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
lvl_ic3_gs6_l1	erg	sub_ic3	ic3-gs6-level-1	IC3 GS6 Level 1	Foundational IC3 level	active	10	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
lvl_tin_hoc_6	erg	sub_tin_hoc	tin-hoc-6	Tin hoc 6	Grade 6 computer science	active	20	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
lvl_python_foundation	erg	sub_python	python-foundation	Python Foundation	Python foundation level	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
lvl_python_intermediate	erg	sub_python	python-intermediate	Python Intermediate	Python intermediate level	active	20	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
lvl_mos_word	erg	sub_mos	mos-word	MOS Word	Microsoft Word level	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
lvl_mos_excel	erg	sub_mos	mos-excel	MOS Excel	Microsoft Excel level	active	20	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
lvl_mos_powerpoint	erg	sub_mos	mos-powerpoint	MOS PowerPoint	Microsoft PowerPoint level	active	30	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
\.


--
-- Data for Name: curriculum_subjects; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.curriculum_subjects (id, tenant_id, code, name, description, status, sort_order, created_by, updated_by, created_at, updated_at) FROM stdin;
sub_hoa_hoc	erg	hoa-hoc	Hóa Học	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_lich_su	erg	lich-su	Lịch Sử	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_math	erg	math	Mathematics	\N	active	0	\N	\N	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00
sub_ngu_van	erg	ngu-van	Ngữ Văn	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_sinh_hoc	erg	sinh-hoc	Sinh Học	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_tieng_anh	erg	tieng-anh	Tiếng Anh	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_toan	erg	toan	Toán	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_vat_ly	erg	vat-ly	Vật Lý	\N	active	0	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
sub_ic3	erg	ic3-digital-literacy	IC3 Digital Literacy	Digital literacy curriculum seed	active	10	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
sub_tin_hoc	erg	tin-hoc	Tin hoc	Computer science curriculum seed	active	20	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
sub_python	erg	python	Python	Python programming curriculum	active	900	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
sub_mos	erg	mos	MOS	Microsoft Office Specialist curriculum	active	910	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
sub_dia_ly	erg	dia-ly	Địa Lý	\N	archived	0	\N	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-12 10:00:04.098593+00
019f593d-fbb8-714a-8f78-0677af93c6ed	erg	ic3-gs6	Ic3 GS6	\N	active	0	usr_super_admin	usr_super_admin	2026-07-13 02:11:02.200237+00	2026-07-13 02:11:02.200237+00
\.


--
-- Data for Name: curriculum_topics; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.curriculum_topics (id, tenant_id, level_id, code, name, description, status, sort_order, created_by, updated_by, created_at, updated_at) FROM stdin;
top_math_g6_num	erg	lvl_math_g6	natural-numbers	Natural Numbers	\N	active	1	\N	\N	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00
top_ic3_intro	erg	lvl_ic3_gs6_l1	intro-computing	Intro to Computing	Hardware, software, and operating system basics	active	10	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
top_ic3_safety	erg	lvl_ic3_gs6_l1	internet-safety	Internet Safety	Safe digital citizenship and online communication	active	20	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
top_tin_hoc_algo	erg	lvl_tin_hoc_6	algorithm-basics	Algorithm Basics	Step-by-step thinking and simple algorithms	active	30	seed	seed	2026-07-11 03:06:44.128568+00	2026-07-11 03:06:44.128568+00
top_python_foundation	erg	lvl_python_foundation	python-foundation	Python Foundation	Python foundation questions	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
top_python_intermediate	erg	lvl_python_intermediate	python-intermediate	Python Intermediate	Python intermediate questions	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
top_mos_word	erg	lvl_mos_word	mos-word	Microsoft Word	Microsoft Word questions	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
top_mos_excel	erg	lvl_mos_excel	mos-excel	Microsoft Excel	Microsoft Excel questions	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
top_mos_powerpoint	erg	lvl_mos_powerpoint	mos-powerpoint	Microsoft PowerPoint	Microsoft PowerPoint questions	active	10	\N	\N	2026-07-11 18:52:59.037488+00	2026-07-11 18:52:59.037488+00
\.


--
-- Data for Name: education_units; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.education_units (id, created_at, updated_at, name, slug, type, parent_id, logo_url, description, status, owner_user_id, created_by, source, signed_at, activated_at, tenant_id, version) FROM stdin;
sch_truong_cong_dinh	2026-07-11 03:05:18.427554+00	2026-07-11 03:06:46.885533+00	THCS Trương Công Định	thcs-truong-cong-dinh	school	ctr_content_ops	\N	\N	ACTIVE	\N	\N	\N	\N	\N	erg	0
sch_nguyen_thi_minh_khai	2026-07-11 03:05:18.427554+00	2026-07-11 03:06:46.885533+00	TH Nguyễn Thị Minh Khai	th-nguyen-thi-minh-khai	school	ctr_content_ops	\N	\N	ACTIVE	\N	\N	\N	\N	\N	erg	0
ctr_content_ops	2026-07-11 03:06:46.885533+00	2026-07-11 03:06:55.150313+00	Content Operations	content-operations	center	ctr_system	/assets/units/content-operations.svg	Curriculum and learning content operations center	ACTIVE	\N	\N	\N	\N	\N	erg	0
ctr_system	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:17.481929+00	He thong ERG	erg-system	system	\N	/assets/units/erg-system.svg	System unit for all schools	ACTIVE	\N	\N	\N	\N	\N	erg	0
ctr_content	2026-07-11 04:26:17.013797+00	2026-07-11 04:26:17.481929+00	Content Studio	content-studio	center	ctr_system	/assets/units/content-studio.svg	Curriculum and learning content operations unit	ACTIVE	\N	\N	\N	\N	\N	erg	0
sch_binh_an	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:17.481929+00	THCS Binh An	thcs-binh-an	school	ctr_content	/assets/units/thcs-binh-an.svg	THCS Binh An	ACTIVE	\N	\N	\N	\N	\N	erg	0
sch_cat_lai	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:17.481929+00	THCS Cat Lai	thcs-cat-lai	school	ctr_content	/assets/units/thcs-cat-lai.svg	THCS Cat Lai	ACTIVE	\N	\N	\N	\N	\N	erg	0
019f5a8b-b528-7507-b347-52e990580f45	2026-07-13 08:15:33.370475+00	2026-07-13 08:15:33.370475+00	Tiểu học Nguyễn Bỉnh Khiêm	tieu-hoc-nguyen-binh-khiem	PRIMARY	\N	\N	\N	ACTIVE	\N	usr_super_admin	LCMS	\N	\N	erg	0
019f5a8b-ba4d-7351-882a-4ef79911b783	2026-07-13 08:15:34.649923+00	2026-07-13 08:15:34.649923+00	THCS Bình An	thcs-binh-an-11b783	SECONDARY	\N	\N	\N	ACTIVE	\N	usr_super_admin	LCMS	\N	\N	erg	0
019f5a8b-bece-7119-b8eb-77c56f9708e3	2026-07-13 08:15:35.80382+00	2026-07-13 08:15:35.80382+00	THPT Lê Quý Đôn	thpt-le-quy-on	HIGH_SCHOOL	\N	\N	\N	ACTIVE	\N	usr_super_admin	LCMS	\N	\N	erg	0
019f5a8b-c2e5-785c-8cac-0a74b2b724de	2026-07-13 08:15:36.848468+00	2026-07-13 08:15:36.848468+00	Đại học Công nghệ ERG	ai-hoc-cong-nghe-erg	UNIVERSITY	\N	\N	\N	ACTIVE	\N	usr_super_admin	LCMS	\N	\N	erg	0
019f5a8b-c5ea-730e-937c-293628da4628	2026-07-13 08:15:37.621846+00	2026-07-13 08:15:37.621846+00	Trung tâm Đào tạo ERG	trung-tam-ao-tao-erg	TRAINING_CENTER	\N	\N	\N	ACTIVE	\N	usr_super_admin	LCMS	\N	\N	erg	0
\.


--
-- Data for Name: employee_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.employee_profiles (id, tenant_id, user_id, employee_code, department_id, job_title, employment_type, joined_at, terminated_at, experience_years, experience_summary, manager_id, work_location, emergency_contact, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	postgres core schema	SQL	V1__postgres_core_schema.sql	-1555242192	avnadmin	2026-07-11 10:03:23.852055	4390	t
2	2	learning resource schema	SQL	V2__learning_resource_schema.sql	2014327761	avnadmin	2026-07-11 10:03:29.77312	4887	t
3	3	lms runtime schema	SQL	V3__lms_runtime_schema.sql	983796202	avnadmin	2026-07-11 10:03:36.054567	3838	t
4	4	enterprise reference seed	SQL	V4__enterprise_reference_seed.sql	-1110191382	avnadmin	2026-07-11 10:03:41.271431	6018	t
5	5	school crm schema	SQL	V5__school_crm_schema.sql	79412827	avnadmin	2026-07-11 10:03:48.675668	2937	t
6	6	school crm permission seed	SQL	V6__school_crm_permission_seed.sql	1017234541	avnadmin	2026-07-11 10:03:52.983261	2542	t
7	7	enterprise admin account seed	SQL	V7__enterprise_admin_account_seed.sql	-2104017096	avnadmin	2026-07-11 10:03:56.894118	1018	t
8	8	expand string ids for uuid v7	SQL	V8__expand_string_ids_for_uuid_v7.sql	1270904412	avnadmin	2026-07-11 10:03:59.281692	6967	t
9	9	lms hoclieu read model indexes	SQL	V9__lms_hoclieu_read_model_indexes.sql	761559534	avnadmin	2026-07-11 10:04:07.619385	1529	t
10	10	firebase push notifications	SQL	V10__firebase_push_notifications.sql	-1175658354	avnadmin	2026-07-11 10:04:10.51841	3584	t
11	11	relationship integrity seed cleanup	SQL	V11__relationship_integrity_seed_cleanup.sql	-1583750246	avnadmin	2026-07-11 10:04:15.488429	10647	t
12	12	core relationship foreign keys not valid	SQL	V12__core_relationship_foreign_keys_not_valid.sql	-1227763424	avnadmin	2026-07-11 10:04:27.505262	5257	t
13	13	lms relationship foreign keys not valid	SQL	V13__lms_relationship_foreign_keys_not_valid.sql	-1626291567	avnadmin	2026-07-11 10:04:34.146765	3700	t
14	14	hoclieu relationship foreign keys not valid	SQL	V14__hoclieu_relationship_foreign_keys_not_valid.sql	-796710887	avnadmin	2026-07-11 10:04:39.221853	4074	t
15	15	crm notification relationship foreign keys not valid	SQL	V15__crm_notification_relationship_foreign_keys_not_valid.sql	-1102203435	avnadmin	2026-07-11 10:04:44.66354	4492	t
16	16	validate relationship foreign keys	SQL	V16__validate_relationship_foreign_keys.sql	1089450588	avnadmin	2026-07-11 10:04:50.522596	10757	t
17	17	teaching schedule schema	SQL	V17__teaching_schedule_schema.sql	633328887	avnadmin	2026-07-11 10:05:02.649963	5264	t
18	18	validate teaching schedule foreign keys	SQL	V18__validate_teaching_schedule_foreign_keys.sql	-1498780970	avnadmin	2026-07-11 10:05:09.300788	2290	t
19	19	notification retention policy	SQL	V19__notification_retention_policy.sql	-353902114	avnadmin	2026-07-11 10:05:12.957181	1139	t
20	20	teaching schedule conflicts audit	SQL	V20__teaching_schedule_conflicts_audit.sql	834741757	avnadmin	2026-07-11 10:05:15.461874	1137	t
21	21	teaching calendar seed data	SQL	V21__teaching_calendar_seed_data.sql	-2063336843	avnadmin	2026-07-11 10:05:17.960933	4925	t
22	22	teaching calendar enterprise indexes	SQL	V22__teaching_calendar_enterprise_indexes.sql	708371278	avnadmin	2026-07-11 10:05:24.26888	1782	t
23	23	quiz authoring core	SQL	V23__quiz_authoring_core.sql	-938470772	avnadmin	2026-07-11 10:05:27.416993	2929	t
24	24	question bank formalize	SQL	V24__question_bank_formalize.sql	-479770643	avnadmin	2026-07-11 10:05:31.722989	3719	t
25	25	media assets	SQL	V25__media_assets.sql	1286950219	avnadmin	2026-07-11 10:05:36.808308	1269	t
26	26	quiz versions	SQL	V26__quiz_versions.sql	-517742010	avnadmin	2026-07-11 10:05:39.448756	2580	t
27	27	attempts runtime	SQL	V27__attempts_runtime.sql	-749486736	avnadmin	2026-07-11 10:05:43.425203	2950	t
28	28	lms distribution	SQL	V28__lms_distribution.sql	-495051028	avnadmin	2026-07-11 10:05:47.762976	2691	t
29	29	quiz rls enable	SQL	V29__quiz_rls_enable.sql	-435363431	avnadmin	2026-07-11 10:05:51.822347	756	t
30	30	attempt start idempotency	SQL	V30__attempt_start_idempotency.sql	-644073828	avnadmin	2026-07-11 10:05:53.943698	756	t
31	31	student group ui metadata	SQL	V31__student_group_ui_metadata.sql	1878953484	avnadmin	2026-07-11 10:05:56.069657	756	t
32	32	quiz bank hot path indexes	SQL	V32__quiz_bank_hot_path_indexes.sql	61476197	avnadmin	2026-07-11 10:05:58.201544	1147	t
33	33	question type and quiz question relationships	SQL	V33__question_type_and_quiz_question_relationships.sql	-248222293	avnadmin	2026-07-11 10:06:00.725403	8016	t
34	34	quiz runtime relationship integrity hardening	SQL	V34__quiz_runtime_relationship_integrity_hardening.sql	161576507	avnadmin	2026-07-11 10:06:10.127241	13385	t
35	35	auth session security and scale hardening	SQL	V35__auth_session_security_and_scale_hardening.sql	-274258247	avnadmin	2026-07-11 10:06:24.884082	1135	t
36	36	canonical curriculum schema	SQL	V36__canonical_curriculum_schema.sql	916436973	avnadmin	2026-07-11 10:06:27.389822	2308	t
37	37	canonical content schema	SQL	V37__canonical_content_schema.sql	-1291801409	avnadmin	2026-07-11 10:06:31.0754	2248	t
38	38	canonical curriculum backfill bridge	SQL	V38__canonical_curriculum_backfill_bridge.sql	-275505127	avnadmin	2026-07-11 10:06:34.691725	1952	t
39	39	canonical content backfill from legacy resources	SQL	V39__canonical_content_backfill_from_legacy_resources.sql	1530457481	avnadmin	2026-07-11 10:06:38.016476	1401	t
40	40	drop legacy hoclieu tables	SQL	V40__drop_legacy_hoclieu_tables.sql	1803756891	avnadmin	2026-07-11 10:06:40.782951	1521	t
41	41	canonical learning resource seed	SQL	V41__canonical_learning_resource_seed.sql	1270021306	avnadmin	2026-07-11 10:06:43.679551	1394	t
42	42	canonical content cutover cleanup	SQL	V42__canonical_content_cutover_cleanup.sql	-1240569964	avnadmin	2026-07-11 10:06:46.43651	2418	t
43	43	internal account identifier	SQL	V43__internal_account_identifier.sql	1546475944	avnadmin	2026-07-11 10:06:50.22202	878	t
44	44	security event audit	SQL	V44__security_event_audit.sql	117857524	avnadmin	2026-07-11 10:06:52.462529	878	t
45	45	canonical runtime naming cleanup	SQL	V45__canonical_runtime_naming_cleanup.sql	949812568	avnadmin	2026-07-11 10:06:54.702931	1641	t
46	46	canonical content link audit columns	SQL	V46__canonical_content_link_audit_columns.sql	-1682592535	avnadmin	2026-07-11 11:21:36.011429	444	t
47	47	remove social auth legacy	SQL	V47__remove_social_auth_legacy.sql	1591295457	avnadmin	2026-07-11 14:37:24.058011	780	t
48	48	remove public auth pin legacy	SQL	V48__remove_public_auth_pin_legacy.sql	187052876	avnadmin	2026-07-11 14:43:44.945577	592	t
49	49	assessment canonical curriculum cutover	SQL	V49__assessment_canonical_curriculum_cutover.sql	100146641	avnadmin	2026-07-12 01:52:58.567493	2485	t
50	50	school operational core	SQL	V50__school_operational_core.sql	1360987466	avnadmin	2026-07-13 14:52:07.742418	1835	t
51	51	account lifecycle recovery otp	SQL	V51__account_lifecycle_recovery_otp.sql	-1747231480	avnadmin	2026-07-15 08:23:31.249332	818	t
52	52	lms teacher permission seed	SQL	V52__lms_teacher_permission_seed.sql	-2140510243	avnadmin	2026-07-15 08:30:29.638562	1113	t
53	53	user account audit context	SQL	V53__user_account_audit_context.sql	1066623587	avnadmin	2026-07-15 08:30:31.698229	512	t
54	54	revoke server inactive teacher actions	SQL	V54__revoke_server_inactive_teacher_actions.sql	1902940973	avnadmin	2026-07-15 08:30:33.038142	346	t
55	55	backfill existing system admin lifecycle	SQL	V55__backfill_existing_system_admin_lifecycle.sql	-127436059	avnadmin	2026-07-15 08:48:41.749778	400	t
56	56	authorization security boundaries	SQL	V56__authorization_security_boundaries.sql	1545251361	avnadmin	2026-07-15 19:08:01.711711	694	t
57	57	permission catalog and bindings	SQL	V57__permission_catalog_and_bindings.sql	455317568	avnadmin	2026-07-15 19:08:03.445957	1584	t
58	58	session platform slots	SQL	V58__session_platform_slots.sql	258890869	avnadmin	2026-07-15 19:08:05.974984	649	t
59	59	scoped role assignments	SQL	V59__scoped_role_assignments.sql	-1923549266	avnadmin	2026-07-15 19:13:45.594749	1288	t
60	60	employee profiles and security feed	SQL	V60__employee_profiles_and_security_feed.sql	-908042304	avnadmin	2026-07-15 19:13:48.135646	1254	t
61	61	authorization version events	SQL	V61__authorization_version_events.sql	740428840	avnadmin	2026-07-15 19:13:50.49543	444	t
62	62	role mutation metadata	SQL	V62__role_mutation_metadata.sql	1680915799	avnadmin	2026-07-15 19:13:52.045222	535	t
63	63	repair legacy uuid rbac ids	SQL	V63__repair_legacy_uuid_rbac_ids.sql	612050409	avnadmin	2026-07-15 20:39:25.674545	710	t
64	64	login identity lookup indexes	SQL	V64__login_identity_lookup_indexes.sql	-1418096264	avnadmin	2026-07-15 20:39:27.569718	566	t
\.


--
-- Data for Name: lms_assignments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_assignments (id, created_at, updated_at, tenant_id, academic_class_id, quiz_id, subject_id, student_ids, due_at, teacher_note, status, assigned_by, recipient_mode, attempt_limit, max_duration_minutes, start_at, title, idempotency_key, recipient_count) FROM stdin;
asg_ba_math_g6_001	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	cls_ba_2025_6a1	qz_math_g6_base	sub_math	\N	2026-07-11 03:03:41.741114+00	\N	active	usr_teacher_ba	all	\N	\N	\N	\N	\N	\N
asg_cl_math_g6_001	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	cls_cl_2025_6a1	qz_math_g6_base	sub_math	\N	2026-07-11 03:03:41.741114+00	\N	active	usr_teacher_cl	all	\N	\N	\N	\N	\N	\N
\.


--
-- Data for Name: lms_attempts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_attempts (id, tenant_id, assignment_id, quiz_id, student_id, package_id, package_hash, quiz_version, status, answers, events, client, score, max_score, percent, passed, started_at, submitted_at, updated_at, quiz_version_id, class_id, school_id, mode, grading_mode, submitted_count, total_questions, last_active_at, client_runtime, start_idempotency_key) FROM stdin;
\.


--
-- Data for Name: lms_levels; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_levels (id, created_at, updated_at, tenant_id, subject_id, name, code, sort_order, status) FROM stdin;
lvl_math_g6	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	sub_math	Grade 6 Mathematics	grade-6	1	active
lvl_toan_cb	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sub_toan	Toán Cơ Bản	co-ban	1	active
lvl_toan_nc	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sub_toan	Toán Nâng Cao	nang-cao	2	active
lvl_van_cb	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	sub_ngu_van	Văn Cơ Bản	co-ban	1	active
ic3-l1	2026-07-11 04:26:22.421762+00	2026-07-11 04:26:22.421762+00	erg	ic3-gs6	Level 1	ic3-l1	1	ACTIVE
ic3-l2	2026-07-11 04:26:22.566961+00	2026-07-11 04:26:22.566961+00	erg	ic3-gs6	Level 2	ic3-l2	2	ACTIVE
ic3-l3	2026-07-11 04:26:22.707817+00	2026-07-11 04:26:22.707817+00	erg	ic3-gs6	Level 3	ic3-l3	3	ACTIVE
python-foundation	2026-07-11 04:26:22.850494+00	2026-07-11 04:26:22.850494+00	erg	python	Foundation	python-foundation	1	ACTIVE
python-intermediate	2026-07-11 04:26:22.992978+00	2026-07-11 04:26:22.992978+00	erg	python	Intermediate	python-intermediate	2	ACTIVE
mos-word	2026-07-11 04:26:23.13584+00	2026-07-11 04:26:23.13584+00	erg	mos	Word Associate	mos-word	1	ACTIVE
mos-excel	2026-07-11 04:26:23.27599+00	2026-07-11 04:26:23.27599+00	erg	mos	Excel Associate	mos-excel	2	ACTIVE
mos-powerpoint	2026-07-11 04:26:23.413562+00	2026-07-11 04:26:23.413562+00	erg	mos	PowerPoint Associate	mos-powerpoint	3	ACTIVE
\.


--
-- Data for Name: lms_questions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_questions (id, created_at, updated_at, tenant_id, scope, subject_id, level_id, topic_id, type, stem, choices, answer, metadata, status, archived_at, category_id, objective, difficulty, tags, content, mastery_rate, usage_count, schools_using, owner_user_id, schema_version, deleted_at, settings) FROM stdin;
que_math_g6_001	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	\N	sub_math	lvl_math_g6	top_math_g6_num	single_choice	Which number is a natural number?	\N	\N	\N	published	\N	\N	\N	\N	\N	\N	\N	0	\N	\N	1	\N	\N
que_math_g6_002	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	\N	sub_math	lvl_math_g6	top_math_g6_num	single_choice	What is 12 + 8?	\N	\N	\N	published	\N	\N	\N	\N	\N	\N	\N	0	\N	\N	1	\N	\N
qb-ic3-001	2026-07-11 04:26:29.785482+00	2026-07-11 04:26:29.785482+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Thiet bi nao dung de nhap du lieu chu vao may tinh?	[{"id": "a", "label": "Ban phim", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Ban phim"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Thiet bi nao dung de nhap du lieu chu vao may tinh?	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-1"]	{"stem": "Thiet bi nao dung de nhap du lieu chu vao may tinh?", "answer": "Ban phim", "source": "fe-mock-question-bank"}	62	18	3	seed.question.bank	1	\N	\N
qb-ic3-002	2026-07-11 04:26:29.926125+00	2026-07-11 04:26:29.926125+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Khi muon doi ten mot tep, thao tac nao la phu hop nhat?	[{"id": "a", "label": "Rename", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Rename"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Khi muon doi ten mot tep, thao tac nao la phu hop nhat?	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-2"]	{"stem": "Khi muon doi ten mot tep, thao tac nao la phu hop nhat?", "answer": "Rename", "source": "fe-mock-question-bank"}	69	23	4	seed.question.bank	1	\N	\N
qb-ic3-003	2026-07-11 04:26:30.066994+00	2026-07-11 04:26:30.066994+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Dau hieu nao cho thay mot website dang dung ket noi an toan?	[{"id": "a", "label": "Bieu tuong o khoa va HTTPS", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Bieu tuong o khoa va HTTPS"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Dau hieu nao cho thay mot website dang dung ket noi an toan?	stretch	["ic3-gs6", "ic3-l1", "ic3-l1-topic-4"]	{"stem": "Dau hieu nao cho thay mot website dang dung ket noi an toan?", "answer": "Bieu tuong o khoa va HTTPS", "source": "fe-mock-question-bank"}	76	28	5	seed.question.bank	1	\N	\N
qb-ic3-004	2026-07-11 04:26:30.207136+00	2026-07-11 04:26:30.207136+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Trong Word, tinh nang nao giup can deu hai le doan van?	[{"id": "a", "label": "Justify", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Justify"	{"seededFrom": "erg-system mock-question-bank.ts"}	reviewing	\N	top_ic3_safety	Trong Word, tinh nang nao giup can deu hai le doan van?	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-6"]	{"stem": "Trong Word, tinh nang nao giup can deu hai le doan van?", "answer": "Justify", "source": "fe-mock-question-bank"}	83	33	6	seed.question.bank	1	\N	\N
qb-ic3-005	2026-07-11 04:26:30.353928+00	2026-07-11 04:26:30.353928+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Trong Word, muc luc tu dong thuong dua vao thanh phan nao?	[{"id": "a", "label": "Heading styles", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Heading styles"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Trong Word, muc luc tu dong thuong dua vao thanh phan nao?	stretch	["ic3-gs6", "ic3-l2", "ic3-l2-topic-1"]	{"stem": "Trong Word, muc luc tu dong thuong dua vao thanh phan nao?", "answer": "Heading styles", "source": "fe-mock-question-bank"}	90	38	7	seed.question.bank	1	\N	\N
qb-ic3-006	2026-07-11 04:26:30.496742+00	2026-07-11 04:26:30.496742+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Trong Excel, o A1 chua 10 va A2 chua 15. Cong thuc nao tinh tong hai o?	[{"id": "a", "label": "=SUM(A1:A2)", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"=SUM(A1:A2)"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Trong Excel, o A1 chua 10 va A2 chua 15. Cong thuc nao tinh tong hai o?	core	["ic3-gs6", "ic3-l2", "ic3-l2-topic-2"]	{"stem": "Trong Excel, o A1 chua 10 va A2 chua 15. Cong thuc nao tinh tong hai o?", "answer": "=SUM(A1:A2)", "source": "fe-mock-question-bank"}	64	43	8	seed.question.bank	1	\N	\N
qb-ic3-007	2026-07-11 04:26:30.638792+00	2026-07-11 04:26:30.638792+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Ham nao dung de tim gia tri lon nhat trong mot vung du lieu Excel?	[{"id": "a", "label": "MAX", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"MAX"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Ham nao dung de tim gia tri lon nhat trong mot vung du lieu Excel?	core	["ic3-gs6", "ic3-l2", "ic3-l2-topic-3"]	{"stem": "Ham nao dung de tim gia tri lon nhat trong mot vung du lieu Excel?", "answer": "MAX", "source": "fe-mock-question-bank"}	71	48	9	seed.question.bank	1	\N	\N
qb-mos-001	2026-07-11 04:26:31.926655+00	2026-07-11 04:26:31.926655+00	erg	\N	sub_mos	lvl_mos_word	top_mos_word	multiple_choice	Trong Microsoft Word, cong cu nao dung de ap dung nhanh bo dinh dang tieu de?	[{"id": "a", "label": "Styles", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Styles"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_mos_word	Trong Microsoft Word, cong cu nao dung de ap dung nhanh bo dinh dang tieu de?	core	["mos", "mos-word", "mos-word-topic-1"]	{"stem": "Trong Microsoft Word, cong cu nao dung de ap dung nhanh bo dinh dang tieu de?", "answer": "Styles", "source": "fe-mock-question-bank"}	91	26	5	seed.question.bank	1	\N	\N
qb-ic3-008	2026-07-11 04:26:30.778066+00	2026-07-11 04:26:30.778066+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_response	Khi lam viec nhom online, hanh vi nao giup giam nham lan phien ban tai lieu?	[{"id": "a", "label": "Dat ten phien ban ro rang; binh luan thay vi ghi de", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Dat ten phien ban ro rang; binh luan thay vi ghi de"	{"seededFrom": "erg-system mock-question-bank.ts"}	pilot	\N	top_ic3_safety	Khi lam viec nhom online, hanh vi nao giup giam nham lan phien ban tai lieu?	stretch	["ic3-gs6", "ic3-l2", "ic3-l2-topic-5"]	{"stem": "Khi lam viec nhom online, hanh vi nao giup giam nham lan phien ban tai lieu?", "answer": "Dat ten phien ban ro rang; binh luan thay vi ghi de", "source": "fe-mock-question-bank"}	78	53	3	seed.question.bank	1	\N	\N
qb-ic3-009	2026-07-11 04:26:30.918797+00	2026-07-11 04:26:30.918797+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Yeu to nao lam mat khau manh hon?	[{"id": "a", "label": "Dai, co chu hoa, chu thuong, so va ky tu dac biet", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Dai, co chu hoa, chu thuong, so va ky tu dac biet"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Yeu to nao lam mat khau manh hon?	core	["ic3-gs6", "ic3-l3", "ic3-l3-topic-1"]	{"stem": "Yeu to nao lam mat khau manh hon?", "answer": "Dai, co chu hoa, chu thuong, so va ky tu dac biet", "source": "fe-mock-question-bank"}	85	58	4	seed.question.bank	1	\N	\N
qb-ic3-010	2026-07-11 04:26:31.068598+00	2026-07-11 04:26:31.068598+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Bieu do cot phu hop nhat khi can lam gi?	[{"id": "a", "label": "So sanh cac nhom du lieu", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"So sanh cac nhom du lieu"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Bieu do cot phu hop nhat khi can lam gi?	core	["ic3-gs6", "ic3-l3", "ic3-l3-topic-2"]	{"stem": "Bieu do cot phu hop nhat khi can lam gi?", "answer": "So sanh cac nhom du lieu", "source": "fe-mock-question-bank"}	92	63	5	seed.question.bank	1	\N	\N
qb-ic3-011	2026-07-11 04:26:31.216807+00	2026-07-11 04:26:31.216807+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	sequence	Sap xep cac buoc: xac dinh bai toan, viet thuat toan, chay thu, sua loi.	[{"id": "a", "label": "Xac dinh bai toan -> viet thuat toan -> chay thu -> sua loi", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Xac dinh bai toan -> viet thuat toan -> chay thu -> sua loi"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Sap xep cac buoc: xac dinh bai toan, viet thuat toan, chay thu, sua loi.	challenge	["ic3-gs6", "ic3-l3", "ic3-l3-topic-3"]	{"stem": "Sap xep cac buoc: xac dinh bai toan, viet thuat toan, chay thu, sua loi.", "answer": "Xac dinh bai toan -> viet thuat toan -> chay thu -> sua loi", "source": "fe-mock-question-bank"}	66	68	6	seed.question.bank	1	\N	\N
qb-ic3-012	2026-07-11 04:26:31.355567+00	2026-07-11 04:26:31.355567+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_choice	Mot bai kiem tra mo phong IC3 nen uu tien dieu gi?	[{"id": "a", "label": "Do dung nang luc theo tung muc tieu ky nang", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Do dung nang luc theo tung muc tieu ky nang"	{"seededFrom": "erg-system mock-question-bank.ts"}	reviewing	\N	top_ic3_safety	Mot bai kiem tra mo phong IC3 nen uu tien dieu gi?	challenge	["ic3-gs6", "ic3-l3", "ic3-l3-topic-6"]	{"stem": "Mot bai kiem tra mo phong IC3 nen uu tien dieu gi?", "answer": "Do dung nang luc theo tung muc tieu ky nang", "source": "fe-mock-question-bank"}	73	73	7	seed.question.bank	1	\N	\N
qb-python-001	2026-07-11 04:26:31.495255+00	2026-07-11 04:26:31.495255+00	erg	\N	sub_python	lvl_python_foundation	top_python_foundation	multiple_choice	Kieu du lieu nao phu hop de luu gia tri dung/sai trong Python?	[{"id": "a", "label": "bool", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"bool"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_python_foundation	Kieu du lieu nao phu hop de luu gia tri dung/sai trong Python?	core	["python", "python-foundation", "python-foundation-topic-1"]	{"stem": "Kieu du lieu nao phu hop de luu gia tri dung/sai trong Python?", "answer": "bool", "source": "fe-mock-question-bank"}	82	22	5	seed.question.bank	1	\N	\N
qb-python-002	2026-07-11 04:26:31.638+00	2026-07-11 04:26:31.638+00	erg	\N	sub_python	lvl_python_foundation	top_python_foundation	multiple_choice	Cau lenh nao dung de kiem tra dieu kien trong Python?	[{"id": "a", "label": "if", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"if"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_python_foundation	Cau lenh nao dung de kiem tra dieu kien trong Python?	core	["python", "python-foundation", "python-foundation-topic-2"]	{"stem": "Cau lenh nao dung de kiem tra dieu kien trong Python?", "answer": "if", "source": "fe-mock-question-bank"}	76	28	5	seed.question.bank	1	\N	\N
qb-python-003	2026-07-11 04:26:31.784335+00	2026-07-11 04:26:31.784335+00	erg	\N	sub_python	lvl_python_intermediate	top_python_intermediate	multiple_choice	Nhung cau truc nao co the luu nhieu gia tri trong Python?	[{"id": "a", "label": "list; dictionary", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"list; dictionary"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_python_intermediate	Nhung cau truc nao co the luu nhieu gia tri trong Python?	stretch	["python", "python-intermediate", "python-intermediate-topic-1"]	{"stem": "Nhung cau truc nao co the luu nhieu gia tri trong Python?", "answer": "list; dictionary", "source": "fe-mock-question-bank"}	88	23	5	seed.question.bank	1	\N	\N
qb-mos-002	2026-07-11 04:26:32.069454+00	2026-07-11 04:26:32.069454+00	erg	\N	sub_mos	lvl_mos_excel	top_mos_excel	multiple_choice	Trong Excel, cong thuc nao tinh tong vung A1 den A5?	[{"id": "a", "label": "=SUM(A1:A5)", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"=SUM(A1:A5)"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_mos_excel	Trong Excel, cong thuc nao tinh tong vung A1 den A5?	core	["mos", "mos-excel", "mos-excel-topic-1"]	{"stem": "Trong Excel, cong thuc nao tinh tong vung A1 den A5?", "answer": "=SUM(A1:A5)", "source": "fe-mock-question-bank"}	84	31	6	seed.question.bank	1	\N	\N
qb-mos-003	2026-07-11 04:26:32.208567+00	2026-07-11 04:26:32.208567+00	erg	\N	sub_mos	lvl_mos_powerpoint	top_mos_powerpoint	multiple_choice	Trong PowerPoint, Slide Master giup lam gi?	[{"id": "a", "label": "Quan ly bo cuc va dinh dang chung", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Quan ly bo cuc va dinh dang chung"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_mos_powerpoint	Trong PowerPoint, Slide Master giup lam gi?	stretch	["mos", "mos-powerpoint", "mos-powerpoint-topic-1"]	{"stem": "Trong PowerPoint, Slide Master giup lam gi?", "answer": "Quan ly bo cuc va dinh dang chung", "source": "fe-mock-question-bank"}	79	19	4	seed.question.bank	1	\N	\N
qb-sample-q01-single-choice	2026-07-11 04:26:32.349419+00	2026-07-11 04:26:32.349419+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	single_choice	Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?	[{"id": "a", "label": "Tập tin văn bản thuần dài 400 trang.", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Tập tin văn bản thuần dài 400 trang."	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?", "answer": "Tập tin văn bản thuần dài 400 trang.", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q02-multiple-response	2026-07-11 04:26:32.4963+00	2026-07-11 04:26:32.4963+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	multiple_response	Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn.	[{"id": "a", "label": "Đặt tên file theo cú pháp giáo viên yêu cầu; Nén ảnh hoặc video nếu dung lượng quá lớn; Kiểm tra lại file trước khi nộp.", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Đặt tên file theo cú pháp giáo viên yêu cầu; Nén ảnh hoặc video nếu dung lượng quá lớn; Kiểm tra lại file trước khi nộp."	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn.", "answer": "Đặt tên file theo cú pháp giáo viên yêu cầu; Nén ảnh hoặc video nếu dung lượng quá lớn; Kiểm tra lại file trước khi nộp.", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q03-true-false	2026-07-11 04:26:32.640783+00	2026-07-11 04:26:32.640783+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	true_false	Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn.	[{"id": "a", "label": "Đúng", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Đúng"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn.", "answer": "Đúng", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q04-short-answer	2026-07-11 04:26:32.784489+00	2026-07-11 04:26:32.784489+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	short_answer	Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?	[{"id": "a", "label": "PDF", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"PDF"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?", "answer": "PDF", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q05-numeric	2026-07-11 04:26:32.925458+00	2026-07-11 04:26:32.925458+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	numeric	Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?	[{"id": "a", "label": "12", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"12"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?", "answer": "12", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q06-sequence	2026-07-11 04:26:33.066608+00	2026-07-11 04:26:33.066608+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	sequence	Sắp xếp quy trình học tập online theo thứ tự hợp lý.	[{"id": "a", "label": "Đọc yêu cầu bài học -> Xem tài liệu hoặc video hướng dẫn -> Làm bài luyện tập -> Xem phản hồi và sửa lỗi", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Đọc yêu cầu bài học -> Xem tài liệu hoặc video hướng dẫn -> Làm bài luyện tập -> Xem phản hồi và sửa lỗi"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Sắp xếp quy trình học tập online theo thứ tự hợp lý.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Sắp xếp quy trình học tập online theo thứ tự hợp lý.", "answer": "Đọc yêu cầu bài học -> Xem tài liệu hoặc video hướng dẫn -> Làm bài luyện tập -> Xem phản hồi và sửa lỗi", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q07-matching	2026-07-11 04:26:33.210142+00	2026-07-11 04:26:33.210142+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	matching	Ghép các bước trong chu trình quản lý dự án với mô tả của nó.	[{"id": "a", "label": "Monitoring, Initiation, Execution, Close", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Monitoring, Initiation, Execution, Close"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Ghép các bước trong chu trình quản lý dự án với mô tả của nó.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Ghép các bước trong chu trình quản lý dự án với mô tả của nó.", "answer": "Monitoring, Initiation, Execution, Close", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q08-matching-image	2026-07-11 04:26:33.355488+00	2026-07-11 04:26:33.355488+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	matching	Ghép tên từng đầu kết nối với đúng hình minh họa.	[{"id": "a", "label": "USB-C, USB-A, HDMI, Lightning", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"USB-C, USB-A, HDMI, Lightning"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Ghép tên từng đầu kết nối với đúng hình minh họa.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Ghép tên từng đầu kết nối với đúng hình minh họa.", "answer": "USB-C, USB-A, HDMI, Lightning", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q09-fill-blank	2026-07-11 04:26:33.506859+00	2026-07-11 04:26:33.506859+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	fill_blank	Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản.	[{"id": "a", "label": "mật khẩu; xác thực", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"mật khẩu; xác thực"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản.", "answer": "mật khẩu; xác thực", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q10-select-from-lists	2026-07-11 04:26:33.648157+00	2026-07-11 04:26:33.648157+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	select_from_lists	Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần.	[{"id": "a", "label": "Có/Không theo tình huống", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"Có/Không theo tình huống"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần.", "answer": "Có/Không theo tình huống", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q11-drag-words	2026-07-11 04:26:33.787901+00	2026-07-11 04:26:33.787901+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	drag_words	Bấm từ trong ngân hàng để đưa vào đúng chỗ trống.	[{"id": "a", "label": "ngân hàng từ", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"ngân hàng từ"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Bấm từ trong ngân hàng để đưa vào đúng chỗ trống.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Bấm từ trong ngân hàng để đưa vào đúng chỗ trống.", "answer": "ngân hàng từ", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q12-hotspot	2026-07-11 04:26:33.927333+00	2026-07-11 04:26:33.927333+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	hotspot	Bấm vào vùng thông tin phiên bản trong màn hình minh họa.	[{"id": "a", "label": "vùng phiên bản", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"vùng phiên bản"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Bấm vào vùng thông tin phiên bản trong màn hình minh họa.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Bấm vào vùng thông tin phiên bản trong màn hình minh họa.", "answer": "vùng phiên bản", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q13-drag-drop	2026-07-11 04:26:34.073072+00	2026-07-11 04:26:34.073072+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	drag_drop	Phân loại các món vào nhóm phù hợp.	[{"id": "a", "label": "thực phẩm tốt / đồ ăn vặt", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"thực phẩm tốt / đồ ăn vặt"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Phân loại các món vào nhóm phù hợp.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Phân loại các món vào nhóm phù hợp.", "answer": "thực phẩm tốt / đồ ăn vặt", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q14-likert	2026-07-11 04:26:34.218658+00	2026-07-11 04:26:34.218658+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	likert_scale	Đánh giá mức độ đồng ý của em sau bài học.	[{"id": "a", "label": "khảo sát Likert", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"khảo sát Likert"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Đánh giá mức độ đồng ý của em sau bài học.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Đánh giá mức độ đồng ý của em sau bài học.", "answer": "khảo sát Likert", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
qb-sample-q15-essay	2026-07-11 04:26:34.3705+00	2026-07-11 04:26:34.3705+00	erg	\N	sub_ic3	lvl_ic3_gs6_l1	top_ic3_safety	essay	Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.	[{"id": "a", "label": "tự luận", "correct": true}, {"id": "b", "label": "Phuong an gay nhieu 1", "correct": false}, {"id": "c", "label": "Phuong an gay nhieu 2", "correct": false}, {"id": "d", "label": "Phuong an gay nhieu 3", "correct": false}]	"tự luận"	{"seededFrom": "erg-system mock-question-bank.ts"}	ready	\N	top_ic3_safety	Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.	core	["ic3-gs6", "ic3-l1", "ic3-l1-topic-7"]	{"stem": "Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.", "answer": "tự luận", "source": "fe-mock-question-bank"}	75	0	1	seed.question.bank	1	\N	\N
\.


--
-- Data for Name: lms_quiz_questions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_quiz_questions (id, created_at, updated_at, tenant_id, quiz_id, question_id, order_index, source, points, settings, metadata) FROM stdin;
019f6609-46c9-73bb-9609-fa0368fedfc3	2026-07-15 13:48:31.816454+00	2026-07-15 13:48:31.816454+00	erg	quiz-ic3-l1-foundation-train	qb-ic3-001	0	manual	\N	{}	{}
019f6609-46d1-7ffa-a6d4-733c7691e907	2026-07-15 13:48:31.825539+00	2026-07-15 13:48:31.825539+00	erg	quiz-ic3-l1-foundation-train	qb-ic3-002	1	manual	\N	{}	{}
019f6609-46d1-76f5-b063-efbe4044375b	2026-07-15 13:48:31.825539+00	2026-07-15 13:48:31.825539+00	erg	quiz-ic3-l1-foundation-train	qb-ic3-003	2	manual	\N	{}	{}
019f6609-46d2-7ff0-be7e-b2cdb8ec3491	2026-07-15 13:48:31.826542+00	2026-07-15 13:48:31.826542+00	erg	quiz-ic3-l1-foundation-train	qb-ic3-004	3	manual	\N	{}	{}
019f6609-4846-714e-82b5-927b5eddb15a	2026-07-15 13:48:32.198312+00	2026-07-15 13:48:32.198312+00	erg	quiz-ic3-l2-excel-test	qb-ic3-006	0	manual	\N	{}	{}
019f6609-4847-748c-a294-c04105c0727c	2026-07-15 13:48:32.199824+00	2026-07-15 13:48:32.199824+00	erg	quiz-ic3-l2-excel-test	qb-ic3-007	1	manual	\N	{}	{}
019f6609-4a17-75d1-871a-e24f4b991417	2026-07-15 13:48:32.66315+00	2026-07-15 13:48:32.66315+00	erg	quiz-ic3-l3-security-train	qb-ic3-009	0	manual	\N	{}	{}
019f6609-4a19-7547-9fa3-18025100f20f	2026-07-15 13:48:32.665155+00	2026-07-15 13:48:32.665155+00	erg	quiz-ic3-l3-security-train	qb-ic3-010	1	manual	\N	{}	{}
019f6609-4a19-79a9-a67c-e236d28f86a7	2026-07-15 13:48:32.665155+00	2026-07-15 13:48:32.665155+00	erg	quiz-ic3-l3-security-train	qb-ic3-011	2	manual	\N	{}	{}
019f6609-4c3e-7cd2-a20b-b49166faf67b	2026-07-15 13:48:33.21473+00	2026-07-15 13:48:33.21473+00	erg	quiz-ic3-l3-mock-test	qb-ic3-009	0	manual	\N	{}	{}
019f6609-4c40-7e6c-ace2-15f381776072	2026-07-15 13:48:33.216245+00	2026-07-15 13:48:33.216245+00	erg	quiz-ic3-l3-mock-test	qb-ic3-010	1	manual	\N	{}	{}
019f6609-4c41-70d6-81c9-2008b0f02efb	2026-07-15 13:48:33.217264+00	2026-07-15 13:48:33.217264+00	erg	quiz-ic3-l3-mock-test	qb-ic3-011	2	manual	\N	{}	{}
019f6609-4c42-7acb-8e93-fbe4bf487e73	2026-07-15 13:48:33.218265+00	2026-07-15 13:48:33.218265+00	erg	quiz-ic3-l3-mock-test	qb-ic3-012	3	manual	\N	{}	{}
019f6609-4db0-76fc-a837-eef13e287cc7	2026-07-15 13:48:33.58494+00	2026-07-15 13:48:33.58494+00	erg	quiz-python-foundation-train	qb-python-001	0	manual	\N	{}	{}
019f6609-4db1-7b96-be57-909a72baf121	2026-07-15 13:48:33.585448+00	2026-07-15 13:48:33.585448+00	erg	quiz-python-foundation-train	qb-python-002	1	manual	\N	{}	{}
019f6609-4ec3-714f-b9a8-bd9c177a3289	2026-07-15 13:48:33.859464+00	2026-07-15 13:48:33.859464+00	erg	quiz-python-intermediate-test	qb-python-003	0	manual	\N	{}	{}
019f6609-508a-709f-b7a6-1539c5688721	2026-07-15 13:48:34.314699+00	2026-07-15 13:48:34.314699+00	erg	quiz-mos-office-readiness	qb-mos-001	0	manual	\N	{}	{}
019f6609-508b-7426-8f35-8b11c841089c	2026-07-15 13:48:34.315207+00	2026-07-15 13:48:34.315207+00	erg	quiz-mos-office-readiness	qb-mos-002	1	manual	\N	{}	{}
019f6609-508b-7193-8463-fd6c9c083b88	2026-07-15 13:48:34.315207+00	2026-07-15 13:48:34.315207+00	erg	quiz-mos-office-readiness	qb-mos-003	2	manual	\N	{}	{}
019f6609-569e-70f0-8792-129d370e51c2	2026-07-15 13:48:35.870824+00	2026-07-15 13:48:35.870824+00	erg	quiz-sample-editor-question-types	qb-sample-q01-single-choice	0	manual	\N	{}	{}
019f6609-569f-73c9-9931-205451ed4da6	2026-07-15 13:48:35.871822+00	2026-07-15 13:48:35.871822+00	erg	quiz-sample-editor-question-types	qb-sample-q02-multiple-response	1	manual	\N	{}	{}
019f6609-569f-7e9b-ac14-6638bfd8f206	2026-07-15 13:48:35.871822+00	2026-07-15 13:48:35.871822+00	erg	quiz-sample-editor-question-types	qb-sample-q03-true-false	2	manual	\N	{}	{}
019f6609-56a0-7cda-bc9d-dbd472bcc1c2	2026-07-15 13:48:35.872335+00	2026-07-15 13:48:35.872335+00	erg	quiz-sample-editor-question-types	qb-sample-q04-short-answer	3	manual	\N	{}	{}
019f6609-56a1-726e-ae70-cd9142c8d69b	2026-07-15 13:48:35.873341+00	2026-07-15 13:48:35.873341+00	erg	quiz-sample-editor-question-types	qb-sample-q05-numeric	4	manual	\N	{}	{}
019f6609-56a2-7497-8120-3aeb42f00412	2026-07-15 13:48:35.874347+00	2026-07-15 13:48:35.874347+00	erg	quiz-sample-editor-question-types	qb-sample-q06-sequence	5	manual	\N	{}	{}
019f6609-56a2-7170-b25e-d2c6bf4bf216	2026-07-15 13:48:35.874347+00	2026-07-15 13:48:35.874347+00	erg	quiz-sample-editor-question-types	qb-sample-q07-matching	6	manual	\N	{}	{}
019f6609-56a2-779a-ba9e-819c9fb942fe	2026-07-15 13:48:35.874347+00	2026-07-15 13:48:35.874347+00	erg	quiz-sample-editor-question-types	qb-sample-q08-matching-image	7	manual	\N	{}	{}
019f6609-56a3-717b-9997-91e3005b639c	2026-07-15 13:48:35.875347+00	2026-07-15 13:48:35.875347+00	erg	quiz-sample-editor-question-types	qb-sample-q09-fill-blank	8	manual	\N	{}	{}
019f6609-56a3-7e94-8bae-f7b11933e310	2026-07-15 13:48:35.875347+00	2026-07-15 13:48:35.875347+00	erg	quiz-sample-editor-question-types	qb-sample-q10-select-from-lists	9	manual	\N	{}	{}
019f6609-56a4-7311-b00c-8bba6dc41f49	2026-07-15 13:48:35.876862+00	2026-07-15 13:48:35.876862+00	erg	quiz-sample-editor-question-types	qb-sample-q11-drag-words	10	manual	\N	{}	{}
019f6609-56a4-79b0-8226-346aaaabf0a0	2026-07-15 13:48:35.876862+00	2026-07-15 13:48:35.876862+00	erg	quiz-sample-editor-question-types	qb-sample-q12-hotspot	11	manual	\N	{}	{}
019f6609-56a5-716c-88eb-66eb28dc75a1	2026-07-15 13:48:35.877874+00	2026-07-15 13:48:35.877874+00	erg	quiz-sample-editor-question-types	qb-sample-q13-drag-drop	12	manual	\N	{}	{}
019f6609-56a5-71bb-86d9-6fdf74ff3bca	2026-07-15 13:48:35.877874+00	2026-07-15 13:48:35.877874+00	erg	quiz-sample-editor-question-types	qb-sample-q14-likert	13	manual	\N	{}	{}
019f6609-56a5-7495-9ae4-0f74f06b6951	2026-07-15 13:48:35.877874+00	2026-07-15 13:48:35.877874+00	erg	quiz-sample-editor-question-types	qb-sample-q15-essay	14	manual	\N	{}	{}
\.


--
-- Data for Name: lms_quizzes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_quizzes (id, created_at, updated_at, tenant_id, scope, title, kind, subject_id, level_id, topic_ids, question_ids, slides, settings, result, theme, theme_id, status, version, package_hash, published_at, portal, subtitle, category_id, current_version_id, latest_draft_id, owner_user_id, content_scope, schema_version, idempotency_key, deleted_at) FROM stdin;
qz_math_g6_base	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	\N	Grade 6 Mathematics Baseline	exam	sub_math	lvl_math_g6	\N	\N	\N	\N	\N	\N	\N	published	1	pkg_math_g6_v1	2026-07-11 03:03:41.741114+00	lcms	\N	\N	\N	\N	\N	\N	1	\N	\N
quiz-ic3-l1-foundation-train	2026-07-11 04:26:34.523923+00	2026-07-11 04:26:34.523923+00	erg	\N	IC3 GS6 L1 - Train nen tang may tinh	train	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-ic3-001", "qb-ic3-002", "qb-ic3-003", "qb-ic3-004"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	ready	0	\N	\N	lcms	\N	top_ic3_safety	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-ic3-l2-excel-test	2026-07-11 04:26:42.605721+00	2026-07-11 04:26:42.605721+00	erg	\N	IC3 GS6 L2 - Test Excel nhap mon	test	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-ic3-006", "qb-ic3-007"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	ready	0	\N	\N	lcms	\N	top_ic3_safety	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-ic3-l3-security-train	2026-07-11 04:26:42.976579+00	2026-07-11 04:26:42.976579+00	erg	\N	IC3 GS6 L3 - Train bao mat tai khoan	train	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-ic3-009", "qb-ic3-010", "qb-ic3-011"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	draft	0	\N	\N	lcms	\N	top_ic3_safety	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-ic3-l3-mock-test	2026-07-11 04:26:43.429168+00	2026-07-11 04:26:43.429168+00	erg	\N	IC3 GS6 L3 - Test mo phong cuoi level	test	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-ic3-009", "qb-ic3-010", "qb-ic3-011", "qb-ic3-012"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	reviewing	0	\N	\N	lcms	\N	top_ic3_safety	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-python-foundation-train	2026-07-11 04:26:43.924819+00	2026-07-11 04:26:43.924819+00	erg	\N	Python Foundation - Train bien va dieu kien	train	sub_python	lvl_python_foundation	\N	["qb-python-001", "qb-python-002"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	ready	0	\N	\N	lcms	\N	top_python_foundation	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-python-intermediate-test	2026-07-11 04:26:44.285527+00	2026-07-11 04:26:44.285527+00	erg	\N	Python Intermediate - Test cau truc du lieu	test	sub_python	lvl_python_intermediate	\N	["qb-python-003"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	ready	0	\N	\N	lcms	\N	top_python_intermediate	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-mos-office-readiness	2026-07-11 04:26:44.568172+00	2026-07-11 04:26:44.568172+00	erg	\N	MOS Office - Test san sang chung chi	test	sub_mos	lvl_mos_excel	\N	["qb-mos-001", "qb-mos-002", "qb-mos-003"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	ready	0	\N	\N	lcms	\N	top_mos_word	\N	\N	seed.question.bank	\N	1	\N	\N
quiz-sample-editor-question-types	2026-07-11 04:26:44.992917+00	2026-07-11 04:26:50.392694+00	erg	\N	Đây là bài kiểm tra mẫu	train	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-sample-q01-single-choice", "qb-sample-q02-multiple-response", "qb-sample-q03-true-false", "qb-sample-q04-short-answer", "qb-sample-q05-numeric", "qb-sample-q06-sequence", "qb-sample-q07-matching", "qb-sample-q08-matching-image", "qb-sample-q09-fill-blank", "qb-sample-q10-select-from-lists", "qb-sample-q11-drag-words", "qb-sample-q12-hotspot", "qb-sample-q13-drag-drop", "qb-sample-q14-likert", "qb-sample-q15-essay"]	\N	{"sourceMode": "fe-mock-question-bank"}	\N	\N	\N	ready	0	\N	\N	lcms	\N	top_ic3_safety	\N	019f4f6d-882c-7bc2-b8af-0423c7a25700	seed.question.bank	\N	1	\N	\N
erg-training-demo	2026-07-11 07:24:47.948521+00	2026-07-11 07:24:49.779673+00	erg	\N	ERG on tap ky nang so (Training)	train	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-sample-q01-single-choice", "qb-sample-q02-multiple-response", "qb-sample-q03-true-false", "qb-sample-q04-short-answer", "qb-sample-q05-numeric", "qb-sample-q06-sequence", "qb-sample-q07-matching", "qb-sample-q09-fill-blank", "qb-sample-q10-select-from-lists", "qb-sample-q11-drag-words", "qb-sample-q12-hotspot", "qb-sample-q13-drag-drop", "qb-sample-q14-likert", "qb-sample-q15-essay"]	\N	{"mode": "training", "passPercent": 80, "shuffleChoices": false, "shuffleQuestions": false, "timeLimitMinutes": 30, "revealFeedbackPerStep": true}	{"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}	{"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}	erg-default	published	1	sha256:09a9204c6ebb711f25d954a6353d2b6ba17795b543ef0824f1cdd650f5ce432a	2026-07-11 07:24:49.630209+00	lcms	Che do luyen tap tung cau	top_ic3_safety	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-85cf-78a4-99e4-1470fb3b00b2	seed.quiz.demo	\N	1	seed:erg-training-demo	\N
erg-testing-demo	2026-07-11 07:24:52.246485+00	2026-07-11 07:24:54.124717+00	erg	\N	ERG kiem tra ky nang so (Testing)	test	sub_ic3	lvl_ic3_gs6_l1	\N	["qb-sample-q01-single-choice", "qb-sample-q02-multiple-response", "qb-sample-q03-true-false", "qb-sample-q04-short-answer", "qb-sample-q05-numeric", "qb-sample-q06-sequence", "qb-sample-q07-matching", "qb-sample-q09-fill-blank", "qb-sample-q10-select-from-lists", "qb-sample-q11-drag-words", "qb-sample-q12-hotspot", "qb-sample-q13-drag-drop", "qb-sample-q14-likert", "qb-sample-q15-essay"]	\N	{"mode": "testing", "passPercent": 80, "shuffleChoices": true, "shuffleQuestions": true, "timeLimitMinutes": 20, "revealFeedbackPerStep": false}	{"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}	{"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}	erg-default	published	1	sha256:5ed58eb02b0da4ed6471be4863583b651653989f65b5109998c4d3ee15349605	2026-07-11 07:24:53.957643+00	lcms	Che do kiem tra day du	top_ic3_safety	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9697-7180-ad82-54a36196b1ad	seed.quiz.demo	\N	1	seed:erg-testing-demo	\N
\.


--
-- Data for Name: lms_students; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_students (id, created_at, updated_at, tenant_id, school_id, academic_class_id, student_code, full_name, username, auth_user_id, email, gender, birthday, phone, address, parent_name, parent_phone, parent_email, parent_relationship, enrollment_date, note, status, metrics) FROM stdin;
stu_ba_0001	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	sch_binh_an	cls_ba_2025_6a1	BA-2025-0001	Nguyen An	ba.2025.0001	usr_stu_ba_0001	ba.2025.0001@example.test	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	active	\N
stu_cl_0001	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	sch_cat_lai	cls_cl_2025_6a1	CL-2025-0001	Tran Binh	cl.2025.0001	usr_stu_cl_0001	cl.2025.0001@example.test	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	active	\N
stu_demo_icdh1x_5	2026-07-13 09:07:14.25311+00	2026-07-13 09:19:25.343145+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh1x_5	\N	stu_demo_icdh1x_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:19:25.253481+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502365.253480900, "completedAssignments": 13}
stu_demo_icdh1x_6	2026-07-13 09:07:14.739729+00	2026-07-13 09:19:25.781771+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh1x_6	\N	stu_demo_icdh1x_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:19:25.691647+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783415965.691646500, "completedAssignments": 14}
stu_demo_icdh1x_7	2026-07-13 09:07:15.216939+00	2026-07-13 09:19:26.216918+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh1x_7	\N	stu_demo_icdh1x_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:19:26.130261+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329566.130261200, "completedAssignments": 15}
stu_demo_icdh1x_8	2026-07-13 09:07:15.71245+00	2026-07-13 09:19:26.654595+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh1x_8	\N	stu_demo_icdh1x_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:19:26.56562+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934366.565620000, "completedAssignments": 16}
stu_demo_icdh1x_9	2026-07-13 09:07:16.204775+00	2026-07-13 09:19:27.091006+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh1x_9	\N	stu_demo_icdh1x_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:19:27.003281+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783847967.003281400, "completedAssignments": 17}
stu_demo_icdh1x_10	2026-07-13 09:07:16.690478+00	2026-07-13 09:19:27.528454+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh1x_10	\N	stu_demo_icdh1x_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:19:27.439454+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761567.439453600, "completedAssignments": 18}
stu_demo_icdh1x_11	2026-07-13 09:07:17.161218+00	2026-07-13 09:19:27.963128+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh1x_11	\N	stu_demo_icdh1x_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:19:27.875891+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675167.875891400, "completedAssignments": 19}
stu_demo_icdh1x_12	2026-07-13 09:07:17.64504+00	2026-07-13 09:19:28.397399+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh1x_12	\N	stu_demo_icdh1x_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:19:28.309619+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588768.309618600, "completedAssignments": 20}
stu_demo_icdh1x_13	2026-07-13 09:07:18.119284+00	2026-07-13 09:19:28.833087+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh1x_13	\N	stu_demo_icdh1x_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:19:28.744131+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502368.744130600, "completedAssignments": 21}
stu_demo_icdh1x_14	2026-07-13 09:07:18.590323+00	2026-07-13 09:19:29.274817+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh1x_14	\N	stu_demo_icdh1x_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:19:29.182403+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783415969.182403000, "completedAssignments": 22}
stu_demo_icdh1x_15	2026-07-13 09:07:19.08416+00	2026-07-13 09:19:29.709623+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh1x_15	\N	stu_demo_icdh1x_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:19:29.621783+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329569.621783200, "completedAssignments": 23}
stu_demo_icdh1x_16	2026-07-13 09:07:19.56328+00	2026-07-13 09:19:30.142613+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh1x_16	\N	stu_demo_icdh1x_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:19:30.055023+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934370.055022800, "completedAssignments": 8}
stu_demo_icdh1x_17	2026-07-13 09:07:20.052647+00	2026-07-13 09:19:30.588657+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh1x_17	\N	stu_demo_icdh1x_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:19:30.488472+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783847970.488471900, "completedAssignments": 9}
stu_demo_icdh1x_18	2026-07-13 09:07:20.534665+00	2026-07-13 09:19:31.024025+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh1x_18	\N	stu_demo_icdh1x_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:19:30.93694+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761570.936940400, "completedAssignments": 10}
stu_demo_icdh1x_2	2026-07-13 09:07:12.788341+00	2026-07-13 09:19:24.00308+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh1x_2	\N	stu_demo_icdh1x_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:19:23.913464+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761563.913464100, "completedAssignments": 10}
stu_demo_icdh1x_3	2026-07-13 09:07:13.289447+00	2026-07-13 09:19:24.446839+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh1x_3	\N	stu_demo_icdh1x_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:19:24.356703+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675164.356702700, "completedAssignments": 11}
stu_demo_icdh1x_4	2026-07-13 09:07:13.769875+00	2026-07-13 09:19:24.899022+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh1x_4	\N	stu_demo_icdh1x_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:19:24.809896+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588764.809895600, "completedAssignments": 12}
stu_demo_icdh1x_23	2026-07-13 09:07:22.990743+00	2026-07-13 09:19:33.191276+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh1x_23	\N	stu_demo_icdh1x_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:19:33.104529+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329573.104529100, "completedAssignments": 15}
stu_demo_icdh1x_24	2026-07-13 09:07:23.469639+00	2026-07-13 09:19:33.626879+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh1x_24	\N	stu_demo_icdh1x_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:19:33.53924+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934373.539240300, "completedAssignments": 16}
stu_demo_icdh1y_1	2026-07-13 09:07:24.455737+00	2026-07-13 09:19:34.409268+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh1y_1	\N	stu_demo_icdh1y_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:19:34.319985+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783847974.319985200, "completedAssignments": 9}
stu_demo_icdh1y_2	2026-07-13 09:07:24.931323+00	2026-07-13 09:19:34.847654+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh1y_2	\N	stu_demo_icdh1y_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:19:34.757411+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761574.757410900, "completedAssignments": 10}
stu_demo_icdh1y_3	2026-07-13 09:07:25.448965+00	2026-07-13 09:19:35.282053+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh1y_3	\N	stu_demo_icdh1y_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:19:35.19473+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675175.194729700, "completedAssignments": 11}
stu_demo_icdh1y_4	2026-07-13 09:07:25.919958+00	2026-07-13 09:19:35.73631+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh1y_4	\N	stu_demo_icdh1y_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:19:35.648323+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588775.648323000, "completedAssignments": 12}
stu_demo_icdh1y_5	2026-07-13 09:07:26.398332+00	2026-07-13 09:19:36.169877+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh1y_5	\N	stu_demo_icdh1y_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:19:36.080819+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502376.080819100, "completedAssignments": 13}
stu_demo_icdh1y_6	2026-07-13 09:07:26.88013+00	2026-07-13 09:19:36.601175+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh1y_6	\N	stu_demo_icdh1y_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:19:36.51375+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783415976.513750100, "completedAssignments": 14}
stu_demo_icdh1y_7	2026-07-13 09:07:27.355891+00	2026-07-13 09:19:37.035287+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh1y_7	\N	stu_demo_icdh1y_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:19:36.94666+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329576.946660200, "completedAssignments": 15}
stu_demo_icdh1y_8	2026-07-13 09:07:27.831826+00	2026-07-13 09:19:37.467903+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh1y_8	\N	stu_demo_icdh1y_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:19:37.379714+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934377.379713600, "completedAssignments": 16}
stu_demo_icdh1y_9	2026-07-13 09:07:28.314084+00	2026-07-13 09:19:37.903372+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh1y_9	\N	stu_demo_icdh1y_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:19:37.815897+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783847977.815897400, "completedAssignments": 17}
stu_demo_icdh1y_10	2026-07-13 09:07:28.786436+00	2026-07-13 09:19:38.335143+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh1y_10	\N	stu_demo_icdh1y_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:19:38.248446+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761578.248446400, "completedAssignments": 18}
stu_demo_icdh1y_11	2026-07-13 09:07:29.26471+00	2026-07-13 09:19:38.766426+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh1y_11	\N	stu_demo_icdh1y_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:19:38.679806+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675178.679805500, "completedAssignments": 19}
stu_demo_icdh1y_12	2026-07-13 09:07:29.749829+00	2026-07-13 09:19:39.20174+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh1y_12	\N	stu_demo_icdh1y_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:19:39.114486+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588779.114486000, "completedAssignments": 20}
stu_demo_icdh1y_13	2026-07-13 09:07:30.240597+00	2026-07-13 09:19:39.636884+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh1y_13	\N	stu_demo_icdh1y_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:19:39.550362+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502379.550362400, "completedAssignments": 21}
stu_demo_icdh1x_20	2026-07-13 09:07:21.512252+00	2026-07-13 09:19:31.891545+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh1x_20	\N	stu_demo_icdh1x_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:19:31.802454+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588771.803449900, "completedAssignments": 12}
stu_demo_icdh1x_21	2026-07-13 09:07:22.004205+00	2026-07-13 09:19:32.325426+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh1x_21	\N	stu_demo_icdh1x_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:19:32.238167+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502372.238166700, "completedAssignments": 13}
stu_demo_icdh1x_22	2026-07-13 09:07:22.504066+00	2026-07-13 09:19:32.759248+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh1x_22	\N	stu_demo_icdh1x_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:19:32.670978+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783415972.670977700, "completedAssignments": 14}
stu_demo_icdh1y_18	2026-07-13 09:07:32.629023+00	2026-07-13 09:19:41.824051+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh1y_18	\N	stu_demo_icdh1y_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:19:41.736297+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761581.736297400, "completedAssignments": 10}
stu_demo_icdh1y_19	2026-07-13 09:07:33.106255+00	2026-07-13 09:19:42.257083+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh1y_19	\N	stu_demo_icdh1y_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:19:42.169887+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675182.169886700, "completedAssignments": 11}
stu_demo_icdh1y_20	2026-07-13 09:07:33.597823+00	2026-07-13 09:19:42.688567+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh1y_20	\N	stu_demo_icdh1y_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:19:42.601994+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588782.601993600, "completedAssignments": 12}
stu_demo_icdh1y_21	2026-07-13 09:07:34.072885+00	2026-07-13 09:19:43.12323+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh1y_21	\N	stu_demo_icdh1y_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:19:43.035304+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502383.035303800, "completedAssignments": 13}
stu_demo_icdh1y_22	2026-07-13 09:07:34.5468+00	2026-07-13 09:19:43.558468+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh1y_22	\N	stu_demo_icdh1y_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:19:43.471847+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783415983.471846600, "completedAssignments": 14}
stu_demo_icdh1y_23	2026-07-13 09:07:35.022689+00	2026-07-13 09:19:43.989003+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh1y_23	\N	stu_demo_icdh1y_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:19:43.902858+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329583.902858400, "completedAssignments": 15}
stu_demo_icdh1y_24	2026-07-13 09:07:35.496267+00	2026-07-13 09:19:44.426553+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh1y_24	\N	stu_demo_icdh1y_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:19:44.338341+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934384.338340500, "completedAssignments": 16}
stu_demo_icdh2s_1	2026-07-13 09:07:36.450676+00	2026-07-13 09:19:45.202925+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh2s_1	\N	stu_demo_icdh2s_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:19:45.116546+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783847985.116546100, "completedAssignments": 9}
stu_demo_icdh2s_2	2026-07-13 09:07:36.934544+00	2026-07-13 09:19:45.634232+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh2s_2	\N	stu_demo_icdh2s_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:19:45.547667+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761585.547667400, "completedAssignments": 10}
stu_demo_icdh2s_3	2026-07-13 09:07:37.414583+00	2026-07-13 09:19:46.066763+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh2s_3	\N	stu_demo_icdh2s_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:19:45.979245+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675185.979245400, "completedAssignments": 11}
stu_demo_icdh2s_4	2026-07-13 09:07:37.898066+00	2026-07-13 09:19:46.49635+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh2s_4	\N	stu_demo_icdh2s_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:19:46.409613+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588786.409612600, "completedAssignments": 12}
stu_demo_icdh2s_5	2026-07-13 09:07:38.379865+00	2026-07-13 09:19:46.928782+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh2s_5	\N	stu_demo_icdh2s_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:19:46.842186+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502386.842186300, "completedAssignments": 13}
stu_demo_icdh2s_6	2026-07-13 09:07:38.857803+00	2026-07-13 09:19:47.360724+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh2s_6	\N	stu_demo_icdh2s_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:19:47.273617+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783415987.273617000, "completedAssignments": 14}
stu_demo_icdh2s_7	2026-07-13 09:07:39.334059+00	2026-07-13 09:19:47.792394+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh2s_7	\N	stu_demo_icdh2s_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:19:47.705795+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329587.705794700, "completedAssignments": 15}
stu_demo_icdh2s_8	2026-07-13 09:07:39.815317+00	2026-07-13 09:19:48.222675+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh2s_8	\N	stu_demo_icdh2s_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:19:48.13687+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934388.136870000, "completedAssignments": 16}
stu_demo_icdh1y_15	2026-07-13 09:07:31.187468+00	2026-07-13 09:19:40.513972+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh1y_15	\N	stu_demo_icdh1y_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:19:40.427099+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329580.427099200, "completedAssignments": 23}
stu_demo_icdh1y_16	2026-07-13 09:07:31.68283+00	2026-07-13 09:19:40.949357+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh1y_16	\N	stu_demo_icdh1y_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:19:40.860919+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934380.860918700, "completedAssignments": 8}
stu_demo_icdh1y_17	2026-07-13 09:07:32.156776+00	2026-07-13 09:19:41.391039+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh1y_17	\N	stu_demo_icdh1y_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:19:41.304237+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783847981.304236700, "completedAssignments": 9}
stu_demo_icdh2s_13	2026-07-13 09:07:42.290992+00	2026-07-13 09:19:50.422079+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh2s_13	\N	stu_demo_icdh2s_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:19:50.334918+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502390.334917700, "completedAssignments": 21}
stu_demo_icdh2s_14	2026-07-13 09:07:42.811083+00	2026-07-13 09:19:50.85519+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh2s_14	\N	stu_demo_icdh2s_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:19:50.767254+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783415990.767253800, "completedAssignments": 22}
stu_demo_icdh2s_15	2026-07-13 09:07:43.288777+00	2026-07-13 09:19:51.290875+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh2s_15	\N	stu_demo_icdh2s_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:19:51.204154+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329591.204154000, "completedAssignments": 23}
stu_demo_icdh2s_16	2026-07-13 09:07:43.76862+00	2026-07-13 09:19:51.72567+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh2s_16	\N	stu_demo_icdh2s_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:19:51.638284+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934391.638284400, "completedAssignments": 8}
stu_demo_icdh2s_17	2026-07-13 09:07:44.26661+00	2026-07-13 09:19:52.160119+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh2s_17	\N	stu_demo_icdh2s_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:19:52.071477+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783847992.071476700, "completedAssignments": 9}
stu_demo_icdh2s_18	2026-07-13 09:07:44.751082+00	2026-07-13 09:19:52.597018+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh2s_18	\N	stu_demo_icdh2s_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:19:52.509232+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761592.509231600, "completedAssignments": 10}
stu_demo_icdh2s_19	2026-07-13 09:07:45.236101+00	2026-07-13 09:19:53.029611+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh2s_19	\N	stu_demo_icdh2s_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:19:52.942635+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675192.942634900, "completedAssignments": 11}
stu_demo_icdh2s_20	2026-07-13 09:07:45.711537+00	2026-07-13 09:19:53.576599+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh2s_20	\N	stu_demo_icdh2s_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:19:53.48752+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588793.487520200, "completedAssignments": 12}
stu_demo_icdh2s_21	2026-07-13 09:07:46.193441+00	2026-07-13 09:19:54.042106+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh2s_21	\N	stu_demo_icdh2s_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:19:53.951077+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502393.951076600, "completedAssignments": 13}
stu_demo_icdh2s_22	2026-07-13 09:07:46.674186+00	2026-07-13 09:19:54.514132+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh2s_22	\N	stu_demo_icdh2s_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:19:54.413636+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783415994.413635600, "completedAssignments": 14}
stu_demo_icdh2s_23	2026-07-13 09:07:47.159624+00	2026-07-13 09:19:54.990456+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh2s_23	\N	stu_demo_icdh2s_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:19:54.886787+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329594.886786500, "completedAssignments": 15}
stu_demo_icdh2s_24	2026-07-13 09:07:47.648182+00	2026-07-13 09:19:55.518252+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh2s_24	\N	stu_demo_icdh2s_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:19:55.420774+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934395.420773900, "completedAssignments": 16}
stu_demo_icdh2t_1	2026-07-13 09:07:48.630259+00	2026-07-13 09:19:56.330591+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh2t_1	\N	stu_demo_icdh2t_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:19:56.239343+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783847996.239342900, "completedAssignments": 9}
stu_demo_icdh2t_2	2026-07-13 09:07:49.135383+00	2026-07-13 09:19:56.779263+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh2t_2	\N	stu_demo_icdh2t_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:19:56.691926+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761596.691925500, "completedAssignments": 10}
stu_demo_icdh2t_3	2026-07-13 09:07:49.634093+00	2026-07-13 09:19:57.217986+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh2t_3	\N	stu_demo_icdh2t_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:19:57.129569+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675197.129568800, "completedAssignments": 11}
stu_demo_icdh2s_10	2026-07-13 09:07:40.79076+00	2026-07-13 09:19:49.094827+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh2s_10	\N	stu_demo_icdh2s_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:19:49.008873+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761589.008873000, "completedAssignments": 18}
stu_demo_icdh2s_11	2026-07-13 09:07:41.270629+00	2026-07-13 09:19:49.535263+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh2s_11	\N	stu_demo_icdh2s_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:19:49.444376+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675189.444375900, "completedAssignments": 19}
stu_demo_icdh2s_12	2026-07-13 09:07:41.803583+00	2026-07-13 09:19:49.969176+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh2s_12	\N	stu_demo_icdh2s_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:19:49.882409+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588789.882409300, "completedAssignments": 20}
stu_demo_icdh2t_8	2026-07-13 09:07:52.042075+00	2026-07-13 09:19:59.463261+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh2t_8	\N	stu_demo_icdh2t_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:19:59.375313+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934399.375313100, "completedAssignments": 16}
stu_demo_icdh2t_9	2026-07-13 09:07:52.515306+00	2026-07-13 09:19:59.906408+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh2t_9	\N	stu_demo_icdh2t_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:19:59.819499+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783847999.819499100, "completedAssignments": 17}
stu_demo_icdh2t_10	2026-07-13 09:07:52.996121+00	2026-07-13 09:20:00.338523+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh2t_10	\N	stu_demo_icdh2t_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:20:00.251438+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761600.252453600, "completedAssignments": 18}
stu_demo_icdh2t_11	2026-07-13 09:07:53.482321+00	2026-07-13 09:20:00.782116+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh2t_11	\N	stu_demo_icdh2t_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:20:00.690099+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675200.690099300, "completedAssignments": 19}
stu_demo_icdh2t_12	2026-07-13 09:07:53.964448+00	2026-07-13 09:20:01.226208+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh2t_12	\N	stu_demo_icdh2t_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:20:01.138688+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588801.138687900, "completedAssignments": 20}
stu_demo_icdh2t_13	2026-07-13 09:07:54.450567+00	2026-07-13 09:20:01.669641+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh2t_13	\N	stu_demo_icdh2t_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:20:01.574527+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502401.574527200, "completedAssignments": 21}
stu_demo_icdh2t_14	2026-07-13 09:07:54.930259+00	2026-07-13 09:20:02.102442+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh2t_14	\N	stu_demo_icdh2t_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:20:02.014703+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416002.014702500, "completedAssignments": 22}
stu_demo_icdh2t_15	2026-07-13 09:07:55.426786+00	2026-07-13 09:20:02.56518+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh2t_15	\N	stu_demo_icdh2t_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:20:02.476808+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329602.476808300, "completedAssignments": 23}
stu_demo_icdh2t_16	2026-07-13 09:07:55.917492+00	2026-07-13 09:20:03.014581+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh2t_16	\N	stu_demo_icdh2t_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:20:02.925854+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934402.926850300, "completedAssignments": 8}
stu_demo_icdh2t_17	2026-07-13 09:07:56.394857+00	2026-07-13 09:20:03.451263+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh2t_17	\N	stu_demo_icdh2t_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:20:03.360151+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848003.360150700, "completedAssignments": 9}
stu_demo_icdh2t_18	2026-07-13 09:07:56.871092+00	2026-07-13 09:20:03.889181+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh2t_18	\N	stu_demo_icdh2t_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:20:03.800034+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761603.800034100, "completedAssignments": 10}
stu_demo_icdh2t_19	2026-07-13 09:07:57.351465+00	2026-07-13 09:20:04.327225+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh2t_19	\N	stu_demo_icdh2t_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:20:04.236018+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675204.236018200, "completedAssignments": 11}
stu_demo_icdh2t_20	2026-07-13 09:07:57.836768+00	2026-07-13 09:20:04.763845+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh2t_20	\N	stu_demo_icdh2t_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:20:04.676062+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588804.676061900, "completedAssignments": 12}
stu_demo_icdh2t_21	2026-07-13 09:07:58.319434+00	2026-07-13 09:20:05.216786+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh2t_21	\N	stu_demo_icdh2t_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:20:05.127618+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502405.127618200, "completedAssignments": 13}
stu_demo_icdh2t_22	2026-07-13 09:07:58.804954+00	2026-07-13 09:20:05.66215+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh2t_22	\N	stu_demo_icdh2t_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:20:05.575356+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416005.575356100, "completedAssignments": 14}
stu_demo_icdh2t_5	2026-07-13 09:07:50.594492+00	2026-07-13 09:19:58.121877+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh2t_5	\N	stu_demo_icdh2t_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:19:58.031427+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502398.031427300, "completedAssignments": 13}
stu_demo_icdh2t_6	2026-07-13 09:07:51.083167+00	2026-07-13 09:19:58.559502+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh2t_6	\N	stu_demo_icdh2t_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:19:58.472575+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783415998.472575300, "completedAssignments": 14}
stu_demo_icdh2t_7	2026-07-13 09:07:51.573376+00	2026-07-13 09:19:59.002151+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh2t_7	\N	stu_demo_icdh2t_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:19:58.914938+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329598.914937700, "completedAssignments": 15}
stu_demo_icdh3n_3	2026-07-13 09:08:01.732514+00	2026-07-13 09:20:08.197685+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh3n_3	\N	stu_demo_icdh3n_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:20:08.110952+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675208.110952100, "completedAssignments": 11}
stu_demo_icdh3n_4	2026-07-13 09:08:02.204436+00	2026-07-13 09:20:08.639873+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh3n_4	\N	stu_demo_icdh3n_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:20:08.549772+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588808.549772000, "completedAssignments": 12}
stu_demo_icdh3n_5	2026-07-13 09:08:02.687491+00	2026-07-13 09:20:09.072366+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh3n_5	\N	stu_demo_icdh3n_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:20:08.985044+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502408.985044000, "completedAssignments": 13}
stu_demo_icdh3n_6	2026-07-13 09:08:03.176613+00	2026-07-13 09:20:09.526168+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh3n_6	\N	stu_demo_icdh3n_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:20:09.423993+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416009.423993100, "completedAssignments": 14}
stu_demo_icdh3n_7	2026-07-13 09:08:03.660361+00	2026-07-13 09:20:09.966021+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh3n_7	\N	stu_demo_icdh3n_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:20:09.876821+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329609.876820700, "completedAssignments": 15}
stu_demo_icdh3n_8	2026-07-13 09:08:04.139382+00	2026-07-13 09:20:10.402646+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh3n_8	\N	stu_demo_icdh3n_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:20:10.314453+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934410.314452500, "completedAssignments": 16}
stu_demo_icdh3n_9	2026-07-13 09:08:04.615846+00	2026-07-13 09:20:10.855534+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh3n_9	\N	stu_demo_icdh3n_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:20:10.766366+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848010.766365800, "completedAssignments": 17}
stu_demo_icdh1y_14	2026-07-13 09:07:30.709529+00	2026-07-13 09:19:40.067977+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_12	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh1y_14	\N	stu_demo_icdh1y_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:19:39.981876+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783415979.981875700, "completedAssignments": 22}
stu_demo_icdh2s_9	2026-07-13 09:07:40.303847+00	2026-07-13 09:19:48.662167+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_21	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh2s_9	\N	stu_demo_icdh2s_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:19:48.574737+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783847988.574736500, "completedAssignments": 17}
stu_demo_icdh2t_4	2026-07-13 09:07:50.116435+00	2026-07-13 09:19:57.675629+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh2t_4	\N	stu_demo_icdh2t_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:19:57.586256+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588797.586256000, "completedAssignments": 12}
stu_demo_icdh2t_23	2026-07-13 09:07:59.302591+00	2026-07-13 09:20:06.096571+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh2t_23	\N	stu_demo_icdh2t_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:20:06.009431+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329606.009431200, "completedAssignments": 15}
stu_demo_icdh2t_24	2026-07-13 09:07:59.776989+00	2026-07-13 09:20:06.532854+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_22	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh2t_24	\N	stu_demo_icdh2t_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:20:06.443863+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934406.443863000, "completedAssignments": 16}
stu_demo_icdh3n_1	2026-07-13 09:08:00.746634+00	2026-07-13 09:20:07.315216+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh3n_1	\N	stu_demo_icdh3n_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:20:07.227631+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848007.227630900, "completedAssignments": 9}
stu_demo_icdh3n_2	2026-07-13 09:08:01.234246+00	2026-07-13 09:20:07.748249+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh3n_2	\N	stu_demo_icdh3n_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:20:07.661146+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761607.661146400, "completedAssignments": 10}
stu_demo_icdh3n_10	2026-07-13 09:12:36.171704+00	2026-07-13 09:20:11.290842+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh3n_10	\N	stu_demo_icdh3n_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:20:11.202464+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761611.202463800, "completedAssignments": 18}
stu_demo_icdh3n_11	2026-07-13 09:12:36.623229+00	2026-07-13 09:20:11.726328+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh3n_11	\N	stu_demo_icdh3n_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:20:11.638719+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675211.638719200, "completedAssignments": 19}
stu_demo_icdh1x_19	2026-07-13 09:07:21.019866+00	2026-07-13 09:19:31.456875+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh1x_19	\N	stu_demo_icdh1x_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:19:31.367942+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675171.367941900, "completedAssignments": 11}
stu_demo_icdh1x_1	2026-07-13 09:07:12.29582+00	2026-07-13 09:19:23.564903+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_11	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh1x_1	\N	stu_demo_icdh1x_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:19:23.472523+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783847963.472523400, "completedAssignments": 9}
stu_demo_icdh3n_14	2026-07-13 09:12:37.965237+00	2026-07-13 09:20:13.025825+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh3n_14	\N	stu_demo_icdh3n_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:20:12.93911+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416012.939109600, "completedAssignments": 22}
stu_demo_icdh3n_15	2026-07-13 09:12:38.401919+00	2026-07-13 09:20:13.459718+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh3n_15	\N	stu_demo_icdh3n_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:20:13.370243+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329613.370243300, "completedAssignments": 23}
stu_demo_icdh3n_16	2026-07-13 09:12:38.834089+00	2026-07-13 09:20:13.892699+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh3n_16	\N	stu_demo_icdh3n_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:20:13.806173+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934413.806173100, "completedAssignments": 8}
stu_demo_icdh3n_17	2026-07-13 09:12:39.264436+00	2026-07-13 09:20:14.324259+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh3n_17	\N	stu_demo_icdh3n_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:20:14.237594+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848014.237594300, "completedAssignments": 9}
stu_demo_icdh3n_18	2026-07-13 09:12:39.691441+00	2026-07-13 09:20:14.767161+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh3n_18	\N	stu_demo_icdh3n_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:20:14.679527+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761614.679527200, "completedAssignments": 10}
stu_demo_icdh3n_19	2026-07-13 09:12:40.129942+00	2026-07-13 09:20:15.208548+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh3n_19	\N	stu_demo_icdh3n_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:20:15.120371+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675215.120371000, "completedAssignments": 11}
stu_demo_icdh3n_20	2026-07-13 09:12:40.574338+00	2026-07-13 09:20:15.642366+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh3n_20	\N	stu_demo_icdh3n_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:20:15.555272+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588815.555271600, "completedAssignments": 12}
stu_demo_icdh3n_21	2026-07-13 09:12:41.02427+00	2026-07-13 09:20:16.082242+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh3n_21	\N	stu_demo_icdh3n_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:20:15.992997+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502415.992996700, "completedAssignments": 13}
stu_demo_icdh3n_22	2026-07-13 09:12:41.461589+00	2026-07-13 09:20:16.521874+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh3n_22	\N	stu_demo_icdh3n_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:20:16.434046+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416016.434046400, "completedAssignments": 14}
stu_demo_icdh3n_23	2026-07-13 09:12:41.894368+00	2026-07-13 09:20:16.982469+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh3n_23	\N	stu_demo_icdh3n_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:20:16.894223+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329616.894222900, "completedAssignments": 15}
stu_demo_icdh3n_24	2026-07-13 09:12:42.326687+00	2026-07-13 09:20:17.418126+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh3n_24	\N	stu_demo_icdh3n_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:20:17.330402+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934417.331402300, "completedAssignments": 16}
stu_demo_icdh3o_1	2026-07-13 09:12:43.20437+00	2026-07-13 09:20:18.204426+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh3o_1	\N	stu_demo_icdh3o_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:20:18.116571+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848018.116570700, "completedAssignments": 9}
stu_demo_icdh3o_2	2026-07-13 09:12:43.637645+00	2026-07-13 09:20:18.636951+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh3o_2	\N	stu_demo_icdh3o_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:20:18.549562+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761618.549562400, "completedAssignments": 10}
stu_demo_icdh3o_3	2026-07-13 09:12:44.071151+00	2026-07-13 09:20:19.06891+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh3o_3	\N	stu_demo_icdh3o_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:20:18.981976+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675218.981975700, "completedAssignments": 11}
stu_demo_icdh3o_4	2026-07-13 09:12:44.496439+00	2026-07-13 09:20:19.509386+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh3o_4	\N	stu_demo_icdh3o_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:20:19.416022+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588819.416021700, "completedAssignments": 12}
stu_demo_icdh3o_5	2026-07-13 09:12:44.934405+00	2026-07-13 09:20:19.946386+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh3o_5	\N	stu_demo_icdh3o_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:20:19.859021+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502419.859021100, "completedAssignments": 13}
stu_demo_icdh3o_6	2026-07-13 09:12:45.37432+00	2026-07-13 09:20:20.383881+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh3o_6	\N	stu_demo_icdh3o_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:20:20.296077+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416020.296076800, "completedAssignments": 14}
stu_demo_icdh3n_13	2026-07-13 09:12:37.518826+00	2026-07-13 09:20:12.590803+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh3n_13	\N	stu_demo_icdh3n_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:20:12.503729+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502412.503729100, "completedAssignments": 21}
stu_demo_icdh3o_9	2026-07-13 09:12:46.717373+00	2026-07-13 09:20:21.700424+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh3o_9	\N	stu_demo_icdh3o_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:20:21.611863+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848021.611863000, "completedAssignments": 17}
stu_demo_icdh3o_10	2026-07-13 09:12:47.159332+00	2026-07-13 09:20:22.133782+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh3o_10	\N	stu_demo_icdh3o_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:20:22.047187+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761622.047186900, "completedAssignments": 18}
stu_demo_icdh3o_11	2026-07-13 09:12:47.588214+00	2026-07-13 09:20:22.567947+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh3o_11	\N	stu_demo_icdh3o_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:20:22.479424+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675222.479423700, "completedAssignments": 19}
stu_demo_icdh3o_12	2026-07-13 09:12:48.0267+00	2026-07-13 09:20:23.006235+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh3o_12	\N	stu_demo_icdh3o_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:20:22.917868+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588822.917868400, "completedAssignments": 20}
stu_demo_icdh3o_13	2026-07-13 09:12:48.453416+00	2026-07-13 09:20:23.439616+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh3o_13	\N	stu_demo_icdh3o_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:20:23.350956+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502423.350956200, "completedAssignments": 21}
stu_demo_icdh3o_14	2026-07-13 09:12:48.877609+00	2026-07-13 09:20:23.871675+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh3o_14	\N	stu_demo_icdh3o_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:20:23.785174+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416023.785174400, "completedAssignments": 22}
stu_demo_icdh3o_15	2026-07-13 09:12:49.301375+00	2026-07-13 09:20:24.311766+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh3o_15	\N	stu_demo_icdh3o_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:20:24.217746+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329624.218746700, "completedAssignments": 23}
stu_demo_icdh3o_16	2026-07-13 09:12:49.737064+00	2026-07-13 09:20:24.742907+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh3o_16	\N	stu_demo_icdh3o_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:20:24.655937+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934424.655936600, "completedAssignments": 8}
stu_demo_icdh3o_17	2026-07-13 09:12:50.169094+00	2026-07-13 09:20:25.176324+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh3o_17	\N	stu_demo_icdh3o_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:20:25.089669+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848025.089669400, "completedAssignments": 9}
stu_demo_icdh3o_18	2026-07-13 09:12:50.595144+00	2026-07-13 09:20:25.608606+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh3o_18	\N	stu_demo_icdh3o_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:20:25.522039+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761625.522039100, "completedAssignments": 10}
stu_demo_icdh3o_19	2026-07-13 09:12:51.03325+00	2026-07-13 09:20:26.039814+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh3o_19	\N	stu_demo_icdh3o_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:20:25.953727+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675225.953726900, "completedAssignments": 11}
stu_demo_icdh3o_20	2026-07-13 09:12:51.461048+00	2026-07-13 09:20:26.476113+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh3o_20	\N	stu_demo_icdh3o_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:20:26.385131+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588826.385130500, "completedAssignments": 12}
stu_demo_icdh3o_21	2026-07-13 09:12:51.886672+00	2026-07-13 09:20:26.910092+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh3o_21	\N	stu_demo_icdh3o_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:20:26.823441+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502426.823440900, "completedAssignments": 13}
stu_demo_icdh3o_22	2026-07-13 09:12:52.318081+00	2026-07-13 09:20:27.347006+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh3o_22	\N	stu_demo_icdh3o_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:20:27.260353+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416027.260353000, "completedAssignments": 14}
stu_demo_icdh3o_23	2026-07-13 09:12:52.740766+00	2026-07-13 09:20:27.797635+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh3o_23	\N	stu_demo_icdh3o_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:20:27.702815+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329627.702815400, "completedAssignments": 15}
stu_demo_icdh3o_24	2026-07-13 09:12:53.168263+00	2026-07-13 09:20:28.232869+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh3o_24	\N	stu_demo_icdh3o_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:20:28.145499+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934428.145498600, "completedAssignments": 16}
stu_demo_icdh4i_1	2026-07-13 09:12:54.014433+00	2026-07-13 09:20:29.043817+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh4i_1	\N	stu_demo_icdh4i_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:20:28.956884+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848028.956883500, "completedAssignments": 9}
stu_demo_icdh3o_8	2026-07-13 09:12:46.280211+00	2026-07-13 09:20:21.263114+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh3o_8	\N	stu_demo_icdh3o_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:20:21.174792+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934421.174791700, "completedAssignments": 16}
stu_demo_icdh4i_4	2026-07-13 09:12:55.287884+00	2026-07-13 09:20:30.615127+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh4i_4	\N	stu_demo_icdh4i_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:20:30.528442+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588830.528442300, "completedAssignments": 12}
stu_demo_icdh4i_5	2026-07-13 09:12:55.711865+00	2026-07-13 09:20:31.064269+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh4i_5	\N	stu_demo_icdh4i_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:20:30.97413+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502430.974129900, "completedAssignments": 13}
stu_demo_icdh4i_6	2026-07-13 09:12:56.139158+00	2026-07-13 09:20:31.496411+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh4i_6	\N	stu_demo_icdh4i_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:20:31.409548+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416031.409547700, "completedAssignments": 14}
stu_demo_icdh4i_7	2026-07-13 09:12:56.577821+00	2026-07-13 09:20:31.927985+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh4i_7	\N	stu_demo_icdh4i_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:20:31.840197+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329631.841200400, "completedAssignments": 15}
stu_demo_icdh4i_8	2026-07-13 09:12:57.057491+00	2026-07-13 09:20:32.359029+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh4i_8	\N	stu_demo_icdh4i_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:20:32.272427+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934432.272426900, "completedAssignments": 16}
stu_demo_icdh4i_9	2026-07-13 09:12:57.496922+00	2026-07-13 09:20:32.793493+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh4i_9	\N	stu_demo_icdh4i_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:20:32.70727+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848032.707270200, "completedAssignments": 17}
stu_demo_icdh4i_10	2026-07-13 09:12:57.936523+00	2026-07-13 09:20:33.225354+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh4i_10	\N	stu_demo_icdh4i_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:20:33.13821+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761633.138209500, "completedAssignments": 18}
stu_demo_icdh4i_11	2026-07-13 09:12:58.370475+00	2026-07-13 09:20:33.668285+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh4i_11	\N	stu_demo_icdh4i_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:20:33.582254+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675233.582253500, "completedAssignments": 19}
stu_demo_icdh4i_12	2026-07-13 09:12:58.796052+00	2026-07-13 09:20:34.100552+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh4i_12	\N	stu_demo_icdh4i_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:20:34.013785+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588834.013784800, "completedAssignments": 20}
stu_demo_icdh4i_13	2026-07-13 09:12:59.221283+00	2026-07-13 09:20:34.532428+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh4i_13	\N	stu_demo_icdh4i_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:20:34.444892+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502434.444892100, "completedAssignments": 21}
stu_demo_icdh4i_14	2026-07-13 09:12:59.66795+00	2026-07-13 09:20:34.964283+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh4i_14	\N	stu_demo_icdh4i_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:20:34.876366+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416034.876366300, "completedAssignments": 22}
stu_demo_icdh4i_15	2026-07-13 09:13:00.1103+00	2026-07-13 09:20:35.396903+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh4i_15	\N	stu_demo_icdh4i_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:20:35.309197+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329635.309196900, "completedAssignments": 23}
stu_demo_icdh4i_16	2026-07-13 09:13:00.540177+00	2026-07-13 09:20:35.833996+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh4i_16	\N	stu_demo_icdh4i_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:20:35.74696+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934435.746960400, "completedAssignments": 8}
stu_demo_icdh4i_17	2026-07-13 09:13:00.982343+00	2026-07-13 09:20:36.264063+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh4i_17	\N	stu_demo_icdh4i_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:20:36.178387+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848036.178387300, "completedAssignments": 9}
stu_demo_icdh4i_18	2026-07-13 09:13:01.407484+00	2026-07-13 09:20:36.69628+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh4i_18	\N	stu_demo_icdh4i_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:20:36.608918+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761636.608918400, "completedAssignments": 10}
stu_demo_icdh4i_19	2026-07-13 09:13:01.837728+00	2026-07-13 09:20:37.141761+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh4i_19	\N	stu_demo_icdh4i_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:20:37.053444+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675237.053444200, "completedAssignments": 11}
stu_demo_icdh4i_20	2026-07-13 09:13:02.27031+00	2026-07-13 09:20:37.574625+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh4i_20	\N	stu_demo_icdh4i_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:20:37.48772+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588837.487720000, "completedAssignments": 12}
stu_demo_icdh4i_3	2026-07-13 09:12:54.865138+00	2026-07-13 09:20:30.181969+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh4i_3	\N	stu_demo_icdh4i_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:20:30.094673+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675230.094672800, "completedAssignments": 11}
stu_demo_icdh4i_23	2026-07-13 09:13:03.591924+00	2026-07-13 09:20:38.901132+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh4i_23	\N	stu_demo_icdh4i_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:20:38.81417+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329638.814170100, "completedAssignments": 15}
stu_demo_icdh4i_24	2026-07-13 09:13:04.038797+00	2026-07-13 09:20:39.33368+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh4i_24	\N	stu_demo_icdh4i_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:20:39.247443+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934439.247443000, "completedAssignments": 16}
stu_demo_icdh4j_1	2026-07-13 09:13:04.893194+00	2026-07-13 09:20:40.112024+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh4j_1	\N	stu_demo_icdh4j_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:20:40.023958+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848040.023958100, "completedAssignments": 9}
stu_demo_icdh4j_2	2026-07-13 09:13:05.321018+00	2026-07-13 09:20:40.545058+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh4j_2	\N	stu_demo_icdh4j_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:20:40.457354+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761640.458358700, "completedAssignments": 10}
stu_demo_icdh4j_3	2026-07-13 09:13:05.755153+00	2026-07-13 09:20:40.979149+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh4j_3	\N	stu_demo_icdh4j_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:20:40.890867+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675240.890866500, "completedAssignments": 11}
stu_demo_icdh4j_4	2026-07-13 09:13:06.180715+00	2026-07-13 09:20:41.410363+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh4j_4	\N	stu_demo_icdh4j_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:20:41.323735+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588841.323735000, "completedAssignments": 12}
stu_demo_icdh4j_5	2026-07-13 09:13:06.608255+00	2026-07-13 09:20:41.850498+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh4j_5	\N	stu_demo_icdh4j_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:20:41.76102+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502441.761019500, "completedAssignments": 13}
stu_demo_icdh4j_6	2026-07-13 09:13:07.055905+00	2026-07-13 09:20:42.282323+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh4j_6	\N	stu_demo_icdh4j_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:20:42.195576+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416042.195575600, "completedAssignments": 14}
stu_demo_icdh4j_7	2026-07-13 09:13:07.495042+00	2026-07-13 09:20:42.713014+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh4j_7	\N	stu_demo_icdh4j_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:20:42.626433+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329642.626433200, "completedAssignments": 15}
stu_demo_icdh4j_8	2026-07-13 09:13:07.920855+00	2026-07-13 09:20:43.14578+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh4j_8	\N	stu_demo_icdh4j_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:20:43.058932+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934443.058931900, "completedAssignments": 16}
stu_demo_icdh4j_9	2026-07-13 09:13:08.347191+00	2026-07-13 09:20:43.578749+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh4j_9	\N	stu_demo_icdh4j_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:20:43.491949+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848043.491949000, "completedAssignments": 17}
stu_demo_icdh4j_10	2026-07-13 09:13:08.773417+00	2026-07-13 09:20:44.011243+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh4j_10	\N	stu_demo_icdh4j_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:20:43.923558+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761643.923558100, "completedAssignments": 18}
stu_demo_icdh4j_11	2026-07-13 09:13:09.197831+00	2026-07-13 09:20:44.448556+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh4j_11	\N	stu_demo_icdh4j_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:20:44.357786+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675244.357786300, "completedAssignments": 19}
stu_demo_icdh4j_12	2026-07-13 09:13:09.634056+00	2026-07-13 09:20:44.882846+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh4j_12	\N	stu_demo_icdh4j_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:20:44.796484+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588844.796483500, "completedAssignments": 20}
stu_demo_icdh4j_13	2026-07-13 09:13:10.063529+00	2026-07-13 09:20:45.313551+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh4j_13	\N	stu_demo_icdh4j_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:20:45.22685+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502445.226850200, "completedAssignments": 21}
stu_demo_icdh4j_14	2026-07-13 09:13:10.517957+00	2026-07-13 09:20:45.745616+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh4j_14	\N	stu_demo_icdh4j_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:20:45.658617+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416045.658617100, "completedAssignments": 22}
stu_demo_icdh4j_15	2026-07-13 09:13:10.983271+00	2026-07-13 09:20:46.177667+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh4j_15	\N	stu_demo_icdh4j_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:20:46.091667+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329646.091666900, "completedAssignments": 23}
stu_demo_icdh4i_22	2026-07-13 09:13:03.162095+00	2026-07-13 09:20:38.467011+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh4i_22	\N	stu_demo_icdh4i_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:20:38.365763+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416038.365762700, "completedAssignments": 14}
stu_demo_icdh4j_18	2026-07-13 09:13:12.322334+00	2026-07-13 09:20:47.472022+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh4j_18	\N	stu_demo_icdh4j_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:20:47.386196+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761647.386196200, "completedAssignments": 10}
stu_demo_icdh4j_19	2026-07-13 09:13:12.759096+00	2026-07-13 09:20:47.904331+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh4j_19	\N	stu_demo_icdh4j_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:20:47.816832+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675247.816831700, "completedAssignments": 11}
stu_demo_icdh4j_20	2026-07-13 09:13:13.186917+00	2026-07-13 09:20:48.341364+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh4j_20	\N	stu_demo_icdh4j_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:20:48.249706+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588848.249705700, "completedAssignments": 12}
stu_demo_icdh4j_21	2026-07-13 09:13:13.622484+00	2026-07-13 09:20:48.79615+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh4j_21	\N	stu_demo_icdh4j_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:20:48.709153+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502448.709152500, "completedAssignments": 13}
stu_demo_icdh4j_22	2026-07-13 09:13:14.047327+00	2026-07-13 09:20:49.23185+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh4j_22	\N	stu_demo_icdh4j_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:20:49.144795+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416049.144794800, "completedAssignments": 14}
stu_demo_icdh4j_23	2026-07-13 09:13:14.492183+00	2026-07-13 09:20:49.663371+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh4j_23	\N	stu_demo_icdh4j_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:20:49.576675+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329649.576674500, "completedAssignments": 15}
stu_demo_icdh4j_24	2026-07-13 09:13:14.918136+00	2026-07-13 09:20:50.101432+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh4j_24	\N	stu_demo_icdh4j_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:20:50.014841+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934450.014841400, "completedAssignments": 16}
stu_demo_icdh5d_1	2026-07-13 09:13:15.801336+00	2026-07-13 09:20:50.895833+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh5d_1	\N	stu_demo_icdh5d_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:20:50.809562+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848050.809561900, "completedAssignments": 9}
stu_demo_icdh5d_2	2026-07-13 09:13:16.254153+00	2026-07-13 09:20:51.330102+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh5d_2	\N	stu_demo_icdh5d_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:20:51.242483+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761651.242482700, "completedAssignments": 10}
stu_demo_icdh5d_3	2026-07-13 09:13:16.696378+00	2026-07-13 09:20:51.764021+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh5d_3	\N	stu_demo_icdh5d_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:20:51.677249+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675251.677249400, "completedAssignments": 11}
stu_demo_icdh5d_4	2026-07-13 09:13:17.126645+00	2026-07-13 09:20:52.195684+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh5d_4	\N	stu_demo_icdh5d_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:20:52.108194+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588852.108193500, "completedAssignments": 12}
stu_demo_icdh5d_5	2026-07-13 09:13:17.549698+00	2026-07-13 09:20:52.641936+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh5d_5	\N	stu_demo_icdh5d_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:20:52.555743+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502452.555743400, "completedAssignments": 13}
stu_demo_icdh5d_6	2026-07-13 09:13:17.974225+00	2026-07-13 09:20:53.071946+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh5d_6	\N	stu_demo_icdh5d_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:20:52.986485+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416052.986485300, "completedAssignments": 14}
stu_demo_icdh5d_7	2026-07-13 09:13:18.403305+00	2026-07-13 09:20:53.504903+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh5d_7	\N	stu_demo_icdh5d_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:20:53.4176+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329653.417599600, "completedAssignments": 15}
stu_demo_icdh5d_8	2026-07-13 09:13:18.847417+00	2026-07-13 09:20:53.93802+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh5d_8	\N	stu_demo_icdh5d_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:20:53.849725+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934453.849725400, "completedAssignments": 16}
stu_demo_icdh5d_9	2026-07-13 09:13:19.293994+00	2026-07-13 09:20:54.369084+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh5d_9	\N	stu_demo_icdh5d_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:20:54.281956+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848054.281956200, "completedAssignments": 17}
stu_demo_icdh5d_10	2026-07-13 09:13:19.718034+00	2026-07-13 09:20:54.802622+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh5d_10	\N	stu_demo_icdh5d_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:20:54.715688+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761654.715687700, "completedAssignments": 18}
stu_demo_icdh4j_17	2026-07-13 09:13:11.893937+00	2026-07-13 09:20:47.041779+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh4j_17	\N	stu_demo_icdh4j_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:20:46.95505+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848046.955049900, "completedAssignments": 9}
stu_demo_icdh5d_13	2026-07-13 09:13:21.013512+00	2026-07-13 09:20:56.120375+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh5d_13	\N	stu_demo_icdh5d_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:20:56.033248+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502456.033248400, "completedAssignments": 21}
stu_demo_icdh5d_14	2026-07-13 09:13:21.439823+00	2026-07-13 09:20:56.558435+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh5d_14	\N	stu_demo_icdh5d_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:20:56.472594+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416056.472593600, "completedAssignments": 22}
stu_demo_icdh5d_15	2026-07-13 09:13:21.868123+00	2026-07-13 09:20:57.013205+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh5d_15	\N	stu_demo_icdh5d_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:20:56.908981+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329656.908981200, "completedAssignments": 23}
stu_demo_icdh5d_16	2026-07-13 09:13:22.298997+00	2026-07-13 09:20:57.458996+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh5d_16	\N	stu_demo_icdh5d_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:20:57.370269+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934457.370269200, "completedAssignments": 8}
stu_demo_icdh5d_17	2026-07-13 09:13:22.72585+00	2026-07-13 09:20:57.891022+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh5d_17	\N	stu_demo_icdh5d_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:20:57.804397+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848057.804396600, "completedAssignments": 9}
stu_demo_icdh5d_18	2026-07-13 09:13:23.163612+00	2026-07-13 09:20:58.321861+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh5d_18	\N	stu_demo_icdh5d_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:20:58.236429+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761658.236429400, "completedAssignments": 10}
stu_demo_icdh5d_19	2026-07-13 09:13:23.587181+00	2026-07-13 09:20:58.752366+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh5d_19	\N	stu_demo_icdh5d_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:20:58.66573+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675258.665730200, "completedAssignments": 11}
stu_demo_icdh5d_20	2026-07-13 09:13:24.015602+00	2026-07-13 09:20:59.181809+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh5d_20	\N	stu_demo_icdh5d_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:20:59.094946+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588859.094945900, "completedAssignments": 12}
stu_demo_icdh5d_21	2026-07-13 09:13:24.448904+00	2026-07-13 09:20:59.615306+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh5d_21	\N	stu_demo_icdh5d_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:20:59.528459+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502459.528459400, "completedAssignments": 13}
stu_demo_icdh5d_22	2026-07-13 09:13:24.875267+00	2026-07-13 09:21:00.048684+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh5d_22	\N	stu_demo_icdh5d_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:20:59.960734+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416059.960733600, "completedAssignments": 14}
stu_demo_icdh5d_23	2026-07-13 09:13:25.306355+00	2026-07-13 09:21:00.482427+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh5d_23	\N	stu_demo_icdh5d_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:21:00.396368+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329660.396367800, "completedAssignments": 15}
stu_demo_icdh5d_24	2026-07-13 09:13:25.72939+00	2026-07-13 09:21:00.913159+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh5d_24	\N	stu_demo_icdh5d_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:21:00.826299+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934460.826299100, "completedAssignments": 16}
stu_demo_icdh5e_1	2026-07-13 09:13:26.587+00	2026-07-13 09:21:01.692878+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ001	Trần Đức Anh	stu_demo_icdh5e_1	\N	stu_demo_icdh5e_1@student.demo.erg.edu.vn	MALE	2012-02-02 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0999194861	\N	\N	2025-12-13 09:21:01.605771+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848061.605770600, "completedAssignments": 9}
stu_demo_icdh5d_12	2026-07-13 09:13:20.564375+00	2026-07-13 09:20:55.682924+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh5d_12	\N	stu_demo_icdh5d_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:20:55.595253+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588855.595253400, "completedAssignments": 20}
stu_demo_icdh5e_2	2026-07-13 09:13:27.014989+00	2026-07-13 09:21:02.124557+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh5e_2	\N	stu_demo_icdh5e_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:21:02.037593+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761662.037593000, "completedAssignments": 10}
stu_demo_icdh5e_3	2026-07-13 09:13:27.444877+00	2026-07-13 09:21:02.555136+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ003	Phạm Ngọc Anh	stu_demo_icdh5e_3	\N	stu_demo_icdh5e_3@student.demo.erg.edu.vn	MALE	2012-04-04 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0999194859	\N	\N	2025-10-13 09:21:02.468256+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675262.468255500, "completedAssignments": 11}
stu_demo_icdh5e_4	2026-07-13 09:13:27.874674+00	2026-07-13 09:21:02.987625+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ004	Võ Bảo Ngọc	stu_demo_icdh5e_4	\N	stu_demo_icdh5e_4@student.demo.erg.edu.vn	FEMALE	2012-05-05 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0999194858	\N	\N	2025-09-13 09:21:02.900991+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588862.900991200, "completedAssignments": 12}
stu_demo_icdh5e_5	2026-07-13 09:13:28.301352+00	2026-07-13 09:21:03.429572+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ005	Đặng Tuấn Kiệt	stu_demo_icdh5e_5	\N	stu_demo_icdh5e_5@student.demo.erg.edu.vn	MALE	2012-06-06 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0999194857	\N	\N	2025-08-13 09:21:03.342249+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502463.342249000, "completedAssignments": 13}
stu_demo_icdh5e_7	2026-07-13 09:13:29.14845+00	2026-07-13 09:21:04.329499+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh5e_7	\N	stu_demo_icdh5e_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:21:04.234194+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329664.234194300, "completedAssignments": 15}
stu_demo_icdh5e_8	2026-07-13 09:13:29.571706+00	2026-07-13 09:21:04.847025+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ008	Nguyễn Khánh Linh	stu_demo_icdh5e_8	\N	stu_demo_icdh5e_8@student.demo.erg.edu.vn	FEMALE	2012-09-09 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0999194854	\N	\N	2025-05-13 09:21:04.743895+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934464.743894800, "completedAssignments": 16}
stu_demo_icdh5e_9	2026-07-13 09:13:29.998862+00	2026-07-13 09:21:05.281446+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ009	Trần Thảo Vy	stu_demo_icdh5e_9	\N	stu_demo_icdh5e_9@student.demo.erg.edu.vn	MALE	2012-10-10 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0999194853	\N	\N	2026-01-13 09:21:05.195379+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848065.195378600, "completedAssignments": 17}
stu_demo_icdh5e_10	2026-07-13 09:13:30.440473+00	2026-07-13 09:21:05.715685+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ010	Lê Gia Hân	stu_demo_icdh5e_10	\N	stu_demo_icdh5e_10@student.demo.erg.edu.vn	FEMALE	2012-11-11 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0964566909	\N	\N	2025-12-13 09:21:05.62856+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761665.628559700, "completedAssignments": 18}
stu_demo_icdh5e_11	2026-07-13 09:13:30.872597+00	2026-07-13 09:21:06.149889+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh5e_11	\N	stu_demo_icdh5e_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:21:06.063252+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675266.063252300, "completedAssignments": 19}
stu_demo_icdh5e_12	2026-07-13 09:13:31.320118+00	2026-07-13 09:21:06.581713+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh5e_12	\N	stu_demo_icdh5e_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:21:06.494771+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588866.494770800, "completedAssignments": 20}
stu_demo_icdh5e_13	2026-07-13 09:13:31.751023+00	2026-07-13 09:21:07.023627+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ013	Đặng Ngọc Anh	stu_demo_icdh5e_13	\N	stu_demo_icdh5e_13@student.demo.erg.edu.vn	MALE	2012-02-14 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0964566912	\N	\N	2025-09-13 09:21:06.937587+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502466.937587000, "completedAssignments": 21}
stu_demo_icdh5e_14	2026-07-13 09:13:32.186062+00	2026-07-13 09:21:07.454863+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ014	Bùi Bảo Ngọc	stu_demo_icdh5e_14	\N	stu_demo_icdh5e_14@student.demo.erg.edu.vn	FEMALE	2012-03-15 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0964566913	\N	\N	2025-08-13 09:21:07.367602+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416067.367601800, "completedAssignments": 22}
stu_demo_icdh5e_15	2026-07-13 09:13:32.609054+00	2026-07-13 09:21:07.892044+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ015	Đỗ Tuấn Kiệt	stu_demo_icdh5e_15	\N	stu_demo_icdh5e_15@student.demo.erg.edu.vn	MALE	2012-04-16 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0964566914	\N	\N	2025-07-13 09:21:07.804713+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329667.804712500, "completedAssignments": 23}
stu_demo_icdh5e_16	2026-07-13 09:13:33.036834+00	2026-07-13 09:21:08.326625+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh5e_16	\N	stu_demo_icdh5e_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:21:08.234951+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934468.234951000, "completedAssignments": 8}
stu_demo_icdh5e_17	2026-07-13 09:13:33.462543+00	2026-07-13 09:21:08.769253+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ017	Trần Minh Khang	stu_demo_icdh5e_17	\N	stu_demo_icdh5e_17@student.demo.erg.edu.vn	MALE	2012-06-18 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0964566916	\N	\N	2025-05-13 09:21:08.683178+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848068.683177500, "completedAssignments": 9}
stu_demo_icdh5e_18	2026-07-13 09:13:33.886529+00	2026-07-13 09:21:09.209263+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ018	Lê Khánh Linh	stu_demo_icdh5e_18	\N	stu_demo_icdh5e_18@student.demo.erg.edu.vn	FEMALE	2012-07-19 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0964566917	\N	\N	2026-01-13 09:21:09.115863+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761669.116866900, "completedAssignments": 10}
stu_demo_icdh5e_19	2026-07-13 09:13:34.311435+00	2026-07-13 09:21:09.647712+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ019	Phạm Thảo Vy	stu_demo_icdh5e_19	\N	stu_demo_icdh5e_19@student.demo.erg.edu.vn	MALE	2012-08-20 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0964566918	\N	\N	2025-12-13 09:21:09.561632+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675269.561632400, "completedAssignments": 11}
stu_demo_icdh5e_20	2026-07-13 09:13:34.738555+00	2026-07-13 09:21:10.079064+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ020	Võ Gia Hân	stu_demo_icdh5e_20	\N	stu_demo_icdh5e_20@student.demo.erg.edu.vn	FEMALE	2012-09-21 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0964566940	\N	\N	2025-11-13 09:21:09.993196+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588869.993196100, "completedAssignments": 12}
stu_demo_icdh5e_21	2026-07-13 09:13:35.16126+00	2026-07-13 09:21:10.596457+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh5e_21	\N	stu_demo_icdh5e_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:21:10.479068+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502470.479068200, "completedAssignments": 13}
stu_demo_icdh5e_22	2026-07-13 09:13:35.586424+00	2026-07-13 09:21:11.047693+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ022	Bùi Hoàng Nam	stu_demo_icdh5e_22	\N	stu_demo_icdh5e_22@student.demo.erg.edu.vn	FEMALE	2012-11-23 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0964566942	\N	\N	2025-09-13 09:21:10.96176+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416070.961759800, "completedAssignments": 14}
stu_demo_icdh5e_23	2026-07-13 09:13:36.087123+00	2026-07-13 09:21:11.480178+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ023	Đỗ Ngọc Anh	stu_demo_icdh5e_23	\N	stu_demo_icdh5e_23@student.demo.erg.edu.vn	MALE	2012-12-24 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0964566943	\N	\N	2025-08-13 09:21:11.392901+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329671.392900600, "completedAssignments": 15}
stu_demo_icdh5e_24	2026-07-13 09:13:36.538756+00	2026-07-13 09:21:11.910417+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ024	Nguyễn Bảo Ngọc	stu_demo_icdh5e_24	\N	stu_demo_icdh5e_24@student.demo.erg.edu.vn	FEMALE	2012-01-25 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0964566944	\N	\N	2025-07-13 09:21:11.823436+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934471.823436000, "completedAssignments": 16}
stu_demo_a6ve72_2	2026-07-13 09:13:39.79138+00	2026-07-13 09:21:14.705182+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve72_2	\N	stu_demo_a6ve72_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:21:14.617432+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761674.617432300, "completedAssignments": 10}
stu_demo_a6ve72_3	2026-07-13 09:13:40.21614+00	2026-07-13 09:21:15.136408+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve72_3	\N	stu_demo_a6ve72_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:21:15.049652+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675275.049652000, "completedAssignments": 11}
stu_demo_a6ve72_4	2026-07-13 09:13:40.638663+00	2026-07-13 09:21:15.573231+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve72_4	\N	stu_demo_a6ve72_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:21:15.487605+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588875.487605100, "completedAssignments": 12}
stu_demo_a6ve72_5	2026-07-13 09:13:41.089223+00	2026-07-13 09:21:16.007207+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve72_5	\N	stu_demo_a6ve72_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:21:15.920951+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502475.920951400, "completedAssignments": 13}
stu_demo_a6ve72_6	2026-07-13 09:13:41.517922+00	2026-07-13 09:21:16.441432+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve72_6	\N	stu_demo_a6ve72_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:21:16.35422+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416076.354219900, "completedAssignments": 14}
stu_demo_a6ve72_7	2026-07-13 09:13:41.948557+00	2026-07-13 09:21:16.947454+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve72_7	\N	stu_demo_a6ve72_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:21:16.832197+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329676.832197000, "completedAssignments": 15}
stu_demo_a6ve72_8	2026-07-13 09:13:42.374568+00	2026-07-13 09:21:17.401777+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve72_8	\N	stu_demo_a6ve72_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:21:17.315547+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934477.315547300, "completedAssignments": 16}
stu_demo_a6ve72_9	2026-07-13 09:13:42.805225+00	2026-07-13 09:21:17.836772+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve72_9	\N	stu_demo_a6ve72_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:21:17.750159+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848077.750158500, "completedAssignments": 17}
stu_demo_a6ve72_10	2026-07-13 09:13:43.240372+00	2026-07-13 09:21:18.26732+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve72_10	\N	stu_demo_a6ve72_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:21:18.181075+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761678.181074900, "completedAssignments": 18}
stu_demo_a6ve72_11	2026-07-13 09:13:43.665049+00	2026-07-13 09:21:18.708768+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve72_11	\N	stu_demo_a6ve72_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:21:18.620752+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675278.620751800, "completedAssignments": 19}
stu_demo_a6ve72_12	2026-07-13 09:13:44.099334+00	2026-07-13 09:21:19.144899+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve72_12	\N	stu_demo_a6ve72_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:21:19.053656+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588879.053655500, "completedAssignments": 20}
stu_demo_a6ve72_13	2026-07-13 09:13:44.521854+00	2026-07-13 09:21:19.582899+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve72_13	\N	stu_demo_a6ve72_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:21:19.495358+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502479.495358200, "completedAssignments": 21}
stu_demo_a6ve72_14	2026-07-13 09:13:44.954487+00	2026-07-13 09:21:20.015841+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve72_14	\N	stu_demo_a6ve72_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:21:19.928721+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416079.928720600, "completedAssignments": 22}
stu_demo_a6ve72_15	2026-07-13 09:13:45.377685+00	2026-07-13 09:21:20.449814+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve72_15	\N	stu_demo_a6ve72_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:21:20.362903+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329680.362903400, "completedAssignments": 23}
stu_demo_a6ve72_16	2026-07-13 09:13:45.801555+00	2026-07-13 09:21:20.893596+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve72_16	\N	stu_demo_a6ve72_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:21:20.806647+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934480.806646700, "completedAssignments": 8}
stu_demo_a6ve72_17	2026-07-13 09:13:46.235685+00	2026-07-13 09:21:21.32811+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve72_17	\N	stu_demo_a6ve72_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:21:21.240589+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848081.240589000, "completedAssignments": 9}
stu_demo_a6ve72_18	2026-07-13 09:13:46.681712+00	2026-07-13 09:21:21.761661+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve72_18	\N	stu_demo_a6ve72_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:21:21.675353+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761681.675352500, "completedAssignments": 10}
stu_demo_a6ve72_19	2026-07-13 09:13:47.110054+00	2026-07-13 09:21:22.195369+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve72_19	\N	stu_demo_a6ve72_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:21:22.109159+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675282.109158600, "completedAssignments": 11}
stu_demo_a6ve72_21	2026-07-13 09:13:48.001197+00	2026-07-13 09:21:23.057259+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve72_21	\N	stu_demo_a6ve72_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:21:22.969191+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502482.969191100, "completedAssignments": 13}
stu_demo_a6ve72_22	2026-07-13 09:13:48.431571+00	2026-07-13 09:21:23.544094+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve72_22	\N	stu_demo_a6ve72_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:21:23.45655+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416083.456550100, "completedAssignments": 14}
stu_demo_a6ve72_23	2026-07-13 09:13:48.85522+00	2026-07-13 09:21:24.035102+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve72_23	\N	stu_demo_a6ve72_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:21:23.931821+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329683.931820500, "completedAssignments": 15}
stu_demo_a6ve72_24	2026-07-13 09:13:49.277602+00	2026-07-13 09:21:24.546402+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve72_24	\N	stu_demo_a6ve72_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:21:24.429664+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934484.429664300, "completedAssignments": 16}
stu_demo_a6ve73_1	2026-07-13 09:13:50.129628+00	2026-07-13 09:21:25.445171+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve73_1	\N	stu_demo_a6ve73_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:21:25.343726+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848085.343726200, "completedAssignments": 9}
stu_demo_a6ve73_2	2026-07-13 09:13:50.554225+00	2026-07-13 09:21:25.924505+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve73_2	\N	stu_demo_a6ve73_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:21:25.83107+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761685.831070100, "completedAssignments": 10}
stu_demo_a6ve73_3	2026-07-13 09:13:50.982275+00	2026-07-13 09:21:26.368497+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve73_3	\N	stu_demo_a6ve73_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:21:26.280089+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675286.280088800, "completedAssignments": 11}
stu_demo_a6ve73_4	2026-07-13 09:13:51.40498+00	2026-07-13 09:21:26.80478+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve73_4	\N	stu_demo_a6ve73_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:21:26.717404+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588886.717404300, "completedAssignments": 12}
stu_demo_a6ve73_5	2026-07-13 09:13:51.831105+00	2026-07-13 09:21:27.239923+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve73_5	\N	stu_demo_a6ve73_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:21:27.15203+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502487.152030000, "completedAssignments": 13}
stu_demo_a6ve73_6	2026-07-13 09:13:52.258307+00	2026-07-13 09:21:27.675554+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve73_6	\N	stu_demo_a6ve73_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:21:27.588421+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416087.588420900, "completedAssignments": 14}
stu_demo_a6ve73_7	2026-07-13 09:13:52.688973+00	2026-07-13 09:21:28.119219+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve73_7	\N	stu_demo_a6ve73_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:21:28.032391+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329688.032391200, "completedAssignments": 15}
stu_demo_a6ve73_8	2026-07-13 09:13:53.117124+00	2026-07-13 09:21:28.549022+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve73_8	\N	stu_demo_a6ve73_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:21:28.462197+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934488.462197400, "completedAssignments": 16}
stu_demo_a6ve73_9	2026-07-13 09:13:53.554439+00	2026-07-13 09:21:28.978931+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve73_9	\N	stu_demo_a6ve73_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:21:28.892787+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848088.892787200, "completedAssignments": 17}
stu_demo_a6ve73_10	2026-07-13 09:13:53.987374+00	2026-07-13 09:21:29.409401+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve73_10	\N	stu_demo_a6ve73_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:21:29.321864+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761689.321863800, "completedAssignments": 18}
stu_demo_a6ve73_11	2026-07-13 09:13:54.429744+00	2026-07-13 09:21:29.839805+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve73_11	\N	stu_demo_a6ve73_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:21:29.752668+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675289.752668300, "completedAssignments": 19}
stu_demo_a6ve73_12	2026-07-13 09:13:54.854221+00	2026-07-13 09:21:30.272166+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve73_12	\N	stu_demo_a6ve73_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:21:30.185921+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588890.185921200, "completedAssignments": 20}
stu_demo_a6ve73_13	2026-07-13 09:13:55.282175+00	2026-07-13 09:21:30.706737+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve73_13	\N	stu_demo_a6ve73_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:21:30.620198+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502490.620198400, "completedAssignments": 21}
stu_demo_a6ve73_14	2026-07-13 09:13:55.711015+00	2026-07-13 09:21:31.138618+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve73_14	\N	stu_demo_a6ve73_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:21:31.051655+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416091.051654900, "completedAssignments": 22}
stu_demo_a6ve73_16	2026-07-13 09:13:56.598699+00	2026-07-13 09:21:32.00807+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve73_16	\N	stu_demo_a6ve73_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:21:31.919998+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934491.919998000, "completedAssignments": 8}
stu_demo_a6ve73_17	2026-07-13 09:13:57.025728+00	2026-07-13 09:21:32.439701+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve73_17	\N	stu_demo_a6ve73_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:21:32.352605+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848092.352605200, "completedAssignments": 9}
stu_demo_a6ve73_18	2026-07-13 09:13:57.455106+00	2026-07-13 09:21:32.881267+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve73_18	\N	stu_demo_a6ve73_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:21:32.788513+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761692.788513200, "completedAssignments": 10}
stu_demo_a6ve73_19	2026-07-13 09:13:57.881182+00	2026-07-13 09:21:33.320858+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve73_19	\N	stu_demo_a6ve73_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:21:33.233282+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675293.233282200, "completedAssignments": 11}
stu_demo_a6ve73_20	2026-07-13 09:13:58.301934+00	2026-07-13 09:21:33.752224+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve73_20	\N	stu_demo_a6ve73_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:21:33.664397+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588893.664397300, "completedAssignments": 12}
stu_demo_a6ve73_21	2026-07-13 09:13:58.731192+00	2026-07-13 09:21:34.323344+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve73_21	\N	stu_demo_a6ve73_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:21:34.235962+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502494.235962300, "completedAssignments": 13}
stu_demo_a6ve73_22	2026-07-13 09:13:59.159871+00	2026-07-13 09:21:34.763349+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve73_22	\N	stu_demo_a6ve73_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:21:34.669883+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416094.669882900, "completedAssignments": 14}
stu_demo_a6ve73_23	2026-07-13 09:13:59.582465+00	2026-07-13 09:21:35.197325+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve73_23	\N	stu_demo_a6ve73_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:21:35.110077+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329695.110077400, "completedAssignments": 15}
stu_demo_a6ve73_24	2026-07-13 09:14:00.004265+00	2026-07-13 09:21:35.631463+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve73_24	\N	stu_demo_a6ve73_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:21:35.544352+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934495.544351600, "completedAssignments": 16}
stu_demo_a6ve7x_1	2026-07-13 09:14:00.871051+00	2026-07-13 09:21:36.40771+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve7x_1	\N	stu_demo_a6ve7x_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:21:36.320265+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848096.320265100, "completedAssignments": 9}
stu_demo_a6ve7x_2	2026-07-13 09:14:01.296391+00	2026-07-13 09:21:36.840373+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve7x_2	\N	stu_demo_a6ve7x_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:21:36.75404+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761696.754039800, "completedAssignments": 10}
stu_demo_a6ve7x_3	2026-07-13 09:14:01.719511+00	2026-07-13 09:21:37.273414+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve7x_3	\N	stu_demo_a6ve7x_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:21:37.184714+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675297.184713600, "completedAssignments": 11}
stu_demo_a6ve7x_4	2026-07-13 09:14:02.147465+00	2026-07-13 09:21:37.708533+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve7x_4	\N	stu_demo_a6ve7x_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:21:37.619889+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588897.619889000, "completedAssignments": 12}
stu_demo_a6ve7x_5	2026-07-13 09:14:02.575068+00	2026-07-13 09:21:38.160894+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve7x_5	\N	stu_demo_a6ve7x_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:21:38.059617+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502498.059617300, "completedAssignments": 13}
stu_demo_a6ve7x_6	2026-07-13 09:14:02.999483+00	2026-07-13 09:21:38.596306+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve7x_6	\N	stu_demo_a6ve7x_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:21:38.508758+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416098.508757600, "completedAssignments": 14}
stu_demo_a6ve7x_7	2026-07-13 09:14:03.424093+00	2026-07-13 09:21:39.031537+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve7x_7	\N	stu_demo_a6ve7x_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:21:38.942012+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329698.942012400, "completedAssignments": 15}
stu_demo_a6ve7x_8	2026-07-13 09:14:03.861206+00	2026-07-13 09:21:39.463936+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve7x_8	\N	stu_demo_a6ve7x_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:21:39.376703+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934499.376703200, "completedAssignments": 16}
stu_demo_a6ve7x_9	2026-07-13 09:14:04.288285+00	2026-07-13 09:21:39.92538+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve7x_9	\N	stu_demo_a6ve7x_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:21:39.833595+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848099.833594700, "completedAssignments": 17}
stu_demo_a6ve7x_11	2026-07-13 09:14:05.149144+00	2026-07-13 09:21:40.784894+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve7x_11	\N	stu_demo_a6ve7x_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:21:40.698785+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675300.698785200, "completedAssignments": 19}
stu_demo_a6ve7x_12	2026-07-13 09:14:05.583607+00	2026-07-13 09:21:41.21585+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve7x_12	\N	stu_demo_a6ve7x_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:21:41.129378+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588901.129378100, "completedAssignments": 20}
stu_demo_a6ve7x_13	2026-07-13 09:14:06.07426+00	2026-07-13 09:21:41.645541+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve7x_13	\N	stu_demo_a6ve7x_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:21:41.559336+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502501.559335700, "completedAssignments": 21}
stu_demo_a6ve7x_14	2026-07-13 09:14:06.504037+00	2026-07-13 09:21:42.089548+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve7x_14	\N	stu_demo_a6ve7x_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:21:41.997855+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416101.997855200, "completedAssignments": 22}
stu_demo_a6ve7x_15	2026-07-13 09:14:06.942221+00	2026-07-13 09:21:42.560478+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve7x_15	\N	stu_demo_a6ve7x_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:21:42.470517+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329702.470516700, "completedAssignments": 23}
stu_demo_a6ve7x_16	2026-07-13 09:14:07.366988+00	2026-07-13 09:21:43.000233+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve7x_16	\N	stu_demo_a6ve7x_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:21:42.911604+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934502.912605500, "completedAssignments": 8}
stu_demo_a6ve7x_17	2026-07-13 09:14:07.791823+00	2026-07-13 09:21:43.435291+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve7x_17	\N	stu_demo_a6ve7x_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:21:43.348525+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848103.348525200, "completedAssignments": 9}
stu_demo_a6ve7x_18	2026-07-13 09:14:08.215819+00	2026-07-13 09:21:43.878147+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve7x_18	\N	stu_demo_a6ve7x_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:21:43.790807+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761703.790806900, "completedAssignments": 10}
stu_demo_a6ve7x_19	2026-07-13 09:14:08.640469+00	2026-07-13 09:21:44.312629+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve7x_19	\N	stu_demo_a6ve7x_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:21:44.224554+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675304.224554100, "completedAssignments": 11}
stu_demo_a6ve7x_20	2026-07-13 09:14:09.085955+00	2026-07-13 09:21:44.793+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve7x_20	\N	stu_demo_a6ve7x_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:21:44.678402+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588904.678402200, "completedAssignments": 12}
stu_demo_a6ve7x_21	2026-07-13 09:14:09.512144+00	2026-07-13 09:21:45.246411+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve7x_21	\N	stu_demo_a6ve7x_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:21:45.15748+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502505.157480400, "completedAssignments": 13}
stu_demo_a6ve7x_22	2026-07-13 09:14:09.936539+00	2026-07-13 09:21:45.686054+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve7x_22	\N	stu_demo_a6ve7x_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:21:45.596442+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416105.596441500, "completedAssignments": 14}
stu_demo_a6ve7x_23	2026-07-13 09:14:10.36157+00	2026-07-13 09:21:46.133934+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve7x_23	\N	stu_demo_a6ve7x_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:21:46.047314+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329706.047313700, "completedAssignments": 15}
stu_demo_a6ve7x_24	2026-07-13 09:14:10.789345+00	2026-07-13 09:21:46.584696+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve7x_24	\N	stu_demo_a6ve7x_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:21:46.49821+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934506.498210100, "completedAssignments": 16}
stu_demo_a6ve7y_1	2026-07-13 09:14:11.64357+00	2026-07-13 09:21:47.369771+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve7y_1	\N	stu_demo_a6ve7y_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:21:47.283452+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848107.283452000, "completedAssignments": 9}
stu_demo_a6ve7y_2	2026-07-13 09:14:12.072031+00	2026-07-13 09:21:47.802973+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve7y_2	\N	stu_demo_a6ve7y_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:21:47.712386+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761707.712385700, "completedAssignments": 10}
stu_demo_a6ve7y_3	2026-07-13 09:14:12.50157+00	2026-07-13 09:21:48.233586+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve7y_3	\N	stu_demo_a6ve7y_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:21:48.147707+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675308.147706800, "completedAssignments": 11}
stu_demo_a6ve7y_4	2026-07-13 09:14:12.928154+00	2026-07-13 09:21:48.663115+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve7y_4	\N	stu_demo_a6ve7y_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:21:48.57715+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588908.577150200, "completedAssignments": 12}
stu_demo_a6ve7y_6	2026-07-13 09:14:13.782006+00	2026-07-13 09:21:49.525944+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve7y_6	\N	stu_demo_a6ve7y_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:21:49.439365+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416109.439365300, "completedAssignments": 14}
stu_demo_a6ve7y_7	2026-07-13 09:14:14.21586+00	2026-07-13 09:21:49.963029+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve7y_7	\N	stu_demo_a6ve7y_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:21:49.875817+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329709.875816800, "completedAssignments": 15}
stu_demo_a6ve7y_8	2026-07-13 09:14:14.645682+00	2026-07-13 09:21:50.410493+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve7y_8	\N	stu_demo_a6ve7y_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:21:50.313279+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934510.313278800, "completedAssignments": 16}
stu_demo_a6ve7y_9	2026-07-13 09:14:15.084074+00	2026-07-13 09:21:50.854124+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve7y_9	\N	stu_demo_a6ve7y_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:21:50.767751+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848110.767751300, "completedAssignments": 17}
stu_demo_a6ve7y_10	2026-07-13 09:14:15.508708+00	2026-07-13 09:21:51.285604+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve7y_10	\N	stu_demo_a6ve7y_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:21:51.199104+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761711.199104000, "completedAssignments": 18}
stu_demo_a6ve7y_11	2026-07-13 09:14:15.93517+00	2026-07-13 09:21:51.716149+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve7y_11	\N	stu_demo_a6ve7y_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:21:51.629618+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675311.629618000, "completedAssignments": 19}
stu_demo_a6ve7y_12	2026-07-13 09:14:16.35993+00	2026-07-13 09:21:52.146154+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve7y_12	\N	stu_demo_a6ve7y_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:21:52.059293+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588912.059293000, "completedAssignments": 20}
stu_demo_a6ve7y_13	2026-07-13 09:14:16.782056+00	2026-07-13 09:21:52.575688+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve7y_13	\N	stu_demo_a6ve7y_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:21:52.489486+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502512.489485900, "completedAssignments": 21}
stu_demo_a6ve7y_14	2026-07-13 09:14:17.207541+00	2026-07-13 09:21:53.006479+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve7y_14	\N	stu_demo_a6ve7y_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:21:52.91934+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416112.919339500, "completedAssignments": 22}
stu_demo_a6ve7y_15	2026-07-13 09:14:17.63223+00	2026-07-13 09:21:53.437139+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve7y_15	\N	stu_demo_a6ve7y_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:21:53.350506+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329713.350505800, "completedAssignments": 23}
stu_demo_a6ve7y_16	2026-07-13 09:14:18.055032+00	2026-07-13 09:21:53.867315+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve7y_16	\N	stu_demo_a6ve7y_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:21:53.780486+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934513.780486300, "completedAssignments": 8}
stu_demo_a6ve7y_17	2026-07-13 09:14:18.482382+00	2026-07-13 09:21:54.301692+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve7y_17	\N	stu_demo_a6ve7y_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:21:54.21554+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848114.215540000, "completedAssignments": 9}
stu_demo_a6ve7y_18	2026-07-13 09:14:18.907332+00	2026-07-13 09:21:54.730693+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve7y_18	\N	stu_demo_a6ve7y_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:21:54.645193+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761714.645192700, "completedAssignments": 10}
stu_demo_a6ve7y_19	2026-07-13 09:14:19.340539+00	2026-07-13 09:21:55.161056+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve7y_19	\N	stu_demo_a6ve7y_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:21:55.074472+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675315.074472200, "completedAssignments": 11}
stu_demo_a6ve7y_20	2026-07-13 09:14:19.790703+00	2026-07-13 09:21:55.59283+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve7y_20	\N	stu_demo_a6ve7y_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:21:55.507485+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588915.507485000, "completedAssignments": 12}
stu_demo_a6ve7y_21	2026-07-13 09:14:20.219058+00	2026-07-13 09:21:56.035815+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve7y_21	\N	stu_demo_a6ve7y_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:21:55.948169+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502515.948169000, "completedAssignments": 13}
stu_demo_a6ve7y_22	2026-07-13 09:14:20.645094+00	2026-07-13 09:21:56.490183+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve7y_22	\N	stu_demo_a6ve7y_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:21:56.402659+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416116.402659200, "completedAssignments": 14}
stu_demo_a6ve7y_23	2026-07-13 09:14:21.074157+00	2026-07-13 09:21:56.924121+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve7y_23	\N	stu_demo_a6ve7y_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:21:56.833327+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329716.833327000, "completedAssignments": 15}
stu_demo_a6ve8s_1	2026-07-13 09:14:22.350177+00	2026-07-13 09:21:58.181321+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve8s_1	\N	stu_demo_a6ve8s_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:21:58.094406+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848118.094406300, "completedAssignments": 9}
stu_demo_a6ve8s_2	2026-07-13 09:14:22.776849+00	2026-07-13 09:21:58.616827+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve8s_2	\N	stu_demo_a6ve8s_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:21:58.528363+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761718.528363000, "completedAssignments": 10}
stu_demo_a6ve8s_3	2026-07-13 09:14:23.202061+00	2026-07-13 09:21:59.071282+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve8s_3	\N	stu_demo_a6ve8s_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:21:58.982411+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675318.982410800, "completedAssignments": 11}
stu_demo_a6ve8s_4	2026-07-13 09:14:23.625252+00	2026-07-13 09:21:59.509743+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve8s_4	\N	stu_demo_a6ve8s_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:21:59.422128+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588919.422128000, "completedAssignments": 12}
stu_demo_a6ve8s_5	2026-07-13 09:14:24.048682+00	2026-07-13 09:21:59.942593+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve8s_5	\N	stu_demo_a6ve8s_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:21:59.854994+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502519.854994300, "completedAssignments": 13}
stu_demo_a6ve8s_6	2026-07-13 09:14:24.477706+00	2026-07-13 09:22:00.376577+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve8s_6	\N	stu_demo_a6ve8s_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:22:00.289246+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416120.289245800, "completedAssignments": 14}
stu_demo_a6ve8s_7	2026-07-13 09:14:24.916274+00	2026-07-13 09:22:00.806705+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve8s_7	\N	stu_demo_a6ve8s_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:22:00.72+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329720.720000200, "completedAssignments": 15}
stu_demo_a6ve8s_8	2026-07-13 09:14:25.345228+00	2026-07-13 09:22:01.236761+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve8s_8	\N	stu_demo_a6ve8s_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:22:01.15072+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934521.150720400, "completedAssignments": 16}
stu_demo_a6ve8s_9	2026-07-13 09:14:25.770396+00	2026-07-13 09:22:01.667247+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve8s_9	\N	stu_demo_a6ve8s_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:22:01.5815+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848121.581499700, "completedAssignments": 17}
stu_demo_a6ve8s_10	2026-07-13 09:14:26.195309+00	2026-07-13 09:22:02.096906+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve8s_10	\N	stu_demo_a6ve8s_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:22:02.010228+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761722.010227700, "completedAssignments": 18}
stu_demo_a6ve8s_11	2026-07-13 09:14:26.621016+00	2026-07-13 09:22:02.530517+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve8s_11	\N	stu_demo_a6ve8s_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:22:02.439367+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675322.440372700, "completedAssignments": 19}
stu_demo_a6ve8s_12	2026-07-13 09:14:27.053399+00	2026-07-13 09:22:02.964025+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve8s_12	\N	stu_demo_a6ve8s_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:22:02.877846+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588922.877845700, "completedAssignments": 20}
stu_demo_a6ve8s_13	2026-07-13 09:14:27.47775+00	2026-07-13 09:22:03.395209+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve8s_13	\N	stu_demo_a6ve8s_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:22:03.309275+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502523.309274600, "completedAssignments": 21}
stu_demo_a6ve8s_14	2026-07-13 09:14:27.903456+00	2026-07-13 09:22:03.824863+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve8s_14	\N	stu_demo_a6ve8s_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:22:03.73878+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416123.738779900, "completedAssignments": 22}
stu_demo_a6ve8s_15	2026-07-13 09:14:28.332073+00	2026-07-13 09:22:04.254918+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve8s_15	\N	stu_demo_a6ve8s_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:22:04.167854+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329724.167854200, "completedAssignments": 23}
stu_demo_a6ve8s_16	2026-07-13 09:14:28.757063+00	2026-07-13 09:22:04.684697+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve8s_16	\N	stu_demo_a6ve8s_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:22:04.598139+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934524.598139000, "completedAssignments": 8}
stu_demo_a6ve8s_17	2026-07-13 09:14:29.187185+00	2026-07-13 09:22:05.115726+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve8s_17	\N	stu_demo_a6ve8s_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:22:05.027584+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848125.027584300, "completedAssignments": 9}
stu_demo_a6ve8s_18	2026-07-13 09:14:29.610806+00	2026-07-13 09:22:05.562548+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve8s_18	\N	stu_demo_a6ve8s_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:22:05.476097+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761725.476097300, "completedAssignments": 10}
stu_demo_a6ve8s_20	2026-07-13 09:14:30.459918+00	2026-07-13 09:22:06.422696+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve8s_20	\N	stu_demo_a6ve8s_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:22:06.336385+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588926.336384800, "completedAssignments": 12}
stu_demo_a6ve8s_21	2026-07-13 09:14:30.886284+00	2026-07-13 09:22:06.851328+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve8s_21	\N	stu_demo_a6ve8s_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:22:06.764502+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502526.764501700, "completedAssignments": 13}
stu_demo_a6ve8s_22	2026-07-13 09:14:31.312336+00	2026-07-13 09:22:07.281231+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve8s_22	\N	stu_demo_a6ve8s_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:22:07.195393+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416127.195393300, "completedAssignments": 14}
stu_demo_a6ve8s_23	2026-07-13 09:14:31.738331+00	2026-07-13 09:22:07.710933+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve8s_23	\N	stu_demo_a6ve8s_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:22:07.624114+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329727.624113600, "completedAssignments": 15}
stu_demo_a6ve8s_24	2026-07-13 09:14:32.167729+00	2026-07-13 09:22:08.148609+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve8s_24	\N	stu_demo_a6ve8s_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:22:08.062615+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934528.062615400, "completedAssignments": 16}
stu_demo_a6ve8t_1	2026-07-13 09:14:33.02593+00	2026-07-13 09:22:08.923805+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve8t_1	\N	stu_demo_a6ve8t_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:22:08.837473+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848128.837472900, "completedAssignments": 9}
stu_demo_a6ve8t_2	2026-07-13 09:14:33.460987+00	2026-07-13 09:22:09.355007+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve8t_2	\N	stu_demo_a6ve8t_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:22:09.267861+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761729.267861400, "completedAssignments": 10}
stu_demo_a6ve8t_3	2026-07-13 09:14:33.885304+00	2026-07-13 09:22:09.784795+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve8t_3	\N	stu_demo_a6ve8t_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:22:09.69944+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675329.699440400, "completedAssignments": 11}
stu_demo_a6ve8t_4	2026-07-13 09:14:34.307532+00	2026-07-13 09:22:10.215922+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve8t_4	\N	stu_demo_a6ve8t_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:22:10.128559+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588930.128558700, "completedAssignments": 12}
stu_demo_a6ve8t_5	2026-07-13 09:14:34.732078+00	2026-07-13 09:22:10.647118+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve8t_5	\N	stu_demo_a6ve8t_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:22:10.561899+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502530.561899100, "completedAssignments": 13}
stu_demo_a6ve8t_6	2026-07-13 09:14:35.15574+00	2026-07-13 09:22:11.077656+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve8t_6	\N	stu_demo_a6ve8t_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:22:10.990492+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416130.990492200, "completedAssignments": 14}
stu_demo_a6ve8t_7	2026-07-13 09:14:35.593319+00	2026-07-13 09:22:11.506937+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve8t_7	\N	stu_demo_a6ve8t_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:22:11.420321+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329731.420321300, "completedAssignments": 15}
stu_demo_a6ve8t_8	2026-07-13 09:14:36.022358+00	2026-07-13 09:22:11.946045+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve8t_8	\N	stu_demo_a6ve8t_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:22:11.86095+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934531.860950300, "completedAssignments": 16}
stu_demo_a6ve8t_9	2026-07-13 09:14:36.44619+00	2026-07-13 09:22:12.374584+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve8t_9	\N	stu_demo_a6ve8t_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:22:12.289811+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848132.289811000, "completedAssignments": 17}
stu_demo_a6ve8t_10	2026-07-13 09:14:36.884294+00	2026-07-13 09:22:12.804373+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve8t_10	\N	stu_demo_a6ve8t_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:22:12.718709+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761732.718708700, "completedAssignments": 18}
stu_demo_a6ve8t_11	2026-07-13 09:14:37.31075+00	2026-07-13 09:22:13.234318+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve8t_11	\N	stu_demo_a6ve8t_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:22:13.1482+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675333.148199900, "completedAssignments": 19}
stu_demo_a6ve8t_12	2026-07-13 09:14:37.73339+00	2026-07-13 09:22:13.667213+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve8t_12	\N	stu_demo_a6ve8t_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:22:13.579496+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588933.579495800, "completedAssignments": 20}
stu_demo_a6ve8t_13	2026-07-13 09:14:38.157383+00	2026-07-13 09:22:14.118536+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve8t_13	\N	stu_demo_a6ve8t_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:22:14.024228+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502534.024227800, "completedAssignments": 21}
stu_demo_a6ve8t_15	2026-07-13 09:14:39.008786+00	2026-07-13 09:22:14.977423+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve8t_15	\N	stu_demo_a6ve8t_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:22:14.891309+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329734.891308700, "completedAssignments": 23}
stu_demo_a6ve8t_16	2026-07-13 09:14:39.432738+00	2026-07-13 09:22:15.407503+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve8t_16	\N	stu_demo_a6ve8t_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:22:15.320889+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934535.320889200, "completedAssignments": 8}
stu_demo_a6ve8t_17	2026-07-13 09:14:39.854442+00	2026-07-13 09:22:15.837624+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve8t_17	\N	stu_demo_a6ve8t_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:22:15.751575+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848135.751575200, "completedAssignments": 9}
stu_demo_a6ve8t_18	2026-07-13 09:14:40.277736+00	2026-07-13 09:22:16.267823+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve8t_18	\N	stu_demo_a6ve8t_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:22:16.18235+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761736.182349600, "completedAssignments": 10}
stu_demo_a6ve8t_19	2026-07-13 09:14:40.706192+00	2026-07-13 09:22:16.718362+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve8t_19	\N	stu_demo_a6ve8t_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:22:16.631299+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675336.631299000, "completedAssignments": 11}
stu_demo_a6ve8t_20	2026-07-13 09:14:41.128149+00	2026-07-13 09:22:17.151207+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve8t_20	\N	stu_demo_a6ve8t_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:22:17.064098+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588937.064097800, "completedAssignments": 12}
stu_demo_a6ve8t_21	2026-07-13 09:14:41.551256+00	2026-07-13 09:22:17.580835+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve8t_21	\N	stu_demo_a6ve8t_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:22:17.494027+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502537.494026800, "completedAssignments": 13}
stu_demo_a6ve8t_22	2026-07-13 09:14:41.975199+00	2026-07-13 09:22:18.011909+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve8t_22	\N	stu_demo_a6ve8t_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:22:17.92521+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416137.925209800, "completedAssignments": 14}
stu_demo_a6ve8t_23	2026-07-13 09:14:42.417856+00	2026-07-13 09:22:18.443617+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve8t_23	\N	stu_demo_a6ve8t_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:22:18.357158+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329738.357157800, "completedAssignments": 15}
stu_demo_a6ve8t_24	2026-07-13 09:14:42.840371+00	2026-07-13 09:22:18.877419+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve8t_24	\N	stu_demo_a6ve8t_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:22:18.788618+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934538.788617500, "completedAssignments": 16}
stu_demo_a6ve9n_1	2026-07-13 09:14:43.687285+00	2026-07-13 09:22:19.655983+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve9n_1	\N	stu_demo_a6ve9n_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:22:19.568976+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848139.568975600, "completedAssignments": 9}
stu_demo_a6ve9n_2	2026-07-13 09:14:44.118475+00	2026-07-13 09:22:20.092442+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve9n_2	\N	stu_demo_a6ve9n_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:22:20.004331+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761740.004330700, "completedAssignments": 10}
stu_demo_a6ve9n_3	2026-07-13 09:14:44.547324+00	2026-07-13 09:22:20.525953+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve9n_3	\N	stu_demo_a6ve9n_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:22:20.439182+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675340.439181600, "completedAssignments": 11}
stu_demo_a6ve9n_4	2026-07-13 09:14:44.97015+00	2026-07-13 09:22:20.956189+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve9n_4	\N	stu_demo_a6ve9n_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:22:20.869939+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588940.869939100, "completedAssignments": 12}
stu_demo_a6ve9n_5	2026-07-13 09:14:45.394125+00	2026-07-13 09:22:21.386605+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve9n_5	\N	stu_demo_a6ve9n_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:22:21.300863+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502541.300862500, "completedAssignments": 13}
stu_demo_a6ve9n_6	2026-07-13 09:14:45.818504+00	2026-07-13 09:22:21.815785+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve9n_6	\N	stu_demo_a6ve9n_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:22:21.729306+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416141.729305600, "completedAssignments": 14}
stu_demo_a6ve9n_7	2026-07-13 09:14:46.239729+00	2026-07-13 09:22:22.244806+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve9n_7	\N	stu_demo_a6ve9n_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:22:22.158007+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329742.158006500, "completedAssignments": 15}
stu_demo_a6ve9n_8	2026-07-13 09:14:46.664028+00	2026-07-13 09:22:22.672871+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve9n_8	\N	stu_demo_a6ve9n_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:22:22.58657+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934542.586570300, "completedAssignments": 16}
stu_demo_a6ve9n_10	2026-07-13 09:14:47.510627+00	2026-07-13 09:22:23.54916+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve9n_10	\N	stu_demo_a6ve9n_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:22:23.462475+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761743.462474500, "completedAssignments": 18}
stu_demo_a6ve9n_11	2026-07-13 09:14:47.938657+00	2026-07-13 09:22:24.010088+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve9n_11	\N	stu_demo_a6ve9n_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:22:23.922387+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675343.922387300, "completedAssignments": 19}
stu_demo_a6ve9n_12	2026-07-13 09:14:48.36198+00	2026-07-13 09:22:24.458891+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve9n_12	\N	stu_demo_a6ve9n_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:22:24.371274+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588944.371273700, "completedAssignments": 20}
stu_demo_a6ve9n_13	2026-07-13 09:14:48.78862+00	2026-07-13 09:22:24.901559+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve9n_13	\N	stu_demo_a6ve9n_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:22:24.812597+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502544.812596600, "completedAssignments": 21}
stu_demo_a6ve9n_14	2026-07-13 09:14:49.211818+00	2026-07-13 09:22:25.337203+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve9n_14	\N	stu_demo_a6ve9n_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:22:25.248036+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416145.248036000, "completedAssignments": 22}
stu_demo_a6ve9n_15	2026-07-13 09:14:49.635016+00	2026-07-13 09:22:25.775256+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve9n_15	\N	stu_demo_a6ve9n_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:22:25.680881+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329745.680881200, "completedAssignments": 23}
stu_demo_a6ve9n_16	2026-07-13 09:14:50.068577+00	2026-07-13 09:22:26.204855+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve9n_16	\N	stu_demo_a6ve9n_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:22:26.118236+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934546.118236000, "completedAssignments": 8}
stu_demo_a6ve9n_17	2026-07-13 09:14:50.495996+00	2026-07-13 09:22:26.637168+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve9n_17	\N	stu_demo_a6ve9n_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:22:26.550261+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848146.550261100, "completedAssignments": 9}
stu_demo_a6ve9n_18	2026-07-13 09:14:50.924012+00	2026-07-13 09:22:27.067563+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve9n_18	\N	stu_demo_a6ve9n_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:22:26.981664+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761746.981663600, "completedAssignments": 10}
stu_demo_a6ve9n_19	2026-07-13 09:14:51.354098+00	2026-07-13 09:22:27.500596+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve9n_19	\N	stu_demo_a6ve9n_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:22:27.413471+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675347.413471200, "completedAssignments": 11}
stu_demo_a6ve9n_20	2026-07-13 09:14:51.779237+00	2026-07-13 09:22:27.935947+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve9n_20	\N	stu_demo_a6ve9n_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:22:27.848767+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588947.848767100, "completedAssignments": 12}
stu_demo_a6ve9n_21	2026-07-13 09:14:52.210996+00	2026-07-13 09:22:28.383942+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve9n_21	\N	stu_demo_a6ve9n_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:22:28.283666+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502548.283666000, "completedAssignments": 13}
stu_demo_a6ve9n_22	2026-07-13 09:14:52.642236+00	2026-07-13 09:22:28.8166+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve9n_22	\N	stu_demo_a6ve9n_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:22:28.729509+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416148.729508700, "completedAssignments": 14}
stu_demo_a6ve9n_23	2026-07-13 09:14:53.065982+00	2026-07-13 09:22:29.252844+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve9n_23	\N	stu_demo_a6ve9n_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:22:29.166297+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329749.166296700, "completedAssignments": 15}
stu_demo_a6ve9n_24	2026-07-13 09:14:53.499088+00	2026-07-13 09:22:29.699695+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve9n_24	\N	stu_demo_a6ve9n_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:22:29.613083+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934549.613082900, "completedAssignments": 16}
stu_demo_a6ve9o_1	2026-07-13 09:14:54.376438+00	2026-07-13 09:22:30.473991+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve9o_1	\N	stu_demo_a6ve9o_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:22:30.387868+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848150.387867600, "completedAssignments": 9}
stu_demo_a6ve9o_2	2026-07-13 09:14:54.815799+00	2026-07-13 09:22:30.906291+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK002	Phạm Thảo Vy	stu_demo_a6ve9o_2	\N	stu_demo_a6ve9o_2@student.demo.erg.edu.vn	FEMALE	2011-03-03 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0997299842	\N	\N	2025-11-13 09:22:30.820445+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761750.820444700, "completedAssignments": 10}
stu_demo_a6ve9o_3	2026-07-13 09:14:55.24963+00	2026-07-13 09:22:31.340814+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK003	Võ Gia Hân	stu_demo_a6ve9o_3	\N	stu_demo_a6ve9o_3@student.demo.erg.edu.vn	MALE	2011-04-04 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0997299843	\N	\N	2025-10-13 09:22:31.249465+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675351.250468600, "completedAssignments": 11}
stu_demo_a6ve9o_5	2026-07-13 09:14:56.123596+00	2026-07-13 09:22:32.204393+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve9o_5	\N	stu_demo_a6ve9o_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:22:32.117774+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502552.117774200, "completedAssignments": 13}
stu_demo_a6ve9o_6	2026-07-13 09:14:56.547954+00	2026-07-13 09:22:32.63846+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK006	Đỗ Ngọc Anh	stu_demo_a6ve9o_6	\N	stu_demo_a6ve9o_6@student.demo.erg.edu.vn	FEMALE	2011-07-07 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0997299846	\N	\N	2025-07-13 09:22:32.547751+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416152.547750700, "completedAssignments": 14}
stu_demo_a6ve9o_7	2026-07-13 09:14:56.974305+00	2026-07-13 09:22:33.07134+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK007	Nguyễn Bảo Ngọc	stu_demo_a6ve9o_7	\N	stu_demo_a6ve9o_7@student.demo.erg.edu.vn	MALE	2011-08-08 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0997299847	\N	\N	2025-06-13 09:22:32.985748+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329752.985747600, "completedAssignments": 15}
stu_demo_a6ve9o_8	2026-07-13 09:14:57.405948+00	2026-07-13 09:22:33.501194+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK008	Trần Tuấn Kiệt	stu_demo_a6ve9o_8	\N	stu_demo_a6ve9o_8@student.demo.erg.edu.vn	FEMALE	2011-09-09 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0997299848	\N	\N	2025-05-13 09:22:33.415034+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934553.415034000, "completedAssignments": 16}
stu_demo_a6ve9o_9	2026-07-13 09:14:57.847563+00	2026-07-13 09:22:33.931219+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve9o_9	\N	stu_demo_a6ve9o_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:22:33.844507+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848153.844507100, "completedAssignments": 17}
stu_demo_a6ve9o_10	2026-07-13 09:14:58.277454+00	2026-07-13 09:22:34.361293+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve9o_10	\N	stu_demo_a6ve9o_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:22:34.273855+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761754.273855000, "completedAssignments": 18}
stu_demo_a6ve9o_11	2026-07-13 09:14:58.71108+00	2026-07-13 09:22:34.792422+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK011	Võ Khánh Linh	stu_demo_a6ve9o_11	\N	stu_demo_a6ve9o_11@student.demo.erg.edu.vn	MALE	2011-12-12 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0928345136	\N	\N	2025-11-13 09:22:34.705135+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675354.705134800, "completedAssignments": 19}
stu_demo_a6ve9o_12	2026-07-13 09:14:59.139933+00	2026-07-13 09:22:35.223601+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK012	Đặng Thảo Vy	stu_demo_a6ve9o_12	\N	stu_demo_a6ve9o_12@student.demo.erg.edu.vn	FEMALE	2011-01-13 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0928345135	\N	\N	2025-10-13 09:22:35.137189+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588955.137189300, "completedAssignments": 20}
stu_demo_a6ve9o_13	2026-07-13 09:14:59.56706+00	2026-07-13 09:22:35.659922+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK013	Bùi Gia Hân	stu_demo_a6ve9o_13	\N	stu_demo_a6ve9o_13@student.demo.erg.edu.vn	MALE	2011-02-14 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0928345134	\N	\N	2025-09-13 09:22:35.570345+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502555.570344700, "completedAssignments": 21}
stu_demo_a6ve9o_14	2026-07-13 09:15:00.001285+00	2026-07-13 09:22:36.153814+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve9o_14	\N	stu_demo_a6ve9o_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:22:36.065133+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416156.065132900, "completedAssignments": 22}
stu_demo_a6ve9o_15	2026-07-13 09:15:00.432001+00	2026-07-13 09:22:36.630512+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve9o_15	\N	stu_demo_a6ve9o_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:22:36.502796+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329756.502795700, "completedAssignments": 23}
stu_demo_a6ve9o_16	2026-07-13 09:15:00.868183+00	2026-07-13 09:22:37.066545+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK016	Trần Ngọc Anh	stu_demo_a6ve9o_16	\N	stu_demo_a6ve9o_16@student.demo.erg.edu.vn	FEMALE	2011-05-17 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0928345131	\N	\N	2025-06-13 09:22:36.978388+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934556.978387500, "completedAssignments": 8}
stu_demo_a6ve9o_17	2026-07-13 09:15:01.292167+00	2026-07-13 09:22:37.499552+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK017	Lê Bảo Ngọc	stu_demo_a6ve9o_17	\N	stu_demo_a6ve9o_17@student.demo.erg.edu.vn	MALE	2011-06-18 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0928345130	\N	\N	2025-05-13 09:22:37.412641+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848157.412640900, "completedAssignments": 9}
stu_demo_a6ve9o_18	2026-07-13 09:15:01.723385+00	2026-07-13 09:22:37.929011+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK018	Phạm Tuấn Kiệt	stu_demo_a6ve9o_18	\N	stu_demo_a6ve9o_18@student.demo.erg.edu.vn	FEMALE	2011-07-19 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0928345129	\N	\N	2026-01-13 09:22:37.843655+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761757.843654800, "completedAssignments": 10}
stu_demo_a6ve9o_19	2026-07-13 09:15:02.16191+00	2026-07-13 09:22:38.361731+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve9o_19	\N	stu_demo_a6ve9o_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:22:38.275356+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675358.275356300, "completedAssignments": 11}
stu_demo_a6ve9o_20	2026-07-13 09:15:02.588994+00	2026-07-13 09:22:38.792507+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve9o_20	\N	stu_demo_a6ve9o_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:22:38.706463+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588958.706463300, "completedAssignments": 12}
stu_demo_a6ve9o_21	2026-07-13 09:15:03.026952+00	2026-07-13 09:22:39.226758+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK021	Bùi Khánh Linh	stu_demo_a6ve9o_21	\N	stu_demo_a6ve9o_21@student.demo.erg.edu.vn	MALE	2011-10-22 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0928345105	\N	\N	2025-10-13 09:22:39.141023+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502559.141022900, "completedAssignments": 13}
stu_demo_a6ve9o_22	2026-07-13 09:15:03.456205+00	2026-07-13 09:22:39.657837+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK022	Đỗ Thảo Vy	stu_demo_a6ve9o_22	\N	stu_demo_a6ve9o_22@student.demo.erg.edu.vn	FEMALE	2011-11-23 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0928345104	\N	\N	2025-09-13 09:22:39.571471+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416159.571471200, "completedAssignments": 14}
stu_demo_a6ve9o_24	2026-07-13 09:15:04.312205+00	2026-07-13 09:22:40.520699+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve9o_24	\N	stu_demo_a6ve9o_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:22:40.435035+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934560.435034600, "completedAssignments": 16}
stu_demo_108go9o_1	2026-07-13 09:15:07.137533+00	2026-07-13 09:22:42.887354+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC001	Phạm Tuấn Kiệt	stu_demo_108go9o_1	\N	stu_demo_108go9o_1@student.demo.erg.edu.vn	MALE	2010-02-02 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0946511699	\N	\N	2025-12-13 09:22:42.799307+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848162.799306600, "completedAssignments": 9}
stu_demo_108go9o_2	2026-07-13 09:15:07.565857+00	2026-07-13 09:22:43.323865+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC002	Võ Quang Huy	stu_demo_108go9o_2	\N	stu_demo_108go9o_2@student.demo.erg.edu.vn	FEMALE	2010-03-03 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0946511698	\N	\N	2025-11-13 09:22:43.238645+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761763.238645000, "completedAssignments": 10}
stu_demo_108go9o_3	2026-07-13 09:15:08.018435+00	2026-07-13 09:22:43.756359+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC003	Đặng Minh Khang	stu_demo_108go9o_3	\N	stu_demo_108go9o_3@student.demo.erg.edu.vn	MALE	2010-04-04 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0946511697	\N	\N	2025-10-13 09:22:43.671218+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675363.671217700, "completedAssignments": 11}
stu_demo_108go9o_4	2026-07-13 09:15:08.441553+00	2026-07-13 09:22:44.205598+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC004	Bùi Khánh Linh	stu_demo_108go9o_4	\N	stu_demo_108go9o_4@student.demo.erg.edu.vn	FEMALE	2010-05-05 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0946511696	\N	\N	2025-09-13 09:22:44.118429+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588964.118428700, "completedAssignments": 12}
stu_demo_108go9o_5	2026-07-13 09:15:08.862561+00	2026-07-13 09:22:44.64175+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC005	Đỗ Thảo Vy	stu_demo_108go9o_5	\N	stu_demo_108go9o_5@student.demo.erg.edu.vn	MALE	2010-06-06 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0946511695	\N	\N	2025-08-13 09:22:44.555495+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502564.555494700, "completedAssignments": 13}
stu_demo_108go9o_6	2026-07-13 09:15:09.294895+00	2026-07-13 09:22:45.079929+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC006	Nguyễn Gia Hân	stu_demo_108go9o_6	\N	stu_demo_108go9o_6@student.demo.erg.edu.vn	FEMALE	2010-07-07 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0946511694	\N	\N	2025-07-13 09:22:44.993146+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416164.993146100, "completedAssignments": 14}
stu_demo_108go9o_7	2026-07-13 09:15:09.718062+00	2026-07-13 09:22:45.511319+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC007	Trần Đức Anh	stu_demo_108go9o_7	\N	stu_demo_108go9o_7@student.demo.erg.edu.vn	MALE	2010-08-08 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0946511693	\N	\N	2025-06-13 09:22:45.42557+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329765.425570200, "completedAssignments": 15}
stu_demo_108go9o_8	2026-07-13 09:15:10.162368+00	2026-07-13 09:22:45.942267+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC008	Lê Hoàng Nam	stu_demo_108go9o_8	\N	stu_demo_108go9o_8@student.demo.erg.edu.vn	FEMALE	2010-09-09 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0946511692	\N	\N	2025-05-13 09:22:45.855664+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934565.855663800, "completedAssignments": 16}
stu_demo_108go9o_9	2026-07-13 09:15:10.604029+00	2026-07-13 09:22:46.375784+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC009	Phạm Ngọc Anh	stu_demo_108go9o_9	\N	stu_demo_108go9o_9@student.demo.erg.edu.vn	MALE	2010-10-10 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0946511691	\N	\N	2026-01-13 09:22:46.289258+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848166.289258000, "completedAssignments": 17}
stu_demo_108go9o_10	2026-07-13 09:15:11.026761+00	2026-07-13 09:22:46.805704+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC010	Võ Bảo Ngọc	stu_demo_108go9o_10	\N	stu_demo_108go9o_10@student.demo.erg.edu.vn	FEMALE	2010-11-11 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0992712227	\N	\N	2025-12-13 09:22:46.720083+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761766.720083300, "completedAssignments": 18}
stu_demo_108go9o_11	2026-07-13 09:15:11.45088+00	2026-07-13 09:22:47.23658+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC011	Đặng Tuấn Kiệt	stu_demo_108go9o_11	\N	stu_demo_108go9o_11@student.demo.erg.edu.vn	MALE	2010-12-12 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0992712228	\N	\N	2025-11-13 09:22:47.149287+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675367.149287200, "completedAssignments": 19}
stu_demo_108go9o_12	2026-07-13 09:15:11.871768+00	2026-07-13 09:22:47.669482+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC012	Bùi Quang Huy	stu_demo_108go9o_12	\N	stu_demo_108go9o_12@student.demo.erg.edu.vn	FEMALE	2010-01-13 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0992712229	\N	\N	2025-10-13 09:22:47.583047+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588967.583047200, "completedAssignments": 20}
stu_demo_108go9o_13	2026-07-13 09:15:12.301354+00	2026-07-13 09:22:48.119577+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC013	Đỗ Minh Khang	stu_demo_108go9o_13	\N	stu_demo_108go9o_13@student.demo.erg.edu.vn	MALE	2010-02-14 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0992712230	\N	\N	2025-09-13 09:22:48.032904+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502568.032904200, "completedAssignments": 21}
stu_demo_108go9o_14	2026-07-13 09:15:12.752499+00	2026-07-13 09:22:48.564681+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC014	Nguyễn Khánh Linh	stu_demo_108go9o_14	\N	stu_demo_108go9o_14@student.demo.erg.edu.vn	FEMALE	2010-03-15 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0992712231	\N	\N	2025-08-13 09:22:48.477842+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416168.477841600, "completedAssignments": 22}
stu_demo_108go9o_15	2026-07-13 09:15:13.217543+00	2026-07-13 09:22:48.993973+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC015	Trần Thảo Vy	stu_demo_108go9o_15	\N	stu_demo_108go9o_15@student.demo.erg.edu.vn	MALE	2010-04-16 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0992712232	\N	\N	2025-07-13 09:22:48.907366+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329768.907365600, "completedAssignments": 23}
stu_demo_108go9o_16	2026-07-13 09:15:13.646157+00	2026-07-13 09:22:49.424077+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC016	Lê Gia Hân	stu_demo_108go9o_16	\N	stu_demo_108go9o_16@student.demo.erg.edu.vn	FEMALE	2010-05-17 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0992712233	\N	\N	2025-06-13 09:22:49.337801+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934569.337801200, "completedAssignments": 8}
stu_demo_108go9o_17	2026-07-13 09:15:14.097364+00	2026-07-13 09:22:49.861627+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC017	Phạm Đức Anh	stu_demo_108go9o_17	\N	stu_demo_108go9o_17@student.demo.erg.edu.vn	MALE	2010-06-18 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0992712234	\N	\N	2025-05-13 09:22:49.775397+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848169.775396800, "completedAssignments": 9}
stu_demo_icdh3n_12	2026-07-13 09:12:37.069065+00	2026-07-13 09:20:12.159156+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_31	ERG18Z1BDQ012	Võ Hoàng Nam	stu_demo_icdh3n_12	\N	stu_demo_icdh3n_12@student.demo.erg.edu.vn	FEMALE	2012-01-13 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0964566911	\N	\N	2025-10-13 09:20:12.072453+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588812.072452900, "completedAssignments": 20}
stu_demo_icdh3o_7	2026-07-13 09:12:45.813302+00	2026-07-13 09:20:20.825845+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_32	ERG18Z1BDQ007	Đỗ Minh Khang	stu_demo_icdh3o_7	\N	stu_demo_icdh3o_7@student.demo.erg.edu.vn	MALE	2012-08-08 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0999194855	\N	\N	2025-06-13 09:20:20.735184+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329620.735183500, "completedAssignments": 15}
stu_demo_icdh4i_2	2026-07-13 09:12:54.439108+00	2026-07-13 09:20:29.476625+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ002	Lê Hoàng Nam	stu_demo_icdh4i_2	\N	stu_demo_icdh4i_2@student.demo.erg.edu.vn	FEMALE	2012-03-03 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0999194860	\N	\N	2025-11-13 09:20:29.389835+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761629.389835000, "completedAssignments": 10}
stu_demo_icdh4i_21	2026-07-13 09:13:02.697852+00	2026-07-13 09:20:38.015455+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_41	ERG18Z1BDQ021	Đặng Đức Anh	stu_demo_icdh4i_21	\N	stu_demo_icdh4i_21@student.demo.erg.edu.vn	MALE	2012-10-22 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0964566941	\N	\N	2025-10-13 09:20:37.921188+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502437.921188100, "completedAssignments": 13}
stu_demo_icdh4j_16	2026-07-13 09:13:11.451206+00	2026-07-13 09:20:46.609487+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_42	ERG18Z1BDQ016	Nguyễn Quang Huy	stu_demo_icdh4j_16	\N	stu_demo_icdh4j_16@student.demo.erg.edu.vn	FEMALE	2012-05-17 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0964566915	\N	\N	2025-06-13 09:20:46.522026+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934446.523315500, "completedAssignments": 8}
stu_demo_icdh5d_11	2026-07-13 09:13:20.140685+00	2026-07-13 09:20:55.235445+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_51	ERG18Z1BDQ011	Phạm Đức Anh	stu_demo_icdh5d_11	\N	stu_demo_icdh5d_11@student.demo.erg.edu.vn	MALE	2012-12-12 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0964566910	\N	\N	2025-11-13 09:20:55.147761+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675255.147760500, "completedAssignments": 19}
stu_demo_icdh5e_6	2026-07-13 09:13:28.72525+00	2026-07-13 09:21:03.872586+00	erg	019f5a8b-b528-7507-b347-52e990580f45	cls_demo_18z1bdq_52	ERG18Z1BDQ006	Bùi Quang Huy	stu_demo_icdh5e_6	\N	stu_demo_icdh5e_6@student.demo.erg.edu.vn	FEMALE	2012-07-07 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0999194856	\N	\N	2025-07-13 09:21:03.784204+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416063.784203800, "completedAssignments": 14}
stu_demo_a6ve72_1	2026-07-13 09:13:39.364214+00	2026-07-13 09:21:14.264862+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK001	Lê Khánh Linh	stu_demo_a6ve72_1	\N	stu_demo_a6ve72_1@student.demo.erg.edu.vn	MALE	2011-02-02 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0997299841	\N	\N	2025-12-13 09:21:14.178197+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848074.178196800, "completedAssignments": 9}
stu_demo_a6ve72_20	2026-07-13 09:13:47.575112+00	2026-07-13 09:21:22.62605+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_61	ERG1CWZSFK020	Đặng Minh Khang	stu_demo_a6ve72_20	\N	stu_demo_a6ve72_20@student.demo.erg.edu.vn	FEMALE	2011-09-21 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0928345106	\N	\N	2025-11-13 09:21:22.539147+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588882.539146500, "completedAssignments": 12}
stu_demo_a6ve73_15	2026-07-13 09:13:56.144058+00	2026-07-13 09:21:31.572417+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_62	ERG1CWZSFK015	Nguyễn Hoàng Nam	stu_demo_a6ve73_15	\N	stu_demo_a6ve73_15@student.demo.erg.edu.vn	MALE	2011-04-16 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0928345132	\N	\N	2025-07-13 09:21:31.48601+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329691.486010100, "completedAssignments": 23}
stu_demo_a6ve7x_10	2026-07-13 09:14:04.717519+00	2026-07-13 09:21:40.355883+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_71	ERG1CWZSFK010	Phạm Minh Khang	stu_demo_a6ve7x_10	\N	stu_demo_a6ve7x_10@student.demo.erg.edu.vn	FEMALE	2011-11-11 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0928345137	\N	\N	2025-12-13 09:21:40.268924+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761700.268923800, "completedAssignments": 18}
stu_demo_a6ve7y_5	2026-07-13 09:14:13.355294+00	2026-07-13 09:21:49.092282+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK005	Bùi Hoàng Nam	stu_demo_a6ve7y_5	\N	stu_demo_a6ve7y_5@student.demo.erg.edu.vn	MALE	2011-06-06 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0997299845	\N	\N	2025-08-13 09:21:49.005069+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502509.005069100, "completedAssignments": 13}
stu_demo_a6ve7y_24	2026-07-13 09:14:21.50004+00	2026-07-13 09:21:57.390482+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_72	ERG1CWZSFK024	Trần Đức Anh	stu_demo_a6ve7y_24	\N	stu_demo_a6ve7y_24@student.demo.erg.edu.vn	FEMALE	2011-01-25 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0928345102	\N	\N	2025-07-13 09:21:57.303198+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934517.303198300, "completedAssignments": 16}
stu_demo_a6ve8s_19	2026-07-13 09:14:30.03515+00	2026-07-13 09:22:05.992097+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_81	ERG1CWZSFK019	Võ Quang Huy	stu_demo_a6ve8s_19	\N	stu_demo_a6ve8s_19@student.demo.erg.edu.vn	MALE	2011-08-20 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0928345128	\N	\N	2025-12-13 09:22:05.905706+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675325.905705700, "completedAssignments": 11}
stu_demo_a6ve8t_14	2026-07-13 09:14:38.584118+00	2026-07-13 09:22:14.546949+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_82	ERG1CWZSFK014	Đỗ Đức Anh	stu_demo_a6ve8t_14	\N	stu_demo_a6ve8t_14@student.demo.erg.edu.vn	FEMALE	2011-03-15 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0928345133	\N	\N	2025-08-13 09:22:14.460584+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416134.460583600, "completedAssignments": 22}
stu_demo_a6ve9n_9	2026-07-13 09:14:47.088966+00	2026-07-13 09:22:23.116297+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_91	ERG1CWZSFK009	Lê Quang Huy	stu_demo_a6ve9n_9	\N	stu_demo_a6ve9n_9@student.demo.erg.edu.vn	MALE	2011-10-10 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0997299849	\N	\N	2026-01-13 09:22:23.029158+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848143.029158100, "completedAssignments": 17}
stu_demo_a6ve9o_4	2026-07-13 09:14:55.687879+00	2026-07-13 09:22:31.775624+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK004	Đặng Đức Anh	stu_demo_a6ve9o_4	\N	stu_demo_a6ve9o_4@student.demo.erg.edu.vn	FEMALE	2011-05-05 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0997299844	\N	\N	2025-09-13 09:22:31.688865+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588951.688864500, "completedAssignments": 12}
stu_demo_a6ve9o_23	2026-07-13 09:15:03.88424+00	2026-07-13 09:22:40.089658+00	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	cls_demo_1cwzsfk_92	ERG1CWZSFK023	Nguyễn Gia Hân	stu_demo_a6ve9o_23	\N	stu_demo_a6ve9o_23@student.demo.erg.edu.vn	MALE	2011-12-24 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0928345103	\N	\N	2025-08-13 09:22:40.002218+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329760.002218100, "completedAssignments": 15}
stu_demo_108go9o_18	2026-07-13 09:22:50.302864+00	2026-07-13 09:22:50.302864+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC018	Võ Hoàng Nam	stu_demo_108go9o_18	\N	stu_demo_108go9o_18@student.demo.erg.edu.vn	FEMALE	2010-07-19 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0992712235	\N	\N	2026-01-13 09:22:50.216323+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761770.216322900, "completedAssignments": 10}
stu_demo_108go9o_19	2026-07-13 09:22:50.738386+00	2026-07-13 09:22:50.738386+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC019	Đặng Ngọc Anh	stu_demo_108go9o_19	\N	stu_demo_108go9o_19@student.demo.erg.edu.vn	MALE	2010-08-20 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0992712236	\N	\N	2025-12-13 09:22:50.65175+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675370.651750300, "completedAssignments": 11}
stu_demo_108go9o_20	2026-07-13 09:22:51.183205+00	2026-07-13 09:22:51.183205+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC020	Bùi Bảo Ngọc	stu_demo_108go9o_20	\N	stu_demo_108go9o_20@student.demo.erg.edu.vn	FEMALE	2010-09-21 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0992712258	\N	\N	2025-11-13 09:22:51.096988+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588971.096987800, "completedAssignments": 12}
stu_demo_108go9o_21	2026-07-13 09:22:51.614406+00	2026-07-13 09:22:51.614406+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC021	Đỗ Tuấn Kiệt	stu_demo_108go9o_21	\N	stu_demo_108go9o_21@student.demo.erg.edu.vn	MALE	2010-10-22 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0992712259	\N	\N	2025-10-13 09:22:51.529057+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502571.529056500, "completedAssignments": 13}
stu_demo_108go9o_22	2026-07-13 09:22:52.044817+00	2026-07-13 09:22:52.044817+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC022	Nguyễn Quang Huy	stu_demo_108go9o_22	\N	stu_demo_108go9o_22@student.demo.erg.edu.vn	FEMALE	2010-11-23 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0992712260	\N	\N	2025-09-13 09:22:51.958086+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416171.958086200, "completedAssignments": 14}
stu_demo_108go9o_23	2026-07-13 09:22:52.486288+00	2026-07-13 09:22:52.486288+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC023	Trần Minh Khang	stu_demo_108go9o_23	\N	stu_demo_108go9o_23@student.demo.erg.edu.vn	MALE	2010-12-24 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0992712261	\N	\N	2025-08-13 09:22:52.396923+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329772.396923400, "completedAssignments": 15}
stu_demo_108go9o_24	2026-07-13 09:22:52.918669+00	2026-07-13 09:22:52.918669+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_101	ERGAIW7IC024	Lê Khánh Linh	stu_demo_108go9o_24	\N	stu_demo_108go9o_24@student.demo.erg.edu.vn	FEMALE	2010-01-25 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0992712262	\N	\N	2025-07-13 09:22:52.833001+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934572.833001200, "completedAssignments": 16}
stu_demo_108go9p_1	2026-07-13 09:22:53.81048+00	2026-07-13 09:22:53.81048+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC001	Phạm Tuấn Kiệt	stu_demo_108go9p_1	\N	stu_demo_108go9p_1@student.demo.erg.edu.vn	MALE	2010-02-02 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0946511699	\N	\N	2025-12-13 09:22:53.722684+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848173.722683700, "completedAssignments": 9}
stu_demo_108go9p_2	2026-07-13 09:22:54.249678+00	2026-07-13 09:22:54.249678+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC002	Võ Quang Huy	stu_demo_108go9p_2	\N	stu_demo_108go9p_2@student.demo.erg.edu.vn	FEMALE	2010-03-03 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0946511698	\N	\N	2025-11-13 09:22:54.1634+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761774.163400000, "completedAssignments": 10}
stu_demo_108go9p_3	2026-07-13 09:22:54.683488+00	2026-07-13 09:22:54.683488+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC003	Đặng Minh Khang	stu_demo_108go9p_3	\N	stu_demo_108go9p_3@student.demo.erg.edu.vn	MALE	2010-04-04 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0946511697	\N	\N	2025-10-13 09:22:54.596051+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675374.596050700, "completedAssignments": 11}
stu_demo_108go9p_4	2026-07-13 09:22:55.123664+00	2026-07-13 09:22:55.123664+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC004	Bùi Khánh Linh	stu_demo_108go9p_4	\N	stu_demo_108go9p_4@student.demo.erg.edu.vn	FEMALE	2010-05-05 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0946511696	\N	\N	2025-09-13 09:22:55.035127+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588975.035126800, "completedAssignments": 12}
stu_demo_108go9p_5	2026-07-13 09:22:55.561826+00	2026-07-13 09:22:55.561826+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC005	Đỗ Thảo Vy	stu_demo_108go9p_5	\N	stu_demo_108go9p_5@student.demo.erg.edu.vn	MALE	2010-06-06 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0946511695	\N	\N	2025-08-13 09:22:55.474881+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502575.474880800, "completedAssignments": 13}
stu_demo_108go9p_6	2026-07-13 09:22:55.999702+00	2026-07-13 09:22:55.999702+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC006	Nguyễn Gia Hân	stu_demo_108go9p_6	\N	stu_demo_108go9p_6@student.demo.erg.edu.vn	FEMALE	2010-07-07 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0946511694	\N	\N	2025-07-13 09:22:55.912569+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416175.912569300, "completedAssignments": 14}
stu_demo_108go9p_7	2026-07-13 09:22:56.434287+00	2026-07-13 09:22:56.434287+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC007	Trần Đức Anh	stu_demo_108go9p_7	\N	stu_demo_108go9p_7@student.demo.erg.edu.vn	MALE	2010-08-08 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0946511693	\N	\N	2025-06-13 09:22:56.348167+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329776.348167000, "completedAssignments": 15}
stu_demo_108go9p_8	2026-07-13 09:22:56.871334+00	2026-07-13 09:22:56.871334+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC008	Lê Hoàng Nam	stu_demo_108go9p_8	\N	stu_demo_108go9p_8@student.demo.erg.edu.vn	FEMALE	2010-09-09 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0946511692	\N	\N	2025-05-13 09:22:56.785006+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934576.785005800, "completedAssignments": 16}
stu_demo_108go9p_9	2026-07-13 09:22:57.310415+00	2026-07-13 09:22:57.310415+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC009	Phạm Ngọc Anh	stu_demo_108go9p_9	\N	stu_demo_108go9p_9@student.demo.erg.edu.vn	MALE	2010-10-10 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0946511691	\N	\N	2026-01-13 09:22:57.218582+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848177.218581900, "completedAssignments": 17}
stu_demo_108go9p_10	2026-07-13 09:22:57.739917+00	2026-07-13 09:22:57.739917+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC010	Võ Bảo Ngọc	stu_demo_108go9p_10	\N	stu_demo_108go9p_10@student.demo.erg.edu.vn	FEMALE	2010-11-11 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0992712227	\N	\N	2025-12-13 09:22:57.652534+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761777.652534000, "completedAssignments": 18}
stu_demo_108go9p_11	2026-07-13 09:22:58.170914+00	2026-07-13 09:22:58.170914+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC011	Đặng Tuấn Kiệt	stu_demo_108go9p_11	\N	stu_demo_108go9p_11@student.demo.erg.edu.vn	MALE	2010-12-12 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0992712228	\N	\N	2025-11-13 09:22:58.085125+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675378.085125200, "completedAssignments": 19}
stu_demo_108go9p_12	2026-07-13 09:22:58.604818+00	2026-07-13 09:22:58.604818+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC012	Bùi Quang Huy	stu_demo_108go9p_12	\N	stu_demo_108go9p_12@student.demo.erg.edu.vn	FEMALE	2010-01-13 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0992712229	\N	\N	2025-10-13 09:22:58.518378+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588978.518377800, "completedAssignments": 20}
stu_demo_108go9p_13	2026-07-13 09:22:59.054091+00	2026-07-13 09:22:59.054091+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC013	Đỗ Minh Khang	stu_demo_108go9p_13	\N	stu_demo_108go9p_13@student.demo.erg.edu.vn	MALE	2010-02-14 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0992712230	\N	\N	2025-09-13 09:22:58.948491+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502578.948491200, "completedAssignments": 21}
stu_demo_108go9p_14	2026-07-13 09:22:59.487342+00	2026-07-13 09:22:59.487342+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC014	Nguyễn Khánh Linh	stu_demo_108go9p_14	\N	stu_demo_108go9p_14@student.demo.erg.edu.vn	FEMALE	2010-03-15 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0992712231	\N	\N	2025-08-13 09:22:59.401171+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416179.401171100, "completedAssignments": 22}
stu_demo_108go9p_15	2026-07-13 09:22:59.918204+00	2026-07-13 09:22:59.918204+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC015	Trần Thảo Vy	stu_demo_108go9p_15	\N	stu_demo_108go9p_15@student.demo.erg.edu.vn	MALE	2010-04-16 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0992712232	\N	\N	2025-07-13 09:22:59.832675+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329779.832674800, "completedAssignments": 23}
stu_demo_108go9p_16	2026-07-13 09:23:00.354022+00	2026-07-13 09:23:00.354022+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC016	Lê Gia Hân	stu_demo_108go9p_16	\N	stu_demo_108go9p_16@student.demo.erg.edu.vn	FEMALE	2010-05-17 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0992712233	\N	\N	2025-06-13 09:23:00.268104+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934580.268103800, "completedAssignments": 8}
stu_demo_108go9p_17	2026-07-13 09:23:00.788135+00	2026-07-13 09:23:00.788135+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC017	Phạm Đức Anh	stu_demo_108go9p_17	\N	stu_demo_108go9p_17@student.demo.erg.edu.vn	MALE	2010-06-18 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0992712234	\N	\N	2025-05-13 09:23:00.702424+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848180.702423700, "completedAssignments": 9}
stu_demo_108go9p_18	2026-07-13 09:23:01.217651+00	2026-07-13 09:23:01.217651+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC018	Võ Hoàng Nam	stu_demo_108go9p_18	\N	stu_demo_108go9p_18@student.demo.erg.edu.vn	FEMALE	2010-07-19 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0992712235	\N	\N	2026-01-13 09:23:01.132089+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761781.132088700, "completedAssignments": 10}
stu_demo_108go9p_19	2026-07-13 09:23:01.657501+00	2026-07-13 09:23:01.657501+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC019	Đặng Ngọc Anh	stu_demo_108go9p_19	\N	stu_demo_108go9p_19@student.demo.erg.edu.vn	MALE	2010-08-20 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0992712236	\N	\N	2025-12-13 09:23:01.571287+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675381.571286800, "completedAssignments": 11}
stu_demo_108go9p_20	2026-07-13 09:23:02.088812+00	2026-07-13 09:23:02.088812+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC020	Bùi Bảo Ngọc	stu_demo_108go9p_20	\N	stu_demo_108go9p_20@student.demo.erg.edu.vn	FEMALE	2010-09-21 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0992712258	\N	\N	2025-11-13 09:23:02.00208+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588982.002079500, "completedAssignments": 12}
stu_demo_108go9p_21	2026-07-13 09:23:02.5232+00	2026-07-13 09:23:02.5232+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC021	Đỗ Tuấn Kiệt	stu_demo_108go9p_21	\N	stu_demo_108go9p_21@student.demo.erg.edu.vn	MALE	2010-10-22 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0992712259	\N	\N	2025-10-13 09:23:02.435523+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502582.435522800, "completedAssignments": 13}
stu_demo_108go9p_22	2026-07-13 09:23:02.955379+00	2026-07-13 09:23:02.955379+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC022	Nguyễn Quang Huy	stu_demo_108go9p_22	\N	stu_demo_108go9p_22@student.demo.erg.edu.vn	FEMALE	2010-11-23 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0992712260	\N	\N	2025-09-13 09:23:02.868968+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416182.868968400, "completedAssignments": 14}
stu_demo_108go9p_23	2026-07-13 09:23:03.384851+00	2026-07-13 09:23:03.384851+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC023	Trần Minh Khang	stu_demo_108go9p_23	\N	stu_demo_108go9p_23@student.demo.erg.edu.vn	MALE	2010-12-24 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0992712261	\N	\N	2025-08-13 09:23:03.299135+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329783.299135200, "completedAssignments": 15}
stu_demo_108go9p_24	2026-07-13 09:23:03.816847+00	2026-07-13 09:23:03.816847+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_102	ERGAIW7IC024	Lê Khánh Linh	stu_demo_108go9p_24	\N	stu_demo_108go9p_24@student.demo.erg.edu.vn	FEMALE	2010-01-25 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0992712262	\N	\N	2025-07-13 09:23:03.728767+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934583.728766600, "completedAssignments": 16}
stu_demo_108goaj_1	2026-07-13 09:23:04.686552+00	2026-07-13 09:23:04.686552+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC001	Phạm Tuấn Kiệt	stu_demo_108goaj_1	\N	stu_demo_108goaj_1@student.demo.erg.edu.vn	MALE	2010-02-02 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0946511699	\N	\N	2025-12-13 09:23:04.599112+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848184.599112300, "completedAssignments": 9}
stu_demo_108goaj_2	2026-07-13 09:23:05.127729+00	2026-07-13 09:23:05.127729+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC002	Võ Quang Huy	stu_demo_108goaj_2	\N	stu_demo_108goaj_2@student.demo.erg.edu.vn	FEMALE	2010-03-03 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0946511698	\N	\N	2025-11-13 09:23:05.041932+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761785.041932400, "completedAssignments": 10}
stu_demo_108goaj_3	2026-07-13 09:23:05.560346+00	2026-07-13 09:23:05.560346+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC003	Đặng Minh Khang	stu_demo_108goaj_3	\N	stu_demo_108goaj_3@student.demo.erg.edu.vn	MALE	2010-04-04 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0946511697	\N	\N	2025-10-13 09:23:05.472827+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675385.472826700, "completedAssignments": 11}
stu_demo_108goaj_4	2026-07-13 09:23:06.006929+00	2026-07-13 09:23:06.006929+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC004	Bùi Khánh Linh	stu_demo_108goaj_4	\N	stu_demo_108goaj_4@student.demo.erg.edu.vn	FEMALE	2010-05-05 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0946511696	\N	\N	2025-09-13 09:23:05.919309+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588985.919308900, "completedAssignments": 12}
stu_demo_108goaj_5	2026-07-13 09:23:06.437058+00	2026-07-13 09:23:06.437058+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC005	Đỗ Thảo Vy	stu_demo_108goaj_5	\N	stu_demo_108goaj_5@student.demo.erg.edu.vn	MALE	2010-06-06 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0946511695	\N	\N	2025-08-13 09:23:06.350102+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502586.350102300, "completedAssignments": 13}
stu_demo_108goaj_6	2026-07-13 09:23:06.866424+00	2026-07-13 09:23:06.866424+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC006	Nguyễn Gia Hân	stu_demo_108goaj_6	\N	stu_demo_108goaj_6@student.demo.erg.edu.vn	FEMALE	2010-07-07 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0946511694	\N	\N	2025-07-13 09:23:06.779057+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416186.779056500, "completedAssignments": 14}
stu_demo_108goaj_7	2026-07-13 09:23:07.296576+00	2026-07-13 09:23:07.296576+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC007	Trần Đức Anh	stu_demo_108goaj_7	\N	stu_demo_108goaj_7@student.demo.erg.edu.vn	MALE	2010-08-08 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0946511693	\N	\N	2025-06-13 09:23:07.209243+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329787.209243200, "completedAssignments": 15}
stu_demo_108goaj_8	2026-07-13 09:23:07.726913+00	2026-07-13 09:23:07.726913+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC008	Lê Hoàng Nam	stu_demo_108goaj_8	\N	stu_demo_108goaj_8@student.demo.erg.edu.vn	FEMALE	2010-09-09 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0946511692	\N	\N	2025-05-13 09:23:07.641911+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934587.641911100, "completedAssignments": 16}
stu_demo_108goaj_9	2026-07-13 09:23:08.172377+00	2026-07-13 09:23:08.172377+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC009	Phạm Ngọc Anh	stu_demo_108goaj_9	\N	stu_demo_108goaj_9@student.demo.erg.edu.vn	MALE	2010-10-10 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0946511691	\N	\N	2026-01-13 09:23:08.084012+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848188.084012400, "completedAssignments": 17}
stu_demo_108goaj_10	2026-07-13 09:23:08.613437+00	2026-07-13 09:23:08.613437+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC010	Võ Bảo Ngọc	stu_demo_108goaj_10	\N	stu_demo_108goaj_10@student.demo.erg.edu.vn	FEMALE	2010-11-11 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0992712227	\N	\N	2025-12-13 09:23:08.516528+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761788.516528100, "completedAssignments": 18}
stu_demo_108goaj_11	2026-07-13 09:23:09.045385+00	2026-07-13 09:23:09.045385+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC011	Đặng Tuấn Kiệt	stu_demo_108goaj_11	\N	stu_demo_108goaj_11@student.demo.erg.edu.vn	MALE	2010-12-12 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0992712228	\N	\N	2025-11-13 09:23:08.958555+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675388.958554500, "completedAssignments": 19}
stu_demo_108goaj_12	2026-07-13 09:23:09.477041+00	2026-07-13 09:23:09.477041+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC012	Bùi Quang Huy	stu_demo_108goaj_12	\N	stu_demo_108goaj_12@student.demo.erg.edu.vn	FEMALE	2010-01-13 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0992712229	\N	\N	2025-10-13 09:23:09.390238+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783588989.390238000, "completedAssignments": 20}
stu_demo_108goaj_13	2026-07-13 09:23:09.912415+00	2026-07-13 09:23:09.912415+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC013	Đỗ Minh Khang	stu_demo_108goaj_13	\N	stu_demo_108goaj_13@student.demo.erg.edu.vn	MALE	2010-02-14 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0992712230	\N	\N	2025-09-13 09:23:09.826143+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502589.826143400, "completedAssignments": 21}
stu_demo_108goaj_14	2026-07-13 09:23:10.342266+00	2026-07-13 09:23:10.342266+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC014	Nguyễn Khánh Linh	stu_demo_108goaj_14	\N	stu_demo_108goaj_14@student.demo.erg.edu.vn	FEMALE	2010-03-15 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0992712231	\N	\N	2025-08-13 09:23:10.25698+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416190.256979500, "completedAssignments": 22}
stu_demo_108goaj_15	2026-07-13 09:23:10.773313+00	2026-07-13 09:23:10.773313+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC015	Trần Thảo Vy	stu_demo_108goaj_15	\N	stu_demo_108goaj_15@student.demo.erg.edu.vn	MALE	2010-04-16 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0992712232	\N	\N	2025-07-13 09:23:10.687598+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329790.687597700, "completedAssignments": 23}
stu_demo_108goaj_16	2026-07-13 09:23:11.201813+00	2026-07-13 09:23:11.201813+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC016	Lê Gia Hân	stu_demo_108goaj_16	\N	stu_demo_108goaj_16@student.demo.erg.edu.vn	FEMALE	2010-05-17 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0992712233	\N	\N	2025-06-13 09:23:11.116006+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934591.116005500, "completedAssignments": 8}
stu_demo_108goaj_17	2026-07-13 09:23:11.634004+00	2026-07-13 09:23:11.634004+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC017	Phạm Đức Anh	stu_demo_108goaj_17	\N	stu_demo_108goaj_17@student.demo.erg.edu.vn	MALE	2010-06-18 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0992712234	\N	\N	2025-05-13 09:23:11.547858+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848191.547858100, "completedAssignments": 9}
stu_demo_108goaj_18	2026-07-13 09:23:12.062976+00	2026-07-13 09:23:12.062976+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC018	Võ Hoàng Nam	stu_demo_108goaj_18	\N	stu_demo_108goaj_18@student.demo.erg.edu.vn	FEMALE	2010-07-19 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0992712235	\N	\N	2026-01-13 09:23:11.977478+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761791.977478400, "completedAssignments": 10}
stu_demo_108goaj_19	2026-07-13 09:23:12.492462+00	2026-07-13 09:23:12.492462+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC019	Đặng Ngọc Anh	stu_demo_108goaj_19	\N	stu_demo_108goaj_19@student.demo.erg.edu.vn	MALE	2010-08-20 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0992712236	\N	\N	2025-12-13 09:23:12.407207+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675392.407207300, "completedAssignments": 11}
stu_demo_108goaj_20	2026-07-13 09:23:12.924349+00	2026-07-13 09:23:12.924349+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC020	Bùi Bảo Ngọc	stu_demo_108goaj_20	\N	stu_demo_108goaj_20@student.demo.erg.edu.vn	FEMALE	2010-09-21 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0992712258	\N	\N	2025-11-13 09:23:12.83792+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783588992.837920400, "completedAssignments": 12}
stu_demo_108goaj_21	2026-07-13 09:23:13.354232+00	2026-07-13 09:23:13.354232+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC021	Đỗ Tuấn Kiệt	stu_demo_108goaj_21	\N	stu_demo_108goaj_21@student.demo.erg.edu.vn	MALE	2010-10-22 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0992712259	\N	\N	2025-10-13 09:23:13.268742+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502593.268741700, "completedAssignments": 13}
stu_demo_108goaj_22	2026-07-13 09:23:13.784848+00	2026-07-13 09:23:13.784848+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC022	Nguyễn Quang Huy	stu_demo_108goaj_22	\N	stu_demo_108goaj_22@student.demo.erg.edu.vn	FEMALE	2010-11-23 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0992712260	\N	\N	2025-09-13 09:23:13.69895+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416193.698950100, "completedAssignments": 14}
stu_demo_108goaj_23	2026-07-13 09:23:14.214989+00	2026-07-13 09:23:14.214989+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC023	Trần Minh Khang	stu_demo_108goaj_23	\N	stu_demo_108goaj_23@student.demo.erg.edu.vn	MALE	2010-12-24 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0992712261	\N	\N	2025-08-13 09:23:14.129133+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329794.129133200, "completedAssignments": 15}
stu_demo_108goaj_24	2026-07-13 09:23:14.649227+00	2026-07-13 09:23:14.649227+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_111	ERGAIW7IC024	Lê Khánh Linh	stu_demo_108goaj_24	\N	stu_demo_108goaj_24@student.demo.erg.edu.vn	FEMALE	2010-01-25 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0992712262	\N	\N	2025-07-13 09:23:14.563237+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934594.563236900, "completedAssignments": 16}
stu_demo_108goak_1	2026-07-13 09:23:15.511308+00	2026-07-13 09:23:15.511308+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC001	Phạm Tuấn Kiệt	stu_demo_108goak_1	\N	stu_demo_108goak_1@student.demo.erg.edu.vn	MALE	2010-02-02 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0946511699	\N	\N	2025-12-13 09:23:15.425556+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848195.425555800, "completedAssignments": 9}
stu_demo_108goak_2	2026-07-13 09:23:15.943115+00	2026-07-13 09:23:15.943115+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC002	Võ Quang Huy	stu_demo_108goak_2	\N	stu_demo_108goak_2@student.demo.erg.edu.vn	FEMALE	2010-03-03 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0946511698	\N	\N	2025-11-13 09:23:15.857301+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761795.857301100, "completedAssignments": 10}
stu_demo_108goak_3	2026-07-13 09:23:16.377638+00	2026-07-13 09:23:16.377638+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC003	Đặng Minh Khang	stu_demo_108goak_3	\N	stu_demo_108goak_3@student.demo.erg.edu.vn	MALE	2010-04-04 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0946511697	\N	\N	2025-10-13 09:23:16.286881+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675396.286880800, "completedAssignments": 11}
stu_demo_108goak_4	2026-07-13 09:23:16.812753+00	2026-07-13 09:23:16.812753+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC004	Bùi Khánh Linh	stu_demo_108goak_4	\N	stu_demo_108goak_4@student.demo.erg.edu.vn	FEMALE	2010-05-05 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0946511696	\N	\N	2025-09-13 09:23:16.726547+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783588996.726546800, "completedAssignments": 12}
stu_demo_108goak_5	2026-07-13 09:23:17.254965+00	2026-07-13 09:23:17.254965+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC005	Đỗ Thảo Vy	stu_demo_108goak_5	\N	stu_demo_108goak_5@student.demo.erg.edu.vn	MALE	2010-06-06 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0946511695	\N	\N	2025-08-13 09:23:17.164137+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502597.164137300, "completedAssignments": 13}
stu_demo_108goak_6	2026-07-13 09:23:17.6938+00	2026-07-13 09:23:17.6938+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC006	Nguyễn Gia Hân	stu_demo_108goak_6	\N	stu_demo_108goak_6@student.demo.erg.edu.vn	FEMALE	2010-07-07 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0946511694	\N	\N	2025-07-13 09:23:17.604169+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416197.604169200, "completedAssignments": 14}
stu_demo_108goak_7	2026-07-13 09:23:18.124193+00	2026-07-13 09:23:18.124193+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC007	Trần Đức Anh	stu_demo_108goak_7	\N	stu_demo_108goak_7@student.demo.erg.edu.vn	MALE	2010-08-08 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0946511693	\N	\N	2025-06-13 09:23:18.037793+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329798.037792600, "completedAssignments": 15}
stu_demo_108goak_8	2026-07-13 09:23:18.562902+00	2026-07-13 09:23:18.562902+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC008	Lê Hoàng Nam	stu_demo_108goak_8	\N	stu_demo_108goak_8@student.demo.erg.edu.vn	FEMALE	2010-09-09 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0946511692	\N	\N	2025-05-13 09:23:18.476165+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934598.476164500, "completedAssignments": 16}
stu_demo_108goak_9	2026-07-13 09:23:19.004194+00	2026-07-13 09:23:19.004194+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC009	Phạm Ngọc Anh	stu_demo_108goak_9	\N	stu_demo_108goak_9@student.demo.erg.edu.vn	MALE	2010-10-10 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0946511691	\N	\N	2026-01-13 09:23:18.913064+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848198.914056500, "completedAssignments": 17}
stu_demo_108goak_10	2026-07-13 09:23:19.435736+00	2026-07-13 09:23:19.435736+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC010	Võ Bảo Ngọc	stu_demo_108goak_10	\N	stu_demo_108goak_10@student.demo.erg.edu.vn	FEMALE	2010-11-11 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0992712227	\N	\N	2025-12-13 09:23:19.348853+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761799.348852600, "completedAssignments": 18}
stu_demo_108goak_11	2026-07-13 09:23:19.872467+00	2026-07-13 09:23:19.872467+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC011	Đặng Tuấn Kiệt	stu_demo_108goak_11	\N	stu_demo_108goak_11@student.demo.erg.edu.vn	MALE	2010-12-12 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0992712228	\N	\N	2025-11-13 09:23:19.785397+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675399.785396700, "completedAssignments": 19}
stu_demo_108goak_12	2026-07-13 09:23:20.309506+00	2026-07-13 09:23:20.309506+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC012	Bùi Quang Huy	stu_demo_108goak_12	\N	stu_demo_108goak_12@student.demo.erg.edu.vn	FEMALE	2010-01-13 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0992712229	\N	\N	2025-10-13 09:23:20.223605+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589000.223604500, "completedAssignments": 20}
stu_demo_108goak_13	2026-07-13 09:23:20.748262+00	2026-07-13 09:23:20.748262+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC013	Đỗ Minh Khang	stu_demo_108goak_13	\N	stu_demo_108goak_13@student.demo.erg.edu.vn	MALE	2010-02-14 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0992712230	\N	\N	2025-09-13 09:23:20.662919+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502600.662918900, "completedAssignments": 21}
stu_demo_108goak_14	2026-07-13 09:23:21.18784+00	2026-07-13 09:23:21.18784+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC014	Nguyễn Khánh Linh	stu_demo_108goak_14	\N	stu_demo_108goak_14@student.demo.erg.edu.vn	FEMALE	2010-03-15 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0992712231	\N	\N	2025-08-13 09:23:21.099544+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416201.099544100, "completedAssignments": 22}
stu_demo_108goak_15	2026-07-13 09:23:21.623473+00	2026-07-13 09:23:21.623473+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC015	Trần Thảo Vy	stu_demo_108goak_15	\N	stu_demo_108goak_15@student.demo.erg.edu.vn	MALE	2010-04-16 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0992712232	\N	\N	2025-07-13 09:23:21.537339+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329801.537339400, "completedAssignments": 23}
stu_demo_108goak_16	2026-07-13 09:23:22.060181+00	2026-07-13 09:23:22.060181+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC016	Lê Gia Hân	stu_demo_108goak_16	\N	stu_demo_108goak_16@student.demo.erg.edu.vn	FEMALE	2010-05-17 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0992712233	\N	\N	2025-06-13 09:23:21.969108+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934601.970104700, "completedAssignments": 8}
stu_demo_108goak_17	2026-07-13 09:23:22.500748+00	2026-07-13 09:23:22.500748+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC017	Phạm Đức Anh	stu_demo_108goak_17	\N	stu_demo_108goak_17@student.demo.erg.edu.vn	MALE	2010-06-18 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0992712234	\N	\N	2025-05-13 09:23:22.410904+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848202.410903800, "completedAssignments": 9}
stu_demo_108goak_18	2026-07-13 09:23:22.946254+00	2026-07-13 09:23:22.946254+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC018	Võ Hoàng Nam	stu_demo_108goak_18	\N	stu_demo_108goak_18@student.demo.erg.edu.vn	FEMALE	2010-07-19 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0992712235	\N	\N	2026-01-13 09:23:22.860305+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761802.860304500, "completedAssignments": 10}
stu_demo_108goak_19	2026-07-13 09:23:23.382738+00	2026-07-13 09:23:23.382738+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC019	Đặng Ngọc Anh	stu_demo_108goak_19	\N	stu_demo_108goak_19@student.demo.erg.edu.vn	MALE	2010-08-20 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0992712236	\N	\N	2025-12-13 09:23:23.295747+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675403.295747100, "completedAssignments": 11}
stu_demo_108goak_20	2026-07-13 09:23:23.819927+00	2026-07-13 09:23:23.819927+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC020	Bùi Bảo Ngọc	stu_demo_108goak_20	\N	stu_demo_108goak_20@student.demo.erg.edu.vn	FEMALE	2010-09-21 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0992712258	\N	\N	2025-11-13 09:23:23.732806+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589003.732805700, "completedAssignments": 12}
stu_demo_108goak_21	2026-07-13 09:23:24.254302+00	2026-07-13 09:23:24.254302+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC021	Đỗ Tuấn Kiệt	stu_demo_108goak_21	\N	stu_demo_108goak_21@student.demo.erg.edu.vn	MALE	2010-10-22 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0992712259	\N	\N	2025-10-13 09:23:24.166603+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502604.166603100, "completedAssignments": 13}
stu_demo_108goak_22	2026-07-13 09:23:24.690907+00	2026-07-13 09:23:24.690907+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC022	Nguyễn Quang Huy	stu_demo_108goak_22	\N	stu_demo_108goak_22@student.demo.erg.edu.vn	FEMALE	2010-11-23 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0992712260	\N	\N	2025-09-13 09:23:24.601465+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416204.601464700, "completedAssignments": 14}
stu_demo_108goak_23	2026-07-13 09:23:25.12355+00	2026-07-13 09:23:25.12355+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC023	Trần Minh Khang	stu_demo_108goak_23	\N	stu_demo_108goak_23@student.demo.erg.edu.vn	MALE	2010-12-24 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0992712261	\N	\N	2025-08-13 09:23:25.034902+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329805.034901500, "completedAssignments": 15}
stu_demo_108goak_24	2026-07-13 09:23:25.554295+00	2026-07-13 09:23:25.554295+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_112	ERGAIW7IC024	Lê Khánh Linh	stu_demo_108goak_24	\N	stu_demo_108goak_24@student.demo.erg.edu.vn	FEMALE	2010-01-25 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0992712262	\N	\N	2025-07-13 09:23:25.468581+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934605.468580800, "completedAssignments": 16}
stu_demo_108gobe_1	2026-07-13 09:23:26.427006+00	2026-07-13 09:23:26.427006+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC001	Phạm Tuấn Kiệt	stu_demo_108gobe_1	\N	stu_demo_108gobe_1@student.demo.erg.edu.vn	MALE	2010-02-02 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0946511699	\N	\N	2025-12-13 09:23:26.341148+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848206.341147700, "completedAssignments": 9}
stu_demo_108gobe_2	2026-07-13 09:23:26.864654+00	2026-07-13 09:23:26.864654+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC002	Võ Quang Huy	stu_demo_108gobe_2	\N	stu_demo_108gobe_2@student.demo.erg.edu.vn	FEMALE	2010-03-03 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0946511698	\N	\N	2025-11-13 09:23:26.777565+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761806.777565300, "completedAssignments": 10}
stu_demo_108gobe_3	2026-07-13 09:23:27.294329+00	2026-07-13 09:23:27.294329+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC003	Đặng Minh Khang	stu_demo_108gobe_3	\N	stu_demo_108gobe_3@student.demo.erg.edu.vn	MALE	2010-04-04 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0946511697	\N	\N	2025-10-13 09:23:27.208426+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675407.208426300, "completedAssignments": 11}
stu_demo_108gobe_4	2026-07-13 09:23:27.730633+00	2026-07-13 09:23:27.730633+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC004	Bùi Khánh Linh	stu_demo_108gobe_4	\N	stu_demo_108gobe_4@student.demo.erg.edu.vn	FEMALE	2010-05-05 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0946511696	\N	\N	2025-09-13 09:23:27.640875+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783589007.640874800, "completedAssignments": 12}
stu_demo_108gobe_5	2026-07-13 09:23:28.181391+00	2026-07-13 09:23:28.181391+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC005	Đỗ Thảo Vy	stu_demo_108gobe_5	\N	stu_demo_108gobe_5@student.demo.erg.edu.vn	MALE	2010-06-06 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0946511695	\N	\N	2025-08-13 09:23:28.086148+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502608.086148200, "completedAssignments": 13}
stu_demo_108gobe_6	2026-07-13 09:23:28.611537+00	2026-07-13 09:23:28.611537+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC006	Nguyễn Gia Hân	stu_demo_108gobe_6	\N	stu_demo_108gobe_6@student.demo.erg.edu.vn	FEMALE	2010-07-07 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0946511694	\N	\N	2025-07-13 09:23:28.524006+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416208.524005500, "completedAssignments": 14}
stu_demo_108gobe_7	2026-07-13 09:23:29.043612+00	2026-07-13 09:23:29.043612+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC007	Trần Đức Anh	stu_demo_108gobe_7	\N	stu_demo_108gobe_7@student.demo.erg.edu.vn	MALE	2010-08-08 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0946511693	\N	\N	2025-06-13 09:23:28.957179+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329808.957178800, "completedAssignments": 15}
stu_demo_108gobe_8	2026-07-13 09:23:29.48648+00	2026-07-13 09:23:29.48648+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC008	Lê Hoàng Nam	stu_demo_108gobe_8	\N	stu_demo_108gobe_8@student.demo.erg.edu.vn	FEMALE	2010-09-09 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0946511692	\N	\N	2025-05-13 09:23:29.400268+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934609.400267700, "completedAssignments": 16}
stu_demo_108gobe_9	2026-07-13 09:23:29.923724+00	2026-07-13 09:23:29.923724+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC009	Phạm Ngọc Anh	stu_demo_108gobe_9	\N	stu_demo_108gobe_9@student.demo.erg.edu.vn	MALE	2010-10-10 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0946511691	\N	\N	2026-01-13 09:23:29.838408+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848209.838407900, "completedAssignments": 17}
stu_demo_108gobe_10	2026-07-13 09:23:30.365329+00	2026-07-13 09:23:30.365329+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC010	Võ Bảo Ngọc	stu_demo_108gobe_10	\N	stu_demo_108gobe_10@student.demo.erg.edu.vn	FEMALE	2010-11-11 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0992712227	\N	\N	2025-12-13 09:23:30.278804+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761810.278804400, "completedAssignments": 18}
stu_demo_108gobe_11	2026-07-13 09:23:30.798452+00	2026-07-13 09:23:30.798452+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC011	Đặng Tuấn Kiệt	stu_demo_108gobe_11	\N	stu_demo_108gobe_11@student.demo.erg.edu.vn	MALE	2010-12-12 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0992712228	\N	\N	2025-11-13 09:23:30.712498+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675410.712498100, "completedAssignments": 19}
stu_demo_108gobe_12	2026-07-13 09:23:31.233978+00	2026-07-13 09:23:31.233978+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC012	Bùi Quang Huy	stu_demo_108gobe_12	\N	stu_demo_108gobe_12@student.demo.erg.edu.vn	FEMALE	2010-01-13 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0992712229	\N	\N	2025-10-13 09:23:31.146397+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589011.146396800, "completedAssignments": 20}
stu_demo_108gobe_13	2026-07-13 09:23:31.66538+00	2026-07-13 09:23:31.66538+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC013	Đỗ Minh Khang	stu_demo_108gobe_13	\N	stu_demo_108gobe_13@student.demo.erg.edu.vn	MALE	2010-02-14 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0992712230	\N	\N	2025-09-13 09:23:31.57848+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502611.578479700, "completedAssignments": 21}
stu_demo_108gobe_14	2026-07-13 09:23:32.093485+00	2026-07-13 09:23:32.093485+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC014	Nguyễn Khánh Linh	stu_demo_108gobe_14	\N	stu_demo_108gobe_14@student.demo.erg.edu.vn	FEMALE	2010-03-15 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0992712231	\N	\N	2025-08-13 09:23:32.007824+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416212.007823500, "completedAssignments": 22}
stu_demo_108gobe_15	2026-07-13 09:23:32.529994+00	2026-07-13 09:23:32.529994+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC015	Trần Thảo Vy	stu_demo_108gobe_15	\N	stu_demo_108gobe_15@student.demo.erg.edu.vn	MALE	2010-04-16 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0992712232	\N	\N	2025-07-13 09:23:32.439192+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329812.439191600, "completedAssignments": 23}
stu_demo_108gobe_16	2026-07-13 09:23:32.963477+00	2026-07-13 09:23:32.963477+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC016	Lê Gia Hân	stu_demo_108gobe_16	\N	stu_demo_108gobe_16@student.demo.erg.edu.vn	FEMALE	2010-05-17 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0992712233	\N	\N	2025-06-13 09:23:32.876371+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934612.876371300, "completedAssignments": 8}
stu_demo_108gobe_17	2026-07-13 09:23:33.405384+00	2026-07-13 09:23:33.405384+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC017	Phạm Đức Anh	stu_demo_108gobe_17	\N	stu_demo_108gobe_17@student.demo.erg.edu.vn	MALE	2010-06-18 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0992712234	\N	\N	2025-05-13 09:23:33.319437+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848213.319437400, "completedAssignments": 9}
stu_demo_108gobe_18	2026-07-13 09:23:33.838512+00	2026-07-13 09:23:33.838512+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC018	Võ Hoàng Nam	stu_demo_108gobe_18	\N	stu_demo_108gobe_18@student.demo.erg.edu.vn	FEMALE	2010-07-19 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0992712235	\N	\N	2026-01-13 09:23:33.752467+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761813.752466500, "completedAssignments": 10}
stu_demo_108gobe_19	2026-07-13 09:23:34.271081+00	2026-07-13 09:23:34.271081+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC019	Đặng Ngọc Anh	stu_demo_108gobe_19	\N	stu_demo_108gobe_19@student.demo.erg.edu.vn	MALE	2010-08-20 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0992712236	\N	\N	2025-12-13 09:23:34.183326+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675414.183325700, "completedAssignments": 11}
stu_demo_108gobe_20	2026-07-13 09:23:34.702516+00	2026-07-13 09:23:34.702516+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC020	Bùi Bảo Ngọc	stu_demo_108gobe_20	\N	stu_demo_108gobe_20@student.demo.erg.edu.vn	FEMALE	2010-09-21 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0992712258	\N	\N	2025-11-13 09:23:34.616295+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589014.616295400, "completedAssignments": 12}
stu_demo_108gobe_21	2026-07-13 09:23:35.150898+00	2026-07-13 09:23:35.150898+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC021	Đỗ Tuấn Kiệt	stu_demo_108gobe_21	\N	stu_demo_108gobe_21@student.demo.erg.edu.vn	MALE	2010-10-22 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0992712259	\N	\N	2025-10-13 09:23:35.057657+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502615.057657300, "completedAssignments": 13}
stu_demo_108gobe_22	2026-07-13 09:23:35.585855+00	2026-07-13 09:23:35.585855+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC022	Nguyễn Quang Huy	stu_demo_108gobe_22	\N	stu_demo_108gobe_22@student.demo.erg.edu.vn	FEMALE	2010-11-23 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0992712260	\N	\N	2025-09-13 09:23:35.499158+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416215.499158000, "completedAssignments": 14}
stu_demo_108gobe_23	2026-07-13 09:23:36.017304+00	2026-07-13 09:23:36.017304+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC023	Trần Minh Khang	stu_demo_108gobe_23	\N	stu_demo_108gobe_23@student.demo.erg.edu.vn	MALE	2010-12-24 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0992712261	\N	\N	2025-08-13 09:23:35.93131+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329815.931310300, "completedAssignments": 15}
stu_demo_108gobe_24	2026-07-13 09:23:36.451531+00	2026-07-13 09:23:36.451531+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_121	ERGAIW7IC024	Lê Khánh Linh	stu_demo_108gobe_24	\N	stu_demo_108gobe_24@student.demo.erg.edu.vn	FEMALE	2010-01-25 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0992712262	\N	\N	2025-07-13 09:23:36.363057+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934616.363056600, "completedAssignments": 16}
stu_demo_108gobf_1	2026-07-13 09:23:37.343365+00	2026-07-13 09:23:37.343365+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC001	Phạm Tuấn Kiệt	stu_demo_108gobf_1	\N	stu_demo_108gobf_1@student.demo.erg.edu.vn	MALE	2010-02-02 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0946511699	\N	\N	2025-12-13 09:23:37.247893+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848217.247892600, "completedAssignments": 9}
stu_demo_108gobf_2	2026-07-13 09:23:37.773377+00	2026-07-13 09:23:37.773377+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC002	Võ Quang Huy	stu_demo_108gobf_2	\N	stu_demo_108gobf_2@student.demo.erg.edu.vn	FEMALE	2010-03-03 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0946511698	\N	\N	2025-11-13 09:23:37.686876+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761817.686876000, "completedAssignments": 10}
stu_demo_108gobf_3	2026-07-13 09:23:38.209912+00	2026-07-13 09:23:38.209912+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC003	Đặng Minh Khang	stu_demo_108gobf_3	\N	stu_demo_108gobf_3@student.demo.erg.edu.vn	MALE	2010-04-04 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0946511697	\N	\N	2025-10-13 09:23:38.118396+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675418.118395500, "completedAssignments": 11}
stu_demo_108gobf_4	2026-07-13 09:23:38.64266+00	2026-07-13 09:23:38.64266+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC004	Bùi Khánh Linh	stu_demo_108gobf_4	\N	stu_demo_108gobf_4@student.demo.erg.edu.vn	FEMALE	2010-05-05 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0946511696	\N	\N	2025-09-13 09:23:38.555603+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783589018.555602500, "completedAssignments": 12}
stu_demo_108gobf_5	2026-07-13 09:23:39.075732+00	2026-07-13 09:23:39.075732+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC005	Đỗ Thảo Vy	stu_demo_108gobf_5	\N	stu_demo_108gobf_5@student.demo.erg.edu.vn	MALE	2010-06-06 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0946511695	\N	\N	2025-08-13 09:23:38.988417+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502618.988416500, "completedAssignments": 13}
stu_demo_108gobf_6	2026-07-13 09:23:39.507584+00	2026-07-13 09:23:39.507584+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC006	Nguyễn Gia Hân	stu_demo_108gobf_6	\N	stu_demo_108gobf_6@student.demo.erg.edu.vn	FEMALE	2010-07-07 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0946511694	\N	\N	2025-07-13 09:23:39.419473+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416219.419473000, "completedAssignments": 14}
stu_demo_108gobf_7	2026-07-13 09:23:39.943082+00	2026-07-13 09:23:39.943082+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC007	Trần Đức Anh	stu_demo_108gobf_7	\N	stu_demo_108gobf_7@student.demo.erg.edu.vn	MALE	2010-08-08 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0946511693	\N	\N	2025-06-13 09:23:39.855772+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329819.855771500, "completedAssignments": 15}
stu_demo_108gobf_8	2026-07-13 09:23:40.37629+00	2026-07-13 09:23:40.37629+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC008	Lê Hoàng Nam	stu_demo_108gobf_8	\N	stu_demo_108gobf_8@student.demo.erg.edu.vn	FEMALE	2010-09-09 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0946511692	\N	\N	2025-05-13 09:23:40.290611+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934620.290610600, "completedAssignments": 16}
stu_demo_108gobf_9	2026-07-13 09:23:40.813543+00	2026-07-13 09:23:40.813543+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC009	Phạm Ngọc Anh	stu_demo_108gobf_9	\N	stu_demo_108gobf_9@student.demo.erg.edu.vn	MALE	2010-10-10 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0946511691	\N	\N	2026-01-13 09:23:40.721551+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848220.721550800, "completedAssignments": 17}
stu_demo_108gobf_10	2026-07-13 09:23:41.260445+00	2026-07-13 09:23:41.260445+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC010	Võ Bảo Ngọc	stu_demo_108gobf_10	\N	stu_demo_108gobf_10@student.demo.erg.edu.vn	FEMALE	2010-11-11 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0992712227	\N	\N	2025-12-13 09:23:41.167473+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761821.167473100, "completedAssignments": 18}
stu_demo_108gobf_11	2026-07-13 09:23:41.694136+00	2026-07-13 09:23:41.694136+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC011	Đặng Tuấn Kiệt	stu_demo_108gobf_11	\N	stu_demo_108gobf_11@student.demo.erg.edu.vn	MALE	2010-12-12 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0992712228	\N	\N	2025-11-13 09:23:41.607718+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675421.607717600, "completedAssignments": 19}
stu_demo_108gobf_12	2026-07-13 09:23:42.127824+00	2026-07-13 09:23:42.127824+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC012	Bùi Quang Huy	stu_demo_108gobf_12	\N	stu_demo_108gobf_12@student.demo.erg.edu.vn	FEMALE	2010-01-13 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0992712229	\N	\N	2025-10-13 09:23:42.040276+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589022.040276200, "completedAssignments": 20}
stu_demo_108gobf_13	2026-07-13 09:23:42.565358+00	2026-07-13 09:23:42.565358+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC013	Đỗ Minh Khang	stu_demo_108gobf_13	\N	stu_demo_108gobf_13@student.demo.erg.edu.vn	MALE	2010-02-14 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0992712230	\N	\N	2025-09-13 09:23:42.479976+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502622.479976300, "completedAssignments": 21}
stu_demo_108gobf_14	2026-07-13 09:23:43.001158+00	2026-07-13 09:23:43.001158+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC014	Nguyễn Khánh Linh	stu_demo_108gobf_14	\N	stu_demo_108gobf_14@student.demo.erg.edu.vn	FEMALE	2010-03-15 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0992712231	\N	\N	2025-08-13 09:23:42.913636+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416222.913635900, "completedAssignments": 22}
stu_demo_108gobf_15	2026-07-13 09:23:43.443419+00	2026-07-13 09:23:43.443419+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC015	Trần Thảo Vy	stu_demo_108gobf_15	\N	stu_demo_108gobf_15@student.demo.erg.edu.vn	MALE	2010-04-16 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0992712232	\N	\N	2025-07-13 09:23:43.357091+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329823.357091200, "completedAssignments": 23}
stu_demo_108gobf_16	2026-07-13 09:23:43.880901+00	2026-07-13 09:23:43.880901+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC016	Lê Gia Hân	stu_demo_108gobf_16	\N	stu_demo_108gobf_16@student.demo.erg.edu.vn	FEMALE	2010-05-17 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0992712233	\N	\N	2025-06-13 09:23:43.79369+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934623.793689800, "completedAssignments": 8}
stu_demo_108gobf_17	2026-07-13 09:23:44.315254+00	2026-07-13 09:23:44.315254+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC017	Phạm Đức Anh	stu_demo_108gobf_17	\N	stu_demo_108gobf_17@student.demo.erg.edu.vn	MALE	2010-06-18 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0992712234	\N	\N	2025-05-13 09:23:44.228261+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848224.228261200, "completedAssignments": 9}
stu_demo_108gobf_18	2026-07-13 09:23:44.753925+00	2026-07-13 09:23:44.753925+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC018	Võ Hoàng Nam	stu_demo_108gobf_18	\N	stu_demo_108gobf_18@student.demo.erg.edu.vn	FEMALE	2010-07-19 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0992712235	\N	\N	2026-01-13 09:23:44.668236+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761824.668235900, "completedAssignments": 10}
stu_demo_108gobf_19	2026-07-13 09:23:45.186662+00	2026-07-13 09:23:45.186662+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC019	Đặng Ngọc Anh	stu_demo_108gobf_19	\N	stu_demo_108gobf_19@student.demo.erg.edu.vn	MALE	2010-08-20 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0992712236	\N	\N	2025-12-13 09:23:45.101338+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675425.101338100, "completedAssignments": 11}
stu_demo_108gobf_20	2026-07-13 09:23:45.61657+00	2026-07-13 09:23:45.61657+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC020	Bùi Bảo Ngọc	stu_demo_108gobf_20	\N	stu_demo_108gobf_20@student.demo.erg.edu.vn	FEMALE	2010-09-21 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0992712258	\N	\N	2025-11-13 09:23:45.530325+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589025.530324700, "completedAssignments": 12}
stu_demo_108gobf_21	2026-07-13 09:23:46.058052+00	2026-07-13 09:23:46.058052+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC021	Đỗ Tuấn Kiệt	stu_demo_108gobf_21	\N	stu_demo_108gobf_21@student.demo.erg.edu.vn	MALE	2010-10-22 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0992712259	\N	\N	2025-10-13 09:23:45.965588+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502625.965588200, "completedAssignments": 13}
stu_demo_108gobf_22	2026-07-13 09:23:46.493966+00	2026-07-13 09:23:46.493966+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC022	Nguyễn Quang Huy	stu_demo_108gobf_22	\N	stu_demo_108gobf_22@student.demo.erg.edu.vn	FEMALE	2010-11-23 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0992712260	\N	\N	2025-09-13 09:23:46.40759+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416226.407589700, "completedAssignments": 14}
stu_demo_108gobf_23	2026-07-13 09:23:46.93064+00	2026-07-13 09:23:46.93064+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC023	Trần Minh Khang	stu_demo_108gobf_23	\N	stu_demo_108gobf_23@student.demo.erg.edu.vn	MALE	2010-12-24 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0992712261	\N	\N	2025-08-13 09:23:46.842035+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329826.842035300, "completedAssignments": 15}
stu_demo_108gobf_24	2026-07-13 09:23:47.380552+00	2026-07-13 09:23:47.380552+00	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	cls_demo_aiw7ic_122	ERGAIW7IC024	Lê Khánh Linh	stu_demo_108gobf_24	\N	stu_demo_108gobf_24@student.demo.erg.edu.vn	FEMALE	2010-01-25 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0992712262	\N	\N	2025-07-13 09:23:47.294446+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934627.294445500, "completedAssignments": 16}
stu_demo_17gyaa4_1	2026-07-13 09:23:50.26313+00	2026-07-13 09:23:50.26313+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT001	Võ Hoàng Nam	stu_demo_17gyaa4_1	\N	stu_demo_17gyaa4_1@student.demo.erg.edu.vn	MALE	2009-02-02 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0910431572	\N	\N	2025-12-13 09:23:50.177239+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848230.177238800, "completedAssignments": 9}
stu_demo_17gyaa4_2	2026-07-13 09:23:50.695734+00	2026-07-13 09:23:50.695734+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT002	Đặng Ngọc Anh	stu_demo_17gyaa4_2	\N	stu_demo_17gyaa4_2@student.demo.erg.edu.vn	FEMALE	2009-03-03 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0910431573	\N	\N	2025-11-13 09:23:50.608744+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761830.608743800, "completedAssignments": 10}
stu_demo_17gyaa4_3	2026-07-13 09:23:51.129793+00	2026-07-13 09:23:51.129793+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT003	Bùi Bảo Ngọc	stu_demo_17gyaa4_3	\N	stu_demo_17gyaa4_3@student.demo.erg.edu.vn	MALE	2009-04-04 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0910431574	\N	\N	2025-10-13 09:23:51.038255+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675431.038255100, "completedAssignments": 11}
stu_demo_17gyaa4_4	2026-07-13 09:23:51.80799+00	2026-07-13 09:23:51.80799+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT004	Đỗ Tuấn Kiệt	stu_demo_17gyaa4_4	\N	stu_demo_17gyaa4_4@student.demo.erg.edu.vn	FEMALE	2009-05-05 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0910431575	\N	\N	2025-09-13 09:23:51.70841+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783589031.708409500, "completedAssignments": 12}
stu_demo_17gyaa4_5	2026-07-13 09:23:52.240471+00	2026-07-13 09:23:52.240471+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT005	Nguyễn Quang Huy	stu_demo_17gyaa4_5	\N	stu_demo_17gyaa4_5@student.demo.erg.edu.vn	MALE	2009-06-06 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0910431576	\N	\N	2025-08-13 09:23:52.152142+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502632.152141500, "completedAssignments": 13}
stu_demo_17gyaa4_6	2026-07-13 09:23:52.678603+00	2026-07-13 09:23:52.678603+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT006	Trần Minh Khang	stu_demo_17gyaa4_6	\N	stu_demo_17gyaa4_6@student.demo.erg.edu.vn	FEMALE	2009-07-07 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0910431577	\N	\N	2025-07-13 09:23:52.592328+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416232.592327500, "completedAssignments": 14}
stu_demo_17gyaa4_7	2026-07-13 09:23:53.115607+00	2026-07-13 09:23:53.115607+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT007	Lê Khánh Linh	stu_demo_17gyaa4_7	\N	stu_demo_17gyaa4_7@student.demo.erg.edu.vn	MALE	2009-08-08 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0910431578	\N	\N	2025-06-13 09:23:53.026837+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329833.026837400, "completedAssignments": 15}
stu_demo_17gyaa4_8	2026-07-13 09:23:53.547171+00	2026-07-13 09:23:53.547171+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT008	Phạm Thảo Vy	stu_demo_17gyaa4_8	\N	stu_demo_17gyaa4_8@student.demo.erg.edu.vn	FEMALE	2009-09-09 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0910431579	\N	\N	2025-05-13 09:23:53.459082+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934633.459082200, "completedAssignments": 16}
stu_demo_17gyaa4_9	2026-07-13 09:23:53.983779+00	2026-07-13 09:23:53.983779+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT009	Võ Gia Hân	stu_demo_17gyaa4_9	\N	stu_demo_17gyaa4_9@student.demo.erg.edu.vn	MALE	2009-10-10 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0910431580	\N	\N	2026-01-13 09:23:53.896578+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848233.896578200, "completedAssignments": 17}
stu_demo_17gyaa4_10	2026-07-13 09:23:54.417688+00	2026-07-13 09:23:54.417688+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT010	Đặng Đức Anh	stu_demo_17gyaa4_10	\N	stu_demo_17gyaa4_10@student.demo.erg.edu.vn	FEMALE	2009-11-11 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0983771228	\N	\N	2025-12-13 09:23:54.332063+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761834.332063000, "completedAssignments": 18}
stu_demo_17gyaa4_11	2026-07-13 09:23:54.848147+00	2026-07-13 09:23:54.848147+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT011	Bùi Hoàng Nam	stu_demo_17gyaa4_11	\N	stu_demo_17gyaa4_11@student.demo.erg.edu.vn	MALE	2009-12-12 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0983771229	\N	\N	2025-11-13 09:23:54.762452+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675434.762452100, "completedAssignments": 19}
stu_demo_17gyaa4_12	2026-07-13 09:23:55.291022+00	2026-07-13 09:23:55.291022+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT012	Đỗ Ngọc Anh	stu_demo_17gyaa4_12	\N	stu_demo_17gyaa4_12@student.demo.erg.edu.vn	FEMALE	2009-01-13 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0983771230	\N	\N	2025-10-13 09:23:55.203954+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589035.203953600, "completedAssignments": 20}
stu_demo_17gyaa4_13	2026-07-13 09:23:55.722494+00	2026-07-13 09:23:55.722494+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT013	Nguyễn Bảo Ngọc	stu_demo_17gyaa4_13	\N	stu_demo_17gyaa4_13@student.demo.erg.edu.vn	MALE	2009-02-14 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0983771231	\N	\N	2025-09-13 09:23:55.636557+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502635.636557300, "completedAssignments": 21}
stu_demo_17gyaa4_14	2026-07-13 09:23:56.165102+00	2026-07-13 09:23:56.165102+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT014	Trần Tuấn Kiệt	stu_demo_17gyaa4_14	\N	stu_demo_17gyaa4_14@student.demo.erg.edu.vn	FEMALE	2009-03-15 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0983771232	\N	\N	2025-08-13 09:23:56.078318+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416236.078318400, "completedAssignments": 22}
stu_demo_17gyaa4_15	2026-07-13 09:23:56.607949+00	2026-07-13 09:23:56.607949+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT015	Lê Quang Huy	stu_demo_17gyaa4_15	\N	stu_demo_17gyaa4_15@student.demo.erg.edu.vn	MALE	2009-04-16 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0983771233	\N	\N	2025-07-13 09:23:56.519734+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329836.519733700, "completedAssignments": 23}
stu_demo_17gyaa4_16	2026-07-13 09:23:57.053558+00	2026-07-13 09:23:57.053558+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT016	Phạm Minh Khang	stu_demo_17gyaa4_16	\N	stu_demo_17gyaa4_16@student.demo.erg.edu.vn	FEMALE	2009-05-17 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0983771234	\N	\N	2025-06-13 09:23:56.959758+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934636.959757800, "completedAssignments": 8}
stu_demo_17gyaa4_17	2026-07-13 09:23:57.487284+00	2026-07-13 09:23:57.487284+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT017	Võ Khánh Linh	stu_demo_17gyaa4_17	\N	stu_demo_17gyaa4_17@student.demo.erg.edu.vn	MALE	2009-06-18 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0983771235	\N	\N	2025-05-13 09:23:57.400828+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848237.400827600, "completedAssignments": 9}
stu_demo_17gyaa4_18	2026-07-13 09:23:57.922326+00	2026-07-13 09:23:57.922326+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT018	Đặng Thảo Vy	stu_demo_17gyaa4_18	\N	stu_demo_17gyaa4_18@student.demo.erg.edu.vn	FEMALE	2009-07-19 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0983771236	\N	\N	2026-01-13 09:23:57.834043+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761837.834043300, "completedAssignments": 10}
stu_demo_17gyaa4_19	2026-07-13 09:23:58.357221+00	2026-07-13 09:23:58.357221+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT019	Bùi Gia Hân	stu_demo_17gyaa4_19	\N	stu_demo_17gyaa4_19@student.demo.erg.edu.vn	MALE	2009-08-20 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0983771237	\N	\N	2025-12-13 09:23:58.268967+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675438.268967200, "completedAssignments": 11}
stu_demo_17gyaa4_20	2026-07-13 09:23:58.79273+00	2026-07-13 09:23:58.79273+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT020	Đỗ Đức Anh	stu_demo_17gyaa4_20	\N	stu_demo_17gyaa4_20@student.demo.erg.edu.vn	FEMALE	2009-09-21 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0983771259	\N	\N	2025-11-13 09:23:58.704417+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589038.704417100, "completedAssignments": 12}
stu_demo_17gyaa4_21	2026-07-13 09:23:59.230111+00	2026-07-13 09:23:59.230111+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT021	Nguyễn Hoàng Nam	stu_demo_17gyaa4_21	\N	stu_demo_17gyaa4_21@student.demo.erg.edu.vn	MALE	2009-10-22 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0983771260	\N	\N	2025-10-13 09:23:59.143988+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502639.143987600, "completedAssignments": 13}
stu_demo_17gyaa4_22	2026-07-13 09:23:59.659894+00	2026-07-13 09:23:59.659894+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT022	Trần Ngọc Anh	stu_demo_17gyaa4_22	\N	stu_demo_17gyaa4_22@student.demo.erg.edu.vn	FEMALE	2009-11-23 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0983771261	\N	\N	2025-09-13 09:23:59.574145+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416239.574144800, "completedAssignments": 14}
stu_demo_17gyaa4_23	2026-07-13 09:24:00.110738+00	2026-07-13 09:24:00.110738+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT023	Lê Bảo Ngọc	stu_demo_17gyaa4_23	\N	stu_demo_17gyaa4_23@student.demo.erg.edu.vn	MALE	2009-12-24 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0983771262	\N	\N	2025-08-13 09:24:00.024048+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329840.024047700, "completedAssignments": 15}
stu_demo_17gyaa4_24	2026-07-13 09:24:00.55339+00	2026-07-13 09:24:00.55339+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a1	ERG1AQLLKT024	Phạm Tuấn Kiệt	stu_demo_17gyaa4_24	\N	stu_demo_17gyaa4_24@student.demo.erg.edu.vn	FEMALE	2009-01-25 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0983771263	\N	\N	2025-07-13 09:24:00.467021+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934640.467020800, "completedAssignments": 16}
stu_demo_17gyaa5_1	2026-07-13 09:24:01.434932+00	2026-07-13 09:24:01.434932+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT001	Võ Hoàng Nam	stu_demo_17gyaa5_1	\N	stu_demo_17gyaa5_1@student.demo.erg.edu.vn	MALE	2009-02-02 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0910431572	\N	\N	2025-12-13 09:24:01.348269+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848241.348268900, "completedAssignments": 9}
stu_demo_17gyaa5_2	2026-07-13 09:24:01.878756+00	2026-07-13 09:24:01.878756+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT002	Đặng Ngọc Anh	stu_demo_17gyaa5_2	\N	stu_demo_17gyaa5_2@student.demo.erg.edu.vn	FEMALE	2009-03-03 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0910431573	\N	\N	2025-11-13 09:24:01.789595+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761841.789594900, "completedAssignments": 10}
stu_demo_17gyaa5_3	2026-07-13 09:24:02.319759+00	2026-07-13 09:24:02.319759+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT003	Bùi Bảo Ngọc	stu_demo_17gyaa5_3	\N	stu_demo_17gyaa5_3@student.demo.erg.edu.vn	MALE	2009-04-04 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0910431574	\N	\N	2025-10-13 09:24:02.232277+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675442.232276900, "completedAssignments": 11}
stu_demo_17gyaa5_4	2026-07-13 09:24:02.766557+00	2026-07-13 09:24:02.766557+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT004	Đỗ Tuấn Kiệt	stu_demo_17gyaa5_4	\N	stu_demo_17gyaa5_4@student.demo.erg.edu.vn	FEMALE	2009-05-05 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0910431575	\N	\N	2025-09-13 09:24:02.671396+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783589042.671396300, "completedAssignments": 12}
stu_demo_17gyaa5_5	2026-07-13 09:24:03.206532+00	2026-07-13 09:24:03.206532+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT005	Nguyễn Quang Huy	stu_demo_17gyaa5_5	\N	stu_demo_17gyaa5_5@student.demo.erg.edu.vn	MALE	2009-06-06 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0910431576	\N	\N	2025-08-13 09:24:03.119881+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502643.119881100, "completedAssignments": 13}
stu_demo_17gyaa5_6	2026-07-13 09:24:03.640975+00	2026-07-13 09:24:03.640975+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT006	Trần Minh Khang	stu_demo_17gyaa5_6	\N	stu_demo_17gyaa5_6@student.demo.erg.edu.vn	FEMALE	2009-07-07 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0910431577	\N	\N	2025-07-13 09:24:03.554906+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416243.554905700, "completedAssignments": 14}
stu_demo_17gyaa5_7	2026-07-13 09:24:04.071502+00	2026-07-13 09:24:04.071502+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT007	Lê Khánh Linh	stu_demo_17gyaa5_7	\N	stu_demo_17gyaa5_7@student.demo.erg.edu.vn	MALE	2009-08-08 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0910431578	\N	\N	2025-06-13 09:24:03.984778+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329843.985781500, "completedAssignments": 15}
stu_demo_17gyaa5_8	2026-07-13 09:24:04.50726+00	2026-07-13 09:24:04.50726+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT008	Phạm Thảo Vy	stu_demo_17gyaa5_8	\N	stu_demo_17gyaa5_8@student.demo.erg.edu.vn	FEMALE	2009-09-09 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0910431579	\N	\N	2025-05-13 09:24:04.418196+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934644.418196400, "completedAssignments": 16}
stu_demo_17gyaa5_9	2026-07-13 09:24:04.960433+00	2026-07-13 09:24:04.960433+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT009	Võ Gia Hân	stu_demo_17gyaa5_9	\N	stu_demo_17gyaa5_9@student.demo.erg.edu.vn	MALE	2009-10-10 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0910431580	\N	\N	2026-01-13 09:24:04.871752+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848244.871751900, "completedAssignments": 17}
stu_demo_17gyaa5_10	2026-07-13 09:24:05.390029+00	2026-07-13 09:24:05.390029+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT010	Đặng Đức Anh	stu_demo_17gyaa5_10	\N	stu_demo_17gyaa5_10@student.demo.erg.edu.vn	FEMALE	2009-11-11 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0983771228	\N	\N	2025-12-13 09:24:05.304828+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761845.304827800, "completedAssignments": 18}
stu_demo_17gyaa5_11	2026-07-13 09:24:05.818945+00	2026-07-13 09:24:05.818945+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT011	Bùi Hoàng Nam	stu_demo_17gyaa5_11	\N	stu_demo_17gyaa5_11@student.demo.erg.edu.vn	MALE	2009-12-12 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0983771229	\N	\N	2025-11-13 09:24:05.733268+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675445.733267900, "completedAssignments": 19}
stu_demo_17gyaa5_12	2026-07-13 09:24:06.273083+00	2026-07-13 09:24:06.273083+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT012	Đỗ Ngọc Anh	stu_demo_17gyaa5_12	\N	stu_demo_17gyaa5_12@student.demo.erg.edu.vn	FEMALE	2009-01-13 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0983771230	\N	\N	2025-10-13 09:24:06.183673+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589046.183673300, "completedAssignments": 20}
stu_demo_17gyaa5_13	2026-07-13 09:24:06.723008+00	2026-07-13 09:24:06.723008+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT013	Nguyễn Bảo Ngọc	stu_demo_17gyaa5_13	\N	stu_demo_17gyaa5_13@student.demo.erg.edu.vn	MALE	2009-02-14 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0983771231	\N	\N	2025-09-13 09:24:06.637421+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502646.637420700, "completedAssignments": 21}
stu_demo_17gyaa5_14	2026-07-13 09:24:07.154735+00	2026-07-13 09:24:07.154735+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT014	Trần Tuấn Kiệt	stu_demo_17gyaa5_14	\N	stu_demo_17gyaa5_14@student.demo.erg.edu.vn	FEMALE	2009-03-15 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0983771232	\N	\N	2025-08-13 09:24:07.068394+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416247.068394100, "completedAssignments": 22}
stu_demo_17gyaa5_15	2026-07-13 09:24:07.600703+00	2026-07-13 09:24:07.600703+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT015	Lê Quang Huy	stu_demo_17gyaa5_15	\N	stu_demo_17gyaa5_15@student.demo.erg.edu.vn	MALE	2009-04-16 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0983771233	\N	\N	2025-07-13 09:24:07.514193+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329847.514193300, "completedAssignments": 23}
stu_demo_17gyaa5_16	2026-07-13 09:24:08.033362+00	2026-07-13 09:24:08.033362+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT016	Phạm Minh Khang	stu_demo_17gyaa5_16	\N	stu_demo_17gyaa5_16@student.demo.erg.edu.vn	FEMALE	2009-05-17 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0983771234	\N	\N	2025-06-13 09:24:07.947222+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934647.947221500, "completedAssignments": 8}
stu_demo_17gyaa5_17	2026-07-13 09:24:08.493625+00	2026-07-13 09:24:08.493625+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT017	Võ Khánh Linh	stu_demo_17gyaa5_17	\N	stu_demo_17gyaa5_17@student.demo.erg.edu.vn	MALE	2009-06-18 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0983771235	\N	\N	2025-05-13 09:24:08.406564+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848248.406564000, "completedAssignments": 9}
stu_demo_17gyaa5_18	2026-07-13 09:24:08.937257+00	2026-07-13 09:24:08.937257+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT018	Đặng Thảo Vy	stu_demo_17gyaa5_18	\N	stu_demo_17gyaa5_18@student.demo.erg.edu.vn	FEMALE	2009-07-19 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0983771236	\N	\N	2026-01-13 09:24:08.844216+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761848.844215700, "completedAssignments": 10}
stu_demo_17gyaa5_19	2026-07-13 09:24:09.370217+00	2026-07-13 09:24:09.370217+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT019	Bùi Gia Hân	stu_demo_17gyaa5_19	\N	stu_demo_17gyaa5_19@student.demo.erg.edu.vn	MALE	2009-08-20 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0983771237	\N	\N	2025-12-13 09:24:09.281908+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675449.281908100, "completedAssignments": 11}
stu_demo_17gyaa5_20	2026-07-13 09:24:09.821505+00	2026-07-13 09:24:09.821505+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT020	Đỗ Đức Anh	stu_demo_17gyaa5_20	\N	stu_demo_17gyaa5_20@student.demo.erg.edu.vn	FEMALE	2009-09-21 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0983771259	\N	\N	2025-11-13 09:24:09.719683+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589049.719683000, "completedAssignments": 12}
stu_demo_17gyaa5_21	2026-07-13 09:24:10.256302+00	2026-07-13 09:24:10.256302+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT021	Nguyễn Hoàng Nam	stu_demo_17gyaa5_21	\N	stu_demo_17gyaa5_21@student.demo.erg.edu.vn	MALE	2009-10-22 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0983771260	\N	\N	2025-10-13 09:24:10.169361+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502650.170870200, "completedAssignments": 13}
stu_demo_17gyaa5_22	2026-07-13 09:24:10.689713+00	2026-07-13 09:24:10.689713+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT022	Trần Ngọc Anh	stu_demo_17gyaa5_22	\N	stu_demo_17gyaa5_22@student.demo.erg.edu.vn	FEMALE	2009-11-23 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0983771261	\N	\N	2025-09-13 09:24:10.603018+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416250.603018000, "completedAssignments": 14}
stu_demo_17gyaa5_23	2026-07-13 09:24:11.141903+00	2026-07-13 09:24:11.141903+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT023	Lê Bảo Ngọc	stu_demo_17gyaa5_23	\N	stu_demo_17gyaa5_23@student.demo.erg.edu.vn	MALE	2009-12-24 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0983771262	\N	\N	2025-08-13 09:24:11.056251+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329851.056251200, "completedAssignments": 15}
stu_demo_17gyaa5_24	2026-07-13 09:24:11.581064+00	2026-07-13 09:24:11.581064+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_a2	ERG1AQLLKT024	Phạm Tuấn Kiệt	stu_demo_17gyaa5_24	\N	stu_demo_17gyaa5_24@student.demo.erg.edu.vn	FEMALE	2009-01-25 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0983771263	\N	\N	2025-07-13 09:24:11.488555+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934651.488555400, "completedAssignments": 16}
stu_demo_17gyaaz_1	2026-07-13 09:24:12.449096+00	2026-07-13 09:24:12.449096+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT001	Võ Hoàng Nam	stu_demo_17gyaaz_1	\N	stu_demo_17gyaaz_1@student.demo.erg.edu.vn	MALE	2009-02-02 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0910431572	\N	\N	2025-12-13 09:24:12.361326+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848252.361326000, "completedAssignments": 9}
stu_demo_17gyaaz_2	2026-07-13 09:24:12.903511+00	2026-07-13 09:24:12.903511+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT002	Đặng Ngọc Anh	stu_demo_17gyaaz_2	\N	stu_demo_17gyaaz_2@student.demo.erg.edu.vn	FEMALE	2009-03-03 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0910431573	\N	\N	2025-11-13 09:24:12.809754+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761852.809754400, "completedAssignments": 10}
stu_demo_17gyaaz_3	2026-07-13 09:24:13.353723+00	2026-07-13 09:24:13.353723+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT003	Bùi Bảo Ngọc	stu_demo_17gyaaz_3	\N	stu_demo_17gyaaz_3@student.demo.erg.edu.vn	MALE	2009-04-04 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0910431574	\N	\N	2025-10-13 09:24:13.267077+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675453.267076500, "completedAssignments": 11}
stu_demo_17gyaaz_4	2026-07-13 09:24:13.798873+00	2026-07-13 09:24:13.798873+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT004	Đỗ Tuấn Kiệt	stu_demo_17gyaaz_4	\N	stu_demo_17gyaaz_4@student.demo.erg.edu.vn	FEMALE	2009-05-05 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0910431575	\N	\N	2025-09-13 09:24:13.712702+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783589053.712702300, "completedAssignments": 12}
stu_demo_17gyaaz_5	2026-07-13 09:24:14.234493+00	2026-07-13 09:24:14.234493+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT005	Nguyễn Quang Huy	stu_demo_17gyaaz_5	\N	stu_demo_17gyaaz_5@student.demo.erg.edu.vn	MALE	2009-06-06 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0910431576	\N	\N	2025-08-13 09:24:14.145809+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502654.145809300, "completedAssignments": 13}
stu_demo_17gyaaz_6	2026-07-13 09:24:14.689393+00	2026-07-13 09:24:14.689393+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT006	Trần Minh Khang	stu_demo_17gyaaz_6	\N	stu_demo_17gyaaz_6@student.demo.erg.edu.vn	FEMALE	2009-07-07 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0910431577	\N	\N	2025-07-13 09:24:14.585229+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416254.585229200, "completedAssignments": 14}
stu_demo_17gyaaz_7	2026-07-13 09:24:15.129635+00	2026-07-13 09:24:15.129635+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT007	Lê Khánh Linh	stu_demo_17gyaaz_7	\N	stu_demo_17gyaaz_7@student.demo.erg.edu.vn	MALE	2009-08-08 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0910431578	\N	\N	2025-06-13 09:24:15.042956+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329855.042955900, "completedAssignments": 15}
stu_demo_17gyaaz_8	2026-07-13 09:24:15.579873+00	2026-07-13 09:24:15.579873+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT008	Phạm Thảo Vy	stu_demo_17gyaaz_8	\N	stu_demo_17gyaaz_8@student.demo.erg.edu.vn	FEMALE	2009-09-09 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0910431579	\N	\N	2025-05-13 09:24:15.493081+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934655.494075200, "completedAssignments": 16}
stu_demo_17gyaaz_9	2026-07-13 09:24:16.013261+00	2026-07-13 09:24:16.013261+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT009	Võ Gia Hân	stu_demo_17gyaaz_9	\N	stu_demo_17gyaaz_9@student.demo.erg.edu.vn	MALE	2009-10-10 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0910431580	\N	\N	2026-01-13 09:24:15.926799+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848255.926798900, "completedAssignments": 17}
stu_demo_17gyaaz_10	2026-07-13 09:24:16.459286+00	2026-07-13 09:24:16.459286+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT010	Đặng Đức Anh	stu_demo_17gyaaz_10	\N	stu_demo_17gyaaz_10@student.demo.erg.edu.vn	FEMALE	2009-11-11 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0983771228	\N	\N	2025-12-13 09:24:16.371367+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761856.372363300, "completedAssignments": 18}
stu_demo_17gyaaz_11	2026-07-13 09:24:16.890515+00	2026-07-13 09:24:16.890515+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT011	Bùi Hoàng Nam	stu_demo_17gyaaz_11	\N	stu_demo_17gyaaz_11@student.demo.erg.edu.vn	MALE	2009-12-12 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0983771229	\N	\N	2025-11-13 09:24:16.803593+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675456.803593400, "completedAssignments": 19}
stu_demo_17gyaaz_12	2026-07-13 09:24:17.333106+00	2026-07-13 09:24:17.333106+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT012	Đỗ Ngọc Anh	stu_demo_17gyaaz_12	\N	stu_demo_17gyaaz_12@student.demo.erg.edu.vn	FEMALE	2009-01-13 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0983771230	\N	\N	2025-10-13 09:24:17.247171+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589057.247170900, "completedAssignments": 20}
stu_demo_17gyaaz_13	2026-07-13 09:24:17.772337+00	2026-07-13 09:24:17.772337+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT013	Nguyễn Bảo Ngọc	stu_demo_17gyaaz_13	\N	stu_demo_17gyaaz_13@student.demo.erg.edu.vn	MALE	2009-02-14 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0983771231	\N	\N	2025-09-13 09:24:17.685221+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502657.685220900, "completedAssignments": 21}
stu_demo_17gyaaz_14	2026-07-13 09:24:18.206376+00	2026-07-13 09:24:18.206376+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT014	Trần Tuấn Kiệt	stu_demo_17gyaaz_14	\N	stu_demo_17gyaaz_14@student.demo.erg.edu.vn	FEMALE	2009-03-15 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0983771232	\N	\N	2025-08-13 09:24:18.119773+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416258.119773400, "completedAssignments": 22}
stu_demo_17gyaaz_15	2026-07-13 09:24:18.638191+00	2026-07-13 09:24:18.638191+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT015	Lê Quang Huy	stu_demo_17gyaaz_15	\N	stu_demo_17gyaaz_15@student.demo.erg.edu.vn	MALE	2009-04-16 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0983771233	\N	\N	2025-07-13 09:24:18.551854+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329858.551854200, "completedAssignments": 23}
stu_demo_17gyaaz_16	2026-07-13 09:24:19.067922+00	2026-07-13 09:24:19.067922+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT016	Phạm Minh Khang	stu_demo_17gyaaz_16	\N	stu_demo_17gyaaz_16@student.demo.erg.edu.vn	FEMALE	2009-05-17 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0983771234	\N	\N	2025-06-13 09:24:18.981183+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934658.981183400, "completedAssignments": 8}
stu_demo_17gyaaz_17	2026-07-13 09:24:19.503402+00	2026-07-13 09:24:19.503402+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT017	Võ Khánh Linh	stu_demo_17gyaaz_17	\N	stu_demo_17gyaaz_17@student.demo.erg.edu.vn	MALE	2009-06-18 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0983771235	\N	\N	2025-05-13 09:24:19.416319+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848259.416318900, "completedAssignments": 9}
stu_demo_17gyaaz_18	2026-07-13 09:24:19.935056+00	2026-07-13 09:24:19.935056+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT018	Đặng Thảo Vy	stu_demo_17gyaaz_18	\N	stu_demo_17gyaaz_18@student.demo.erg.edu.vn	FEMALE	2009-07-19 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0983771236	\N	\N	2026-01-13 09:24:19.847758+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761859.847758200, "completedAssignments": 10}
stu_demo_17gyaaz_19	2026-07-13 09:24:20.374404+00	2026-07-13 09:24:20.374404+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT019	Bùi Gia Hân	stu_demo_17gyaaz_19	\N	stu_demo_17gyaaz_19@student.demo.erg.edu.vn	MALE	2009-08-20 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0983771237	\N	\N	2025-12-13 09:24:20.288332+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675460.288332200, "completedAssignments": 11}
stu_demo_17gyaaz_20	2026-07-13 09:24:20.815179+00	2026-07-13 09:24:20.815179+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT020	Đỗ Đức Anh	stu_demo_17gyaaz_20	\N	stu_demo_17gyaaz_20@student.demo.erg.edu.vn	FEMALE	2009-09-21 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0983771259	\N	\N	2025-11-13 09:24:20.725168+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589060.725168000, "completedAssignments": 12}
stu_demo_17gyaaz_21	2026-07-13 09:24:21.258222+00	2026-07-13 09:24:21.258222+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT021	Nguyễn Hoàng Nam	stu_demo_17gyaaz_21	\N	stu_demo_17gyaaz_21@student.demo.erg.edu.vn	MALE	2009-10-22 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0983771260	\N	\N	2025-10-13 09:24:21.170108+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502661.170108200, "completedAssignments": 13}
stu_demo_17gyaaz_22	2026-07-13 09:24:21.703659+00	2026-07-13 09:24:21.703659+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT022	Trần Ngọc Anh	stu_demo_17gyaaz_22	\N	stu_demo_17gyaaz_22@student.demo.erg.edu.vn	FEMALE	2009-11-23 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0983771261	\N	\N	2025-09-13 09:24:21.615344+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416261.615343800, "completedAssignments": 14}
stu_demo_17gyaaz_23	2026-07-13 09:24:22.15671+00	2026-07-13 09:24:22.15671+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT023	Lê Bảo Ngọc	stu_demo_17gyaaz_23	\N	stu_demo_17gyaaz_23@student.demo.erg.edu.vn	MALE	2009-12-24 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0983771262	\N	\N	2025-08-13 09:24:22.068309+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329862.068309400, "completedAssignments": 15}
stu_demo_17gyaaz_24	2026-07-13 09:24:22.58775+00	2026-07-13 09:24:22.58775+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b1	ERG1AQLLKT024	Phạm Tuấn Kiệt	stu_demo_17gyaaz_24	\N	stu_demo_17gyaaz_24@student.demo.erg.edu.vn	FEMALE	2009-01-25 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0983771263	\N	\N	2025-07-13 09:24:22.500498+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783934662.501502700, "completedAssignments": 16}
stu_demo_17gyab0_1	2026-07-13 09:24:23.453335+00	2026-07-13 09:24:23.453335+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT001	Võ Hoàng Nam	stu_demo_17gyab0_1	\N	stu_demo_17gyab0_1@student.demo.erg.edu.vn	MALE	2009-02-02 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0910431572	\N	\N	2025-12-13 09:24:23.36557+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783848263.365569600, "completedAssignments": 9}
stu_demo_17gyab0_2	2026-07-13 09:24:23.889494+00	2026-07-13 09:24:23.889494+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT002	Đặng Ngọc Anh	stu_demo_17gyab0_2	\N	stu_demo_17gyab0_2@student.demo.erg.edu.vn	FEMALE	2009-03-03 00:00:00+00	\N	\N	Phụ huynh Đặng Ngọc Anh	0910431573	\N	\N	2025-11-13 09:24:23.803367+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783761863.803367400, "completedAssignments": 10}
stu_demo_17gyab0_3	2026-07-13 09:24:24.318393+00	2026-07-13 09:24:24.318393+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT003	Bùi Bảo Ngọc	stu_demo_17gyab0_3	\N	stu_demo_17gyab0_3@student.demo.erg.edu.vn	MALE	2009-04-04 00:00:00+00	\N	\N	Phụ huynh Bùi Bảo Ngọc	0910431574	\N	\N	2025-10-13 09:24:24.233031+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783675464.233030600, "completedAssignments": 11}
stu_demo_17gyab0_4	2026-07-13 09:24:24.754665+00	2026-07-13 09:24:24.754665+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT004	Đỗ Tuấn Kiệt	stu_demo_17gyab0_4	\N	stu_demo_17gyab0_4@student.demo.erg.edu.vn	FEMALE	2009-05-05 00:00:00+00	\N	\N	Phụ huynh Đỗ Tuấn Kiệt	0910431575	\N	\N	2025-09-13 09:24:24.667608+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783589064.667607500, "completedAssignments": 12}
stu_demo_17gyab0_5	2026-07-13 09:24:25.19835+00	2026-07-13 09:24:25.19835+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT005	Nguyễn Quang Huy	stu_demo_17gyab0_5	\N	stu_demo_17gyab0_5@student.demo.erg.edu.vn	MALE	2009-06-06 00:00:00+00	\N	\N	Phụ huynh Nguyễn Quang Huy	0910431576	\N	\N	2025-08-13 09:24:25.110196+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783502665.110196300, "completedAssignments": 13}
stu_demo_17gyab0_6	2026-07-13 09:24:25.641032+00	2026-07-13 09:24:25.641032+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT006	Trần Minh Khang	stu_demo_17gyab0_6	\N	stu_demo_17gyab0_6@student.demo.erg.edu.vn	FEMALE	2009-07-07 00:00:00+00	\N	\N	Phụ huynh Trần Minh Khang	0910431577	\N	\N	2025-07-13 09:24:25.554449+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783416265.554449400, "completedAssignments": 14}
stu_demo_17gyab0_7	2026-07-13 09:24:26.071381+00	2026-07-13 09:24:26.071381+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT007	Lê Khánh Linh	stu_demo_17gyab0_7	\N	stu_demo_17gyab0_7@student.demo.erg.edu.vn	MALE	2009-08-08 00:00:00+00	\N	\N	Phụ huynh Lê Khánh Linh	0910431578	\N	\N	2025-06-13 09:24:25.985116+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783329865.985115900, "completedAssignments": 15}
stu_demo_17gyab0_8	2026-07-13 09:24:26.503384+00	2026-07-13 09:24:26.503384+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT008	Phạm Thảo Vy	stu_demo_17gyab0_8	\N	stu_demo_17gyab0_8@student.demo.erg.edu.vn	FEMALE	2009-09-09 00:00:00+00	\N	\N	Phụ huynh Phạm Thảo Vy	0910431579	\N	\N	2025-05-13 09:24:26.416192+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783934666.416191700, "completedAssignments": 16}
stu_demo_17gyab0_9	2026-07-13 09:24:26.948886+00	2026-07-13 09:24:26.948886+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT009	Võ Gia Hân	stu_demo_17gyab0_9	\N	stu_demo_17gyab0_9@student.demo.erg.edu.vn	MALE	2009-10-10 00:00:00+00	\N	\N	Phụ huynh Võ Gia Hân	0910431580	\N	\N	2026-01-13 09:24:26.861636+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783848266.861635900, "completedAssignments": 17}
stu_demo_17gyab0_10	2026-07-13 09:24:27.387371+00	2026-07-13 09:24:27.387371+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT010	Đặng Đức Anh	stu_demo_17gyab0_10	\N	stu_demo_17gyab0_10@student.demo.erg.edu.vn	FEMALE	2009-11-11 00:00:00+00	\N	\N	Phụ huynh Đặng Đức Anh	0983771228	\N	\N	2025-12-13 09:24:27.300409+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783761867.300408800, "completedAssignments": 18}
stu_demo_17gyab0_11	2026-07-13 09:24:27.820094+00	2026-07-13 09:24:27.820094+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT011	Bùi Hoàng Nam	stu_demo_17gyab0_11	\N	stu_demo_17gyab0_11@student.demo.erg.edu.vn	MALE	2009-12-12 00:00:00+00	\N	\N	Phụ huynh Bùi Hoàng Nam	0983771229	\N	\N	2025-11-13 09:24:27.733344+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783675467.733344300, "completedAssignments": 19}
stu_demo_17gyab0_12	2026-07-13 09:24:28.254818+00	2026-07-13 09:24:28.254818+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT012	Đỗ Ngọc Anh	stu_demo_17gyab0_12	\N	stu_demo_17gyab0_12@student.demo.erg.edu.vn	FEMALE	2009-01-13 00:00:00+00	\N	\N	Phụ huynh Đỗ Ngọc Anh	0983771230	\N	\N	2025-10-13 09:24:28.168578+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783589068.168577800, "completedAssignments": 20}
stu_demo_17gyab0_13	2026-07-13 09:24:28.69397+00	2026-07-13 09:24:28.69397+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT013	Nguyễn Bảo Ngọc	stu_demo_17gyab0_13	\N	stu_demo_17gyab0_13@student.demo.erg.edu.vn	MALE	2009-02-14 00:00:00+00	\N	\N	Phụ huynh Nguyễn Bảo Ngọc	0983771231	\N	\N	2025-09-13 09:24:28.605223+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783502668.605222700, "completedAssignments": 21}
stu_demo_17gyab0_14	2026-07-13 09:24:29.125764+00	2026-07-13 09:24:29.125764+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT014	Trần Tuấn Kiệt	stu_demo_17gyab0_14	\N	stu_demo_17gyab0_14@student.demo.erg.edu.vn	FEMALE	2009-03-15 00:00:00+00	\N	\N	Phụ huynh Trần Tuấn Kiệt	0983771232	\N	\N	2025-08-13 09:24:29.04+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783416269.040000400, "completedAssignments": 22}
stu_demo_17gyab0_15	2026-07-13 09:24:29.55381+00	2026-07-13 09:24:29.55381+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT015	Lê Quang Huy	stu_demo_17gyab0_15	\N	stu_demo_17gyab0_15@student.demo.erg.edu.vn	MALE	2009-04-16 00:00:00+00	\N	\N	Phụ huynh Lê Quang Huy	0983771233	\N	\N	2025-07-13 09:24:29.467545+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783329869.467544500, "completedAssignments": 23}
stu_demo_17gyab0_16	2026-07-13 09:24:29.985921+00	2026-07-13 09:24:29.985921+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT016	Phạm Minh Khang	stu_demo_17gyab0_16	\N	stu_demo_17gyab0_16@student.demo.erg.edu.vn	FEMALE	2009-05-17 00:00:00+00	\N	\N	Phụ huynh Phạm Minh Khang	0983771234	\N	\N	2025-06-13 09:24:29.899919+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783934669.899918700, "completedAssignments": 8}
stu_demo_17gyab0_17	2026-07-13 09:24:30.418071+00	2026-07-13 09:24:30.418071+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT017	Võ Khánh Linh	stu_demo_17gyab0_17	\N	stu_demo_17gyab0_17@student.demo.erg.edu.vn	MALE	2009-06-18 00:00:00+00	\N	\N	Phụ huynh Võ Khánh Linh	0983771235	\N	\N	2025-05-13 09:24:30.330301+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783848270.330300600, "completedAssignments": 9}
stu_demo_17gyab0_18	2026-07-13 09:24:30.848199+00	2026-07-13 09:24:30.848199+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT018	Đặng Thảo Vy	stu_demo_17gyab0_18	\N	stu_demo_17gyab0_18@student.demo.erg.edu.vn	FEMALE	2009-07-19 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0983771236	\N	\N	2026-01-13 09:24:30.762+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783761870.762000200, "completedAssignments": 10}
stu_demo_17gyab0_19	2026-07-13 09:24:31.277511+00	2026-07-13 09:24:31.277511+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT019	Bùi Gia Hân	stu_demo_17gyab0_19	\N	stu_demo_17gyab0_19@student.demo.erg.edu.vn	MALE	2009-08-20 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0983771237	\N	\N	2025-12-13 09:24:31.191832+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783675471.191832100, "completedAssignments": 11}
stu_demo_17gyab0_20	2026-07-13 09:24:31.712583+00	2026-07-13 09:24:31.712583+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT020	Đỗ Đức Anh	stu_demo_17gyab0_20	\N	stu_demo_17gyab0_20@student.demo.erg.edu.vn	FEMALE	2009-09-21 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0983771259	\N	\N	2025-11-13 09:24:31.626524+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783589071.626524000, "completedAssignments": 12}
stu_demo_17gyab0_21	2026-07-13 09:24:32.147551+00	2026-07-13 09:24:32.147551+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT021	Nguyễn Hoàng Nam	stu_demo_17gyab0_21	\N	stu_demo_17gyab0_21@student.demo.erg.edu.vn	MALE	2009-10-22 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0983771260	\N	\N	2025-10-13 09:24:32.061914+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783502672.061913600, "completedAssignments": 13}
stu_demo_17gyab0_22	2026-07-13 09:24:32.584022+00	2026-07-13 09:24:32.584022+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT022	Trần Ngọc Anh	stu_demo_17gyab0_22	\N	stu_demo_17gyab0_22@student.demo.erg.edu.vn	FEMALE	2009-11-23 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0983771261	\N	\N	2025-09-13 09:24:32.495482+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783416272.495482100, "completedAssignments": 14}
stu_demo_17gyab0_23	2026-07-13 09:24:33.020561+00	2026-07-13 09:24:33.020561+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT023	Lê Bảo Ngọc	stu_demo_17gyab0_23	\N	stu_demo_17gyab0_23@student.demo.erg.edu.vn	MALE	2009-12-24 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0983771262	\N	\N	2025-08-13 09:24:32.932919+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783329872.932919000, "completedAssignments": 15}
stu_demo_17gyab0_24	2026-07-13 10:48:37.039822+00	2026-07-13 10:48:37.039822+00	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	cls_demo_1aqllkt_b2	ERG1AQLLKT024	Phạm Tuấn Kiệt	stu_demo_17gyab0_24	\N	stu_demo_17gyab0_24@student.demo.erg.edu.vn	FEMALE	2009-01-25 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0983771263	\N	\N	2025-07-13 10:48:36.920007+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783939716.920006800, "completedAssignments": 16}
stu_demo_15qzdh7_1	2026-07-13 10:51:22.530476+00	2026-07-13 10:51:22.530476+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE001	Đặng Thảo Vy	stu_demo_15qzdh7_1	\N	stu_demo_15qzdh7_1@student.demo.erg.edu.vn	MALE	2008-02-02 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0986096165	\N	\N	2025-12-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783853482.409333800, "completedAssignments": 9}
stu_demo_15qzdh7_2	2026-07-13 10:51:22.65125+00	2026-07-13 10:51:22.65125+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE002	Bùi Gia Hân	stu_demo_15qzdh7_2	\N	stu_demo_15qzdh7_2@student.demo.erg.edu.vn	FEMALE	2008-03-03 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0986096164	\N	\N	2025-11-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783767082.409333800, "completedAssignments": 10}
stu_demo_15qzdh7_3	2026-07-13 10:51:22.767802+00	2026-07-13 10:51:22.767802+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE003	Đỗ Đức Anh	stu_demo_15qzdh7_3	\N	stu_demo_15qzdh7_3@student.demo.erg.edu.vn	MALE	2008-04-04 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0986096163	\N	\N	2025-10-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783680682.409333800, "completedAssignments": 11}
stu_demo_15qzdh7_4	2026-07-13 10:51:22.885925+00	2026-07-13 10:51:22.885925+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE004	Nguyễn Hoàng Nam	stu_demo_15qzdh7_4	\N	stu_demo_15qzdh7_4@student.demo.erg.edu.vn	FEMALE	2008-05-05 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0986096162	\N	\N	2025-09-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783594282.409333800, "completedAssignments": 12}
stu_demo_15qzdh7_5	2026-07-13 10:51:23.004288+00	2026-07-13 10:51:23.004288+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE005	Trần Ngọc Anh	stu_demo_15qzdh7_5	\N	stu_demo_15qzdh7_5@student.demo.erg.edu.vn	MALE	2008-06-06 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0986096161	\N	\N	2025-08-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783507882.409333800, "completedAssignments": 13}
stu_demo_15qzdh7_6	2026-07-13 10:51:23.121863+00	2026-07-13 10:51:23.121863+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE006	Lê Bảo Ngọc	stu_demo_15qzdh7_6	\N	stu_demo_15qzdh7_6@student.demo.erg.edu.vn	FEMALE	2008-07-07 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0986096160	\N	\N	2025-07-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783421482.409333800, "completedAssignments": 14}
stu_demo_15qzdh7_7	2026-07-13 10:51:23.239296+00	2026-07-13 10:51:23.239296+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE007	Phạm Tuấn Kiệt	stu_demo_15qzdh7_7	\N	stu_demo_15qzdh7_7@student.demo.erg.edu.vn	MALE	2008-08-08 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0986096159	\N	\N	2025-06-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783335082.409333800, "completedAssignments": 15}
stu_demo_15qzdh7_8	2026-07-13 10:51:23.356839+00	2026-07-13 10:51:23.356839+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE008	Võ Quang Huy	stu_demo_15qzdh7_8	\N	stu_demo_15qzdh7_8@student.demo.erg.edu.vn	FEMALE	2008-09-09 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0986096158	\N	\N	2025-05-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783939882.409333800, "completedAssignments": 16}
stu_demo_15qzdh7_9	2026-07-13 10:51:23.473369+00	2026-07-13 10:51:23.473369+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE009	Đặng Minh Khang	stu_demo_15qzdh7_9	\N	stu_demo_15qzdh7_9@student.demo.erg.edu.vn	MALE	2008-10-10 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0986096157	\N	\N	2026-01-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783853482.409333800, "completedAssignments": 17}
stu_demo_15qzdh7_10	2026-07-13 10:51:23.591015+00	2026-07-13 10:51:23.591015+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE010	Bùi Khánh Linh	stu_demo_15qzdh7_10	\N	stu_demo_15qzdh7_10@student.demo.erg.edu.vn	FEMALE	2008-11-11 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0979046475	\N	\N	2025-12-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783767082.409333800, "completedAssignments": 18}
stu_demo_15qzdh7_11	2026-07-13 10:51:23.707701+00	2026-07-13 10:51:23.707701+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE011	Đỗ Thảo Vy	stu_demo_15qzdh7_11	\N	stu_demo_15qzdh7_11@student.demo.erg.edu.vn	MALE	2008-12-12 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0979046474	\N	\N	2025-11-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783680682.409333800, "completedAssignments": 19}
stu_demo_15qzdh7_12	2026-07-13 10:51:23.826077+00	2026-07-13 10:51:23.826077+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE012	Nguyễn Gia Hân	stu_demo_15qzdh7_12	\N	stu_demo_15qzdh7_12@student.demo.erg.edu.vn	FEMALE	2008-01-13 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0979046473	\N	\N	2025-10-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783594282.409333800, "completedAssignments": 20}
stu_demo_15qzdh7_13	2026-07-13 10:51:23.943578+00	2026-07-13 10:51:23.943578+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE013	Trần Đức Anh	stu_demo_15qzdh7_13	\N	stu_demo_15qzdh7_13@student.demo.erg.edu.vn	MALE	2008-02-14 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0979046472	\N	\N	2025-09-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783507882.409333800, "completedAssignments": 21}
stu_demo_15qzdh7_14	2026-07-13 10:51:24.068443+00	2026-07-13 10:51:24.068443+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE014	Lê Hoàng Nam	stu_demo_15qzdh7_14	\N	stu_demo_15qzdh7_14@student.demo.erg.edu.vn	FEMALE	2008-03-15 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0979046471	\N	\N	2025-08-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783421482.409333800, "completedAssignments": 22}
stu_demo_15qzdh7_15	2026-07-13 10:51:24.187278+00	2026-07-13 10:51:24.187278+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE015	Phạm Ngọc Anh	stu_demo_15qzdh7_15	\N	stu_demo_15qzdh7_15@student.demo.erg.edu.vn	MALE	2008-04-16 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0979046470	\N	\N	2025-07-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783335082.409333800, "completedAssignments": 23}
stu_demo_15qzdh7_16	2026-07-13 10:51:24.304607+00	2026-07-13 10:51:24.304607+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE016	Võ Bảo Ngọc	stu_demo_15qzdh7_16	\N	stu_demo_15qzdh7_16@student.demo.erg.edu.vn	FEMALE	2008-05-17 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0979046469	\N	\N	2025-06-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783939882.409333800, "completedAssignments": 8}
stu_demo_15qzdh7_17	2026-07-13 10:51:24.421685+00	2026-07-13 10:51:24.421685+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE017	Đặng Tuấn Kiệt	stu_demo_15qzdh7_17	\N	stu_demo_15qzdh7_17@student.demo.erg.edu.vn	MALE	2008-06-18 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0979046468	\N	\N	2025-05-13 10:51:22.409334+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783853482.409333800, "completedAssignments": 9}
stu_demo_15qzdh7_18	2026-07-13 10:51:24.539247+00	2026-07-13 10:51:24.539247+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE018	Bùi Quang Huy	stu_demo_15qzdh7_18	\N	stu_demo_15qzdh7_18@student.demo.erg.edu.vn	FEMALE	2008-07-19 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0979046467	\N	\N	2026-01-13 10:51:22.409334+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783767082.409333800, "completedAssignments": 10}
stu_demo_15qzdh7_19	2026-07-13 10:51:24.65599+00	2026-07-13 10:51:24.65599+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE019	Đỗ Minh Khang	stu_demo_15qzdh7_19	\N	stu_demo_15qzdh7_19@student.demo.erg.edu.vn	MALE	2008-08-20 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0979046466	\N	\N	2025-12-13 10:51:22.410331+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783680682.410330500, "completedAssignments": 11}
stu_demo_15qzdh7_20	2026-07-13 10:51:24.77266+00	2026-07-13 10:51:24.77266+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE020	Nguyễn Khánh Linh	stu_demo_15qzdh7_20	\N	stu_demo_15qzdh7_20@student.demo.erg.edu.vn	FEMALE	2008-09-21 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0979046444	\N	\N	2025-11-13 10:51:22.410331+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783594282.410330500, "completedAssignments": 12}
stu_demo_15qzdh7_21	2026-07-13 10:51:24.890281+00	2026-07-13 10:51:24.890281+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE021	Trần Thảo Vy	stu_demo_15qzdh7_21	\N	stu_demo_15qzdh7_21@student.demo.erg.edu.vn	MALE	2008-10-22 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0979046443	\N	\N	2025-10-13 10:51:22.410331+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783507882.410330500, "completedAssignments": 13}
stu_demo_15qzdh7_22	2026-07-13 10:51:25.006747+00	2026-07-13 10:51:25.006747+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE022	Lê Gia Hân	stu_demo_15qzdh7_22	\N	stu_demo_15qzdh7_22@student.demo.erg.edu.vn	FEMALE	2008-11-23 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0979046442	\N	\N	2025-09-13 10:51:22.410331+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783421482.410330500, "completedAssignments": 14}
stu_demo_15qzdh7_23	2026-07-13 10:51:25.124937+00	2026-07-13 10:51:25.124937+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE023	Phạm Đức Anh	stu_demo_15qzdh7_23	\N	stu_demo_15qzdh7_23@student.demo.erg.edu.vn	MALE	2008-12-24 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0979046441	\N	\N	2025-08-13 10:51:22.410331+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783335082.410330500, "completedAssignments": 15}
stu_demo_15qzdh7_24	2026-07-13 10:51:25.242619+00	2026-07-13 10:51:25.242619+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a1	ERG24ZQQE024	Võ Hoàng Nam	stu_demo_15qzdh7_24	\N	stu_demo_15qzdh7_24@student.demo.erg.edu.vn	FEMALE	2008-01-25 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0979046440	\N	\N	2025-07-13 10:51:22.410331+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783939882.410330500, "completedAssignments": 16}
stu_demo_15qzdh8_1	2026-07-13 10:51:29.134884+00	2026-07-13 10:51:29.134884+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE001	Đặng Thảo Vy	stu_demo_15qzdh8_1	\N	stu_demo_15qzdh8_1@student.demo.erg.edu.vn	MALE	2008-02-02 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0986096165	\N	\N	2025-12-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783853489.016241400, "completedAssignments": 9}
stu_demo_15qzdh8_2	2026-07-13 10:51:29.253289+00	2026-07-13 10:51:29.253289+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE002	Bùi Gia Hân	stu_demo_15qzdh8_2	\N	stu_demo_15qzdh8_2@student.demo.erg.edu.vn	FEMALE	2008-03-03 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0986096164	\N	\N	2025-11-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783767089.016241400, "completedAssignments": 10}
stu_demo_15qzdh8_3	2026-07-13 10:51:29.370578+00	2026-07-13 10:51:29.370578+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE003	Đỗ Đức Anh	stu_demo_15qzdh8_3	\N	stu_demo_15qzdh8_3@student.demo.erg.edu.vn	MALE	2008-04-04 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0986096163	\N	\N	2025-10-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783680689.016241400, "completedAssignments": 11}
stu_demo_15qzdh8_4	2026-07-13 10:51:29.488872+00	2026-07-13 10:51:29.488872+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE004	Nguyễn Hoàng Nam	stu_demo_15qzdh8_4	\N	stu_demo_15qzdh8_4@student.demo.erg.edu.vn	FEMALE	2008-05-05 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0986096162	\N	\N	2025-09-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783594289.016241400, "completedAssignments": 12}
stu_demo_15qzdh8_5	2026-07-13 10:51:29.606074+00	2026-07-13 10:51:29.606074+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE005	Trần Ngọc Anh	stu_demo_15qzdh8_5	\N	stu_demo_15qzdh8_5@student.demo.erg.edu.vn	MALE	2008-06-06 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0986096161	\N	\N	2025-08-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783507889.016241400, "completedAssignments": 13}
stu_demo_15qzdh8_6	2026-07-13 10:51:29.723133+00	2026-07-13 10:51:29.723133+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE006	Lê Bảo Ngọc	stu_demo_15qzdh8_6	\N	stu_demo_15qzdh8_6@student.demo.erg.edu.vn	FEMALE	2008-07-07 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0986096160	\N	\N	2025-07-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783421489.016241400, "completedAssignments": 14}
stu_demo_15qzdh8_7	2026-07-13 10:51:29.84153+00	2026-07-13 10:51:29.84153+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE007	Phạm Tuấn Kiệt	stu_demo_15qzdh8_7	\N	stu_demo_15qzdh8_7@student.demo.erg.edu.vn	MALE	2008-08-08 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0986096159	\N	\N	2025-06-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783335089.016241400, "completedAssignments": 15}
stu_demo_15qzdh8_8	2026-07-13 10:51:29.960246+00	2026-07-13 10:51:29.960246+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE008	Võ Quang Huy	stu_demo_15qzdh8_8	\N	stu_demo_15qzdh8_8@student.demo.erg.edu.vn	FEMALE	2008-09-09 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0986096158	\N	\N	2025-05-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783939889.016241400, "completedAssignments": 16}
stu_demo_15qzdh8_9	2026-07-13 10:51:30.077384+00	2026-07-13 10:51:30.077384+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE009	Đặng Minh Khang	stu_demo_15qzdh8_9	\N	stu_demo_15qzdh8_9@student.demo.erg.edu.vn	MALE	2008-10-10 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0986096157	\N	\N	2026-01-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783853489.016241400, "completedAssignments": 17}
stu_demo_15qzdh8_10	2026-07-13 10:51:30.194807+00	2026-07-13 10:51:30.194807+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE010	Bùi Khánh Linh	stu_demo_15qzdh8_10	\N	stu_demo_15qzdh8_10@student.demo.erg.edu.vn	FEMALE	2008-11-11 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0979046475	\N	\N	2025-12-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783767089.016241400, "completedAssignments": 18}
stu_demo_15qzdh8_11	2026-07-13 10:51:30.31358+00	2026-07-13 10:51:30.31358+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE011	Đỗ Thảo Vy	stu_demo_15qzdh8_11	\N	stu_demo_15qzdh8_11@student.demo.erg.edu.vn	MALE	2008-12-12 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0979046474	\N	\N	2025-11-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783680689.016241400, "completedAssignments": 19}
stu_demo_15qzdh8_12	2026-07-13 10:51:30.431426+00	2026-07-13 10:51:30.431426+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE012	Nguyễn Gia Hân	stu_demo_15qzdh8_12	\N	stu_demo_15qzdh8_12@student.demo.erg.edu.vn	FEMALE	2008-01-13 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0979046473	\N	\N	2025-10-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783594289.016241400, "completedAssignments": 20}
stu_demo_15qzdh8_13	2026-07-13 10:51:30.548676+00	2026-07-13 10:51:30.548676+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE013	Trần Đức Anh	stu_demo_15qzdh8_13	\N	stu_demo_15qzdh8_13@student.demo.erg.edu.vn	MALE	2008-02-14 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0979046472	\N	\N	2025-09-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783507889.016241400, "completedAssignments": 21}
stu_demo_15qzdh8_14	2026-07-13 10:51:30.66692+00	2026-07-13 10:51:30.66692+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE014	Lê Hoàng Nam	stu_demo_15qzdh8_14	\N	stu_demo_15qzdh8_14@student.demo.erg.edu.vn	FEMALE	2008-03-15 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0979046471	\N	\N	2025-08-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783421489.016241400, "completedAssignments": 22}
stu_demo_15qzdh8_15	2026-07-13 10:51:30.784713+00	2026-07-13 10:51:30.784713+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE015	Phạm Ngọc Anh	stu_demo_15qzdh8_15	\N	stu_demo_15qzdh8_15@student.demo.erg.edu.vn	MALE	2008-04-16 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0979046470	\N	\N	2025-07-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783335089.016241400, "completedAssignments": 23}
stu_demo_15qzdh8_16	2026-07-13 10:51:30.902618+00	2026-07-13 10:51:30.902618+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE016	Võ Bảo Ngọc	stu_demo_15qzdh8_16	\N	stu_demo_15qzdh8_16@student.demo.erg.edu.vn	FEMALE	2008-05-17 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0979046469	\N	\N	2025-06-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783939889.016241400, "completedAssignments": 8}
stu_demo_15qzdh8_17	2026-07-13 10:51:31.01984+00	2026-07-13 10:51:31.01984+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE017	Đặng Tuấn Kiệt	stu_demo_15qzdh8_17	\N	stu_demo_15qzdh8_17@student.demo.erg.edu.vn	MALE	2008-06-18 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0979046468	\N	\N	2025-05-13 10:51:29.016241+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783853489.016241400, "completedAssignments": 9}
stu_demo_15qzdh8_18	2026-07-13 10:51:31.137546+00	2026-07-13 10:51:31.137546+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE018	Bùi Quang Huy	stu_demo_15qzdh8_18	\N	stu_demo_15qzdh8_18@student.demo.erg.edu.vn	FEMALE	2008-07-19 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0979046467	\N	\N	2026-01-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783767089.016241400, "completedAssignments": 10}
stu_demo_15qzdh8_19	2026-07-13 10:51:31.256133+00	2026-07-13 10:51:31.256133+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE019	Đỗ Minh Khang	stu_demo_15qzdh8_19	\N	stu_demo_15qzdh8_19@student.demo.erg.edu.vn	MALE	2008-08-20 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0979046466	\N	\N	2025-12-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783680689.016241400, "completedAssignments": 11}
stu_demo_15qzdh8_20	2026-07-13 10:51:31.37328+00	2026-07-13 10:51:31.37328+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE020	Nguyễn Khánh Linh	stu_demo_15qzdh8_20	\N	stu_demo_15qzdh8_20@student.demo.erg.edu.vn	FEMALE	2008-09-21 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0979046444	\N	\N	2025-11-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783594289.016241400, "completedAssignments": 12}
stu_demo_15qzdh8_21	2026-07-13 10:51:31.49007+00	2026-07-13 10:51:31.49007+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE021	Trần Thảo Vy	stu_demo_15qzdh8_21	\N	stu_demo_15qzdh8_21@student.demo.erg.edu.vn	MALE	2008-10-22 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0979046443	\N	\N	2025-10-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783507889.016241400, "completedAssignments": 13}
stu_demo_15qzdh8_22	2026-07-13 10:51:31.607336+00	2026-07-13 10:51:31.607336+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE022	Lê Gia Hân	stu_demo_15qzdh8_22	\N	stu_demo_15qzdh8_22@student.demo.erg.edu.vn	FEMALE	2008-11-23 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0979046442	\N	\N	2025-09-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783421489.016241400, "completedAssignments": 14}
stu_demo_15qzdh8_23	2026-07-13 10:51:31.725552+00	2026-07-13 10:51:31.725552+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE023	Phạm Đức Anh	stu_demo_15qzdh8_23	\N	stu_demo_15qzdh8_23@student.demo.erg.edu.vn	MALE	2008-12-24 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0979046441	\N	\N	2025-08-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783335089.016241400, "completedAssignments": 15}
stu_demo_15qzdh8_24	2026-07-13 10:51:31.842219+00	2026-07-13 10:51:31.842219+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_a2	ERG24ZQQE024	Võ Hoàng Nam	stu_demo_15qzdh8_24	\N	stu_demo_15qzdh8_24@student.demo.erg.edu.vn	FEMALE	2008-01-25 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0979046440	\N	\N	2025-07-13 10:51:29.016241+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783939889.016241400, "completedAssignments": 16}
stu_demo_15qzdi2_1	2026-07-13 10:51:35.712063+00	2026-07-13 10:51:35.712063+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE001	Đặng Thảo Vy	stu_demo_15qzdi2_1	\N	stu_demo_15qzdi2_1@student.demo.erg.edu.vn	MALE	2008-02-02 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0986096165	\N	\N	2025-12-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783853495.593636400, "completedAssignments": 9}
stu_demo_15qzdi2_2	2026-07-13 10:51:35.828694+00	2026-07-13 10:51:35.828694+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE002	Bùi Gia Hân	stu_demo_15qzdi2_2	\N	stu_demo_15qzdi2_2@student.demo.erg.edu.vn	FEMALE	2008-03-03 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0986096164	\N	\N	2025-11-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783767095.593636400, "completedAssignments": 10}
stu_demo_15qzdi2_3	2026-07-13 10:51:35.945508+00	2026-07-13 10:51:35.945508+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE003	Đỗ Đức Anh	stu_demo_15qzdi2_3	\N	stu_demo_15qzdi2_3@student.demo.erg.edu.vn	MALE	2008-04-04 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0986096163	\N	\N	2025-10-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783680695.593636400, "completedAssignments": 11}
stu_demo_15qzdi2_4	2026-07-13 10:51:36.063163+00	2026-07-13 10:51:36.063163+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE004	Nguyễn Hoàng Nam	stu_demo_15qzdi2_4	\N	stu_demo_15qzdi2_4@student.demo.erg.edu.vn	FEMALE	2008-05-05 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0986096162	\N	\N	2025-09-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783594295.593636400, "completedAssignments": 12}
stu_demo_15qzdi2_5	2026-07-13 10:51:36.180824+00	2026-07-13 10:51:36.180824+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE005	Trần Ngọc Anh	stu_demo_15qzdi2_5	\N	stu_demo_15qzdi2_5@student.demo.erg.edu.vn	MALE	2008-06-06 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0986096161	\N	\N	2025-08-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783507895.593636400, "completedAssignments": 13}
stu_demo_15qzdi2_6	2026-07-13 10:51:36.298175+00	2026-07-13 10:51:36.298175+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE006	Lê Bảo Ngọc	stu_demo_15qzdi2_6	\N	stu_demo_15qzdi2_6@student.demo.erg.edu.vn	FEMALE	2008-07-07 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0986096160	\N	\N	2025-07-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783421495.593636400, "completedAssignments": 14}
stu_demo_15qzdi2_7	2026-07-13 10:51:36.415548+00	2026-07-13 10:51:36.415548+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE007	Phạm Tuấn Kiệt	stu_demo_15qzdi2_7	\N	stu_demo_15qzdi2_7@student.demo.erg.edu.vn	MALE	2008-08-08 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0986096159	\N	\N	2025-06-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783335095.593636400, "completedAssignments": 15}
stu_demo_15qzdi2_8	2026-07-13 10:51:36.532448+00	2026-07-13 10:51:36.532448+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE008	Võ Quang Huy	stu_demo_15qzdi2_8	\N	stu_demo_15qzdi2_8@student.demo.erg.edu.vn	FEMALE	2008-09-09 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0986096158	\N	\N	2025-05-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783939895.593636400, "completedAssignments": 16}
stu_demo_15qzdi2_9	2026-07-13 10:51:36.649725+00	2026-07-13 10:51:36.649725+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE009	Đặng Minh Khang	stu_demo_15qzdi2_9	\N	stu_demo_15qzdi2_9@student.demo.erg.edu.vn	MALE	2008-10-10 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0986096157	\N	\N	2026-01-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783853495.593636400, "completedAssignments": 17}
stu_demo_15qzdi2_10	2026-07-13 10:51:36.766132+00	2026-07-13 10:51:36.766132+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE010	Bùi Khánh Linh	stu_demo_15qzdi2_10	\N	stu_demo_15qzdi2_10@student.demo.erg.edu.vn	FEMALE	2008-11-11 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0979046475	\N	\N	2025-12-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783767095.593636400, "completedAssignments": 18}
stu_demo_15qzdi2_11	2026-07-13 10:51:36.883362+00	2026-07-13 10:51:36.883362+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE011	Đỗ Thảo Vy	stu_demo_15qzdi2_11	\N	stu_demo_15qzdi2_11@student.demo.erg.edu.vn	MALE	2008-12-12 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0979046474	\N	\N	2025-11-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783680695.593636400, "completedAssignments": 19}
stu_demo_15qzdi2_12	2026-07-13 10:51:37.001021+00	2026-07-13 10:51:37.001021+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE012	Nguyễn Gia Hân	stu_demo_15qzdi2_12	\N	stu_demo_15qzdi2_12@student.demo.erg.edu.vn	FEMALE	2008-01-13 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0979046473	\N	\N	2025-10-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783594295.593636400, "completedAssignments": 20}
stu_demo_15qzdi2_13	2026-07-13 10:51:37.118892+00	2026-07-13 10:51:37.118892+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE013	Trần Đức Anh	stu_demo_15qzdi2_13	\N	stu_demo_15qzdi2_13@student.demo.erg.edu.vn	MALE	2008-02-14 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0979046472	\N	\N	2025-09-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783507895.593636400, "completedAssignments": 21}
stu_demo_15qzdi2_14	2026-07-13 10:51:37.236176+00	2026-07-13 10:51:37.236176+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE014	Lê Hoàng Nam	stu_demo_15qzdi2_14	\N	stu_demo_15qzdi2_14@student.demo.erg.edu.vn	FEMALE	2008-03-15 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0979046471	\N	\N	2025-08-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783421495.593636400, "completedAssignments": 22}
stu_demo_15qzdi2_15	2026-07-13 10:51:37.353743+00	2026-07-13 10:51:37.353743+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE015	Phạm Ngọc Anh	stu_demo_15qzdi2_15	\N	stu_demo_15qzdi2_15@student.demo.erg.edu.vn	MALE	2008-04-16 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0979046470	\N	\N	2025-07-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783335095.593636400, "completedAssignments": 23}
stu_demo_15qzdi2_16	2026-07-13 10:51:37.471876+00	2026-07-13 10:51:37.471876+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE016	Võ Bảo Ngọc	stu_demo_15qzdi2_16	\N	stu_demo_15qzdi2_16@student.demo.erg.edu.vn	FEMALE	2008-05-17 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0979046469	\N	\N	2025-06-13 10:51:35.593636+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783939895.593636400, "completedAssignments": 8}
stu_demo_15qzdi2_17	2026-07-13 10:51:37.589019+00	2026-07-13 10:51:37.589019+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE017	Đặng Tuấn Kiệt	stu_demo_15qzdi2_17	\N	stu_demo_15qzdi2_17@student.demo.erg.edu.vn	MALE	2008-06-18 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0979046468	\N	\N	2025-05-13 10:51:35.593636+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783853495.593636400, "completedAssignments": 9}
stu_demo_15qzdi2_18	2026-07-13 10:51:37.705404+00	2026-07-13 10:51:37.705404+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE018	Bùi Quang Huy	stu_demo_15qzdi2_18	\N	stu_demo_15qzdi2_18@student.demo.erg.edu.vn	FEMALE	2008-07-19 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0979046467	\N	\N	2026-01-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783767095.594637000, "completedAssignments": 10}
stu_demo_15qzdi2_19	2026-07-13 10:51:37.822477+00	2026-07-13 10:51:37.822477+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE019	Đỗ Minh Khang	stu_demo_15qzdi2_19	\N	stu_demo_15qzdi2_19@student.demo.erg.edu.vn	MALE	2008-08-20 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0979046466	\N	\N	2025-12-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783680695.594637000, "completedAssignments": 11}
stu_demo_15qzdi2_20	2026-07-13 10:51:37.939421+00	2026-07-13 10:51:37.939421+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE020	Nguyễn Khánh Linh	stu_demo_15qzdi2_20	\N	stu_demo_15qzdi2_20@student.demo.erg.edu.vn	FEMALE	2008-09-21 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0979046444	\N	\N	2025-11-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783594295.594637000, "completedAssignments": 12}
stu_demo_15qzdi2_21	2026-07-13 10:51:38.058902+00	2026-07-13 10:51:38.058902+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE021	Trần Thảo Vy	stu_demo_15qzdi2_21	\N	stu_demo_15qzdi2_21@student.demo.erg.edu.vn	MALE	2008-10-22 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0979046443	\N	\N	2025-10-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783507895.594637000, "completedAssignments": 13}
stu_demo_15qzdi2_22	2026-07-13 10:51:38.178146+00	2026-07-13 10:51:38.178146+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE022	Lê Gia Hân	stu_demo_15qzdi2_22	\N	stu_demo_15qzdi2_22@student.demo.erg.edu.vn	FEMALE	2008-11-23 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0979046442	\N	\N	2025-09-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783421495.594637000, "completedAssignments": 14}
stu_demo_15qzdi2_23	2026-07-13 10:51:38.296143+00	2026-07-13 10:51:38.296143+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE023	Phạm Đức Anh	stu_demo_15qzdi2_23	\N	stu_demo_15qzdi2_23@student.demo.erg.edu.vn	MALE	2008-12-24 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0979046441	\N	\N	2025-08-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783335095.594637000, "completedAssignments": 15}
stu_demo_15qzdi2_24	2026-07-13 10:51:38.412551+00	2026-07-13 10:51:38.412551+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b1	ERG24ZQQE024	Võ Hoàng Nam	stu_demo_15qzdi2_24	\N	stu_demo_15qzdi2_24@student.demo.erg.edu.vn	FEMALE	2008-01-25 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0979046440	\N	\N	2025-07-13 10:51:35.594637+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783939895.594637000, "completedAssignments": 16}
stu_demo_15qzdi3_1	2026-07-13 10:51:42.284288+00	2026-07-13 10:51:42.284288+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE001	Đặng Thảo Vy	stu_demo_15qzdi3_1	\N	stu_demo_15qzdi3_1@student.demo.erg.edu.vn	MALE	2008-02-02 00:00:00+00	\N	\N	Phụ huynh Đặng Thảo Vy	0986096165	\N	\N	2025-12-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.3, "lastActivityAt": 1783853502.164798200, "completedAssignments": 9}
stu_demo_15qzdi3_2	2026-07-13 10:51:42.402141+00	2026-07-13 10:51:42.402141+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE002	Bùi Gia Hân	stu_demo_15qzdi3_2	\N	stu_demo_15qzdi3_2@student.demo.erg.edu.vn	FEMALE	2008-03-03 00:00:00+00	\N	\N	Phụ huynh Bùi Gia Hân	0986096164	\N	\N	2025-11-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.4, "lastActivityAt": 1783767102.164798200, "completedAssignments": 10}
stu_demo_15qzdi3_3	2026-07-13 10:51:42.520121+00	2026-07-13 10:51:42.520121+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE003	Đỗ Đức Anh	stu_demo_15qzdi3_3	\N	stu_demo_15qzdi3_3@student.demo.erg.edu.vn	MALE	2008-04-04 00:00:00+00	\N	\N	Phụ huynh Đỗ Đức Anh	0986096163	\N	\N	2025-10-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.5, "lastActivityAt": 1783680702.164798200, "completedAssignments": 11}
stu_demo_15qzdi3_4	2026-07-13 10:51:42.637826+00	2026-07-13 10:51:42.637826+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE004	Nguyễn Hoàng Nam	stu_demo_15qzdi3_4	\N	stu_demo_15qzdi3_4@student.demo.erg.edu.vn	FEMALE	2008-05-05 00:00:00+00	\N	\N	Phụ huynh Nguyễn Hoàng Nam	0986096162	\N	\N	2025-09-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.6000000000000005, "lastActivityAt": 1783594302.164798200, "completedAssignments": 12}
stu_demo_15qzdi3_5	2026-07-13 10:51:42.754621+00	2026-07-13 10:51:42.754621+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE005	Trần Ngọc Anh	stu_demo_15qzdi3_5	\N	stu_demo_15qzdi3_5@student.demo.erg.edu.vn	MALE	2008-06-06 00:00:00+00	\N	\N	Phụ huynh Trần Ngọc Anh	0986096161	\N	\N	2025-08-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.7, "lastActivityAt": 1783507902.164798200, "completedAssignments": 13}
stu_demo_15qzdi3_6	2026-07-13 10:51:42.872781+00	2026-07-13 10:51:42.872781+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE006	Lê Bảo Ngọc	stu_demo_15qzdi3_6	\N	stu_demo_15qzdi3_6@student.demo.erg.edu.vn	FEMALE	2008-07-07 00:00:00+00	\N	\N	Phụ huynh Lê Bảo Ngọc	0986096160	\N	\N	2025-07-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.8, "lastActivityAt": 1783421502.164798200, "completedAssignments": 14}
stu_demo_15qzdi3_7	2026-07-13 10:51:42.990487+00	2026-07-13 10:51:42.990487+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE007	Phạm Tuấn Kiệt	stu_demo_15qzdi3_7	\N	stu_demo_15qzdi3_7@student.demo.erg.edu.vn	MALE	2008-08-08 00:00:00+00	\N	\N	Phụ huynh Phạm Tuấn Kiệt	0986096159	\N	\N	2025-06-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 6.9, "lastActivityAt": 1783335102.164798200, "completedAssignments": 15}
stu_demo_15qzdi3_8	2026-07-13 10:51:43.10811+00	2026-07-13 10:51:43.10811+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE008	Võ Quang Huy	stu_demo_15qzdi3_8	\N	stu_demo_15qzdi3_8@student.demo.erg.edu.vn	FEMALE	2008-09-09 00:00:00+00	\N	\N	Phụ huynh Võ Quang Huy	0986096158	\N	\N	2025-05-13 10:51:42.164798+00	\N	ACTIVE	{"averageScore": 7.0, "lastActivityAt": 1783939902.164798200, "completedAssignments": 16}
stu_demo_15qzdi3_9	2026-07-13 10:51:43.226074+00	2026-07-13 10:51:43.226074+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE009	Đặng Minh Khang	stu_demo_15qzdi3_9	\N	stu_demo_15qzdi3_9@student.demo.erg.edu.vn	MALE	2008-10-10 00:00:00+00	\N	\N	Phụ huynh Đặng Minh Khang	0986096157	\N	\N	2026-01-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.1000000000000005, "lastActivityAt": 1783853502.165817400, "completedAssignments": 17}
stu_demo_15qzdi3_10	2026-07-13 10:51:43.343591+00	2026-07-13 10:51:43.343591+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE010	Bùi Khánh Linh	stu_demo_15qzdi3_10	\N	stu_demo_15qzdi3_10@student.demo.erg.edu.vn	FEMALE	2008-11-11 00:00:00+00	\N	\N	Phụ huynh Bùi Khánh Linh	0979046475	\N	\N	2025-12-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.2, "lastActivityAt": 1783767102.165817400, "completedAssignments": 18}
stu_demo_15qzdi3_11	2026-07-13 10:51:43.461554+00	2026-07-13 10:51:43.461554+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE011	Đỗ Thảo Vy	stu_demo_15qzdi3_11	\N	stu_demo_15qzdi3_11@student.demo.erg.edu.vn	MALE	2008-12-12 00:00:00+00	\N	\N	Phụ huynh Đỗ Thảo Vy	0979046474	\N	\N	2025-11-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.300000000000001, "lastActivityAt": 1783680702.165817400, "completedAssignments": 19}
stu_demo_15qzdi3_12	2026-07-13 10:51:43.579378+00	2026-07-13 10:51:43.579378+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE012	Nguyễn Gia Hân	stu_demo_15qzdi3_12	\N	stu_demo_15qzdi3_12@student.demo.erg.edu.vn	FEMALE	2008-01-13 00:00:00+00	\N	\N	Phụ huynh Nguyễn Gia Hân	0979046473	\N	\N	2025-10-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.4, "lastActivityAt": 1783594302.165817400, "completedAssignments": 20}
stu_demo_15qzdi3_13	2026-07-13 10:51:43.697694+00	2026-07-13 10:51:43.697694+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE013	Trần Đức Anh	stu_demo_15qzdi3_13	\N	stu_demo_15qzdi3_13@student.demo.erg.edu.vn	MALE	2008-02-14 00:00:00+00	\N	\N	Phụ huynh Trần Đức Anh	0979046472	\N	\N	2025-09-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.5, "lastActivityAt": 1783507902.165817400, "completedAssignments": 21}
stu_demo_15qzdi3_14	2026-07-13 10:51:43.814815+00	2026-07-13 10:51:43.814815+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE014	Lê Hoàng Nam	stu_demo_15qzdi3_14	\N	stu_demo_15qzdi3_14@student.demo.erg.edu.vn	FEMALE	2008-03-15 00:00:00+00	\N	\N	Phụ huynh Lê Hoàng Nam	0979046471	\N	\N	2025-08-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.6, "lastActivityAt": 1783421502.165817400, "completedAssignments": 22}
stu_demo_15qzdi3_15	2026-07-13 10:51:43.937029+00	2026-07-13 10:51:43.937029+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE015	Phạm Ngọc Anh	stu_demo_15qzdi3_15	\N	stu_demo_15qzdi3_15@student.demo.erg.edu.vn	MALE	2008-04-16 00:00:00+00	\N	\N	Phụ huynh Phạm Ngọc Anh	0979046470	\N	\N	2025-07-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.7, "lastActivityAt": 1783335102.165817400, "completedAssignments": 23}
stu_demo_15qzdi3_16	2026-07-13 10:51:44.055159+00	2026-07-13 10:51:44.055159+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE016	Võ Bảo Ngọc	stu_demo_15qzdi3_16	\N	stu_demo_15qzdi3_16@student.demo.erg.edu.vn	FEMALE	2008-05-17 00:00:00+00	\N	\N	Phụ huynh Võ Bảo Ngọc	0979046469	\N	\N	2025-06-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 7.800000000000001, "lastActivityAt": 1783939902.165817400, "completedAssignments": 8}
stu_demo_15qzdi3_17	2026-07-13 10:51:44.171957+00	2026-07-13 10:51:44.171957+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE017	Đặng Tuấn Kiệt	stu_demo_15qzdi3_17	\N	stu_demo_15qzdi3_17@student.demo.erg.edu.vn	MALE	2008-06-18 00:00:00+00	\N	\N	Phụ huynh Đặng Tuấn Kiệt	0979046468	\N	\N	2025-05-13 10:51:42.165817+00	\N	AT_RISK	{"averageScore": 7.9, "lastActivityAt": 1783853502.165817400, "completedAssignments": 9}
stu_demo_15qzdi3_18	2026-07-13 10:51:44.29002+00	2026-07-13 10:51:44.29002+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE018	Bùi Quang Huy	stu_demo_15qzdi3_18	\N	stu_demo_15qzdi3_18@student.demo.erg.edu.vn	FEMALE	2008-07-19 00:00:00+00	\N	\N	Phụ huynh Bùi Quang Huy	0979046467	\N	\N	2026-01-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.0, "lastActivityAt": 1783767102.165817400, "completedAssignments": 10}
stu_demo_15qzdi3_19	2026-07-13 10:51:44.407413+00	2026-07-13 10:51:44.407413+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE019	Đỗ Minh Khang	stu_demo_15qzdi3_19	\N	stu_demo_15qzdi3_19@student.demo.erg.edu.vn	MALE	2008-08-20 00:00:00+00	\N	\N	Phụ huynh Đỗ Minh Khang	0979046466	\N	\N	2025-12-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.1, "lastActivityAt": 1783680702.165817400, "completedAssignments": 11}
stu_demo_15qzdi3_20	2026-07-13 10:51:44.525386+00	2026-07-13 10:51:44.525386+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE020	Nguyễn Khánh Linh	stu_demo_15qzdi3_20	\N	stu_demo_15qzdi3_20@student.demo.erg.edu.vn	FEMALE	2008-09-21 00:00:00+00	\N	\N	Phụ huynh Nguyễn Khánh Linh	0979046444	\N	\N	2025-11-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.2, "lastActivityAt": 1783594302.165817400, "completedAssignments": 12}
stu_demo_15qzdi3_21	2026-07-13 10:51:44.642989+00	2026-07-13 10:51:44.642989+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE021	Trần Thảo Vy	stu_demo_15qzdi3_21	\N	stu_demo_15qzdi3_21@student.demo.erg.edu.vn	MALE	2008-10-22 00:00:00+00	\N	\N	Phụ huynh Trần Thảo Vy	0979046443	\N	\N	2025-10-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.3, "lastActivityAt": 1783507902.165817400, "completedAssignments": 13}
stu_demo_15qzdi3_22	2026-07-13 10:51:44.760157+00	2026-07-13 10:51:44.760157+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE022	Lê Gia Hân	stu_demo_15qzdi3_22	\N	stu_demo_15qzdi3_22@student.demo.erg.edu.vn	FEMALE	2008-11-23 00:00:00+00	\N	\N	Phụ huynh Lê Gia Hân	0979046442	\N	\N	2025-09-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.4, "lastActivityAt": 1783421502.165817400, "completedAssignments": 14}
stu_demo_15qzdi3_23	2026-07-13 10:51:44.87788+00	2026-07-13 10:51:44.87788+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE023	Phạm Đức Anh	stu_demo_15qzdi3_23	\N	stu_demo_15qzdi3_23@student.demo.erg.edu.vn	MALE	2008-12-24 00:00:00+00	\N	\N	Phụ huynh Phạm Đức Anh	0979046441	\N	\N	2025-08-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.5, "lastActivityAt": 1783335102.165817400, "completedAssignments": 15}
stu_demo_15qzdi3_24	2026-07-13 10:51:44.994541+00	2026-07-13 10:51:44.994541+00	erg	019f5a8b-c5ea-730e-937c-293628da4628	cls_demo_24zqqe_b2	ERG24ZQQE024	Võ Hoàng Nam	stu_demo_15qzdi3_24	\N	stu_demo_15qzdi3_24@student.demo.erg.edu.vn	FEMALE	2008-01-25 00:00:00+00	\N	\N	Phụ huynh Võ Hoàng Nam	0979046440	\N	\N	2025-07-13 10:51:42.165817+00	\N	ACTIVE	{"averageScore": 8.6, "lastActivityAt": 1783939902.165817400, "completedAssignments": 16}
\.


--
-- Data for Name: lms_subjects; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_subjects (id, created_at, updated_at, tenant_id, scope, name, code, status) FROM stdin;
sub_math	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	\N	Mathematics	math	active
sub_toan	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Toán	toan	active
sub_ngu_van	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Ngữ Văn	ngu-van	active
sub_tieng_anh	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Tiếng Anh	tieng-anh	active
sub_vat_ly	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Vật Lý	vat-ly	active
sub_hoa_hoc	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Hóa Học	hoa-hoc	active
sub_sinh_hoc	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Sinh Học	sinh-hoc	active
sub_lich_su	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Lịch Sử	lich-su	active
sub_dia_ly	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	erg	\N	Địa Lý	dia-ly	active
ic3-gs6	2026-07-11 04:26:21.763365+00	2026-07-11 04:26:21.763365+00	erg	\N	IC3 GS6	ic3-gs6	ACTIVE
python	2026-07-11 04:26:21.915597+00	2026-07-11 04:26:21.915597+00	erg	\N	Python	python	ACTIVE
mos	2026-07-11 04:26:22.058431+00	2026-07-11 04:26:22.058431+00	erg	\N	MOS	mos	ACTIVE
sub_demo_18z1bdq_0	2026-07-13 09:13:36.972553+00	2026-07-13 09:13:36.972553+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-b528-7507-b347-52e990580f45"}	Tiếng Anh	ENG	ACTIVE
sub_demo_18z1bdq_1	2026-07-13 09:13:37.399621+00	2026-07-13 09:13:37.399621+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-b528-7507-b347-52e990580f45"}	Toán học	MATH	ACTIVE
sub_demo_18z1bdq_2	2026-07-13 09:13:37.827796+00	2026-07-13 09:13:37.827796+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-b528-7507-b347-52e990580f45"}	Khoa học	SCI	ACTIVE
sub_demo_18z1bdq_3	2026-07-13 09:13:38.257305+00	2026-07-13 09:13:38.257305+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-b528-7507-b347-52e990580f45"}	Tin học	ICT	ACTIVE
sub_demo_1cwzsfk_0	2026-07-13 09:15:04.739202+00	2026-07-13 09:15:04.739202+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-ba4d-7351-882a-4ef79911b783"}	Tiếng Anh	ENG	ACTIVE
sub_demo_1cwzsfk_1	2026-07-13 09:15:05.16512+00	2026-07-13 09:15:05.16512+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-ba4d-7351-882a-4ef79911b783"}	Toán học	MATH	ACTIVE
sub_demo_1cwzsfk_2	2026-07-13 09:15:05.590126+00	2026-07-13 09:15:05.590126+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-ba4d-7351-882a-4ef79911b783"}	Khoa học	SCI	ACTIVE
sub_demo_1cwzsfk_3	2026-07-13 09:15:06.016092+00	2026-07-13 09:15:06.016092+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-ba4d-7351-882a-4ef79911b783"}	Tin học	ICT	ACTIVE
sub_demo_aiw7ic_0	2026-07-13 09:23:47.811948+00	2026-07-13 09:23:47.811948+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-bece-7119-b8eb-77c56f9708e3"}	Tiếng Anh	ENG	ACTIVE
sub_demo_aiw7ic_1	2026-07-13 09:23:48.258948+00	2026-07-13 09:23:48.258948+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-bece-7119-b8eb-77c56f9708e3"}	Toán học	MATH	ACTIVE
sub_demo_aiw7ic_2	2026-07-13 09:23:48.692014+00	2026-07-13 09:23:48.692014+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-bece-7119-b8eb-77c56f9708e3"}	Khoa học	SCI	ACTIVE
sub_demo_aiw7ic_3	2026-07-13 09:23:49.123937+00	2026-07-13 09:23:49.123937+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-bece-7119-b8eb-77c56f9708e3"}	Tin học	ICT	ACTIVE
sub_demo_1aqllkt_0	2026-07-13 10:48:37.645155+00	2026-07-13 10:48:37.645155+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c2e5-785c-8cac-0a74b2b724de"}	Tiếng Anh	ENG	ACTIVE
sub_demo_1aqllkt_1	2026-07-13 10:48:38.238535+00	2026-07-13 10:48:38.238535+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c2e5-785c-8cac-0a74b2b724de"}	Toán học	MATH	ACTIVE
sub_demo_1aqllkt_2	2026-07-13 10:48:38.842678+00	2026-07-13 10:48:38.842678+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c2e5-785c-8cac-0a74b2b724de"}	Khoa học	SCI	ACTIVE
sub_demo_1aqllkt_3	2026-07-13 10:48:39.435465+00	2026-07-13 10:48:39.435465+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c2e5-785c-8cac-0a74b2b724de"}	Tin học	ICT	ACTIVE
sub_demo_24zqqe_0	2026-07-13 10:51:48.283833+00	2026-07-13 10:51:48.283833+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c5ea-730e-937c-293628da4628"}	Tiếng Anh	ENG	ACTIVE
sub_demo_24zqqe_1	2026-07-13 10:51:48.869595+00	2026-07-13 10:51:48.869595+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c5ea-730e-937c-293628da4628"}	Toán học	MATH	ACTIVE
sub_demo_24zqqe_2	2026-07-13 10:51:49.454746+00	2026-07-13 10:51:49.454746+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c5ea-730e-937c-293628da4628"}	Khoa học	SCI	ACTIVE
sub_demo_24zqqe_3	2026-07-13 10:51:50.057576+00	2026-07-13 10:51:50.057576+00	erg	{"type": "SCHOOL", "schoolId": "019f5a8b-c5ea-730e-937c-293628da4628"}	Tin học	ICT	ACTIVE
\.


--
-- Data for Name: lms_topics; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lms_topics (id, created_at, updated_at, tenant_id, level_id, name, code, sort_order, status) FROM stdin;
top_math_g6_num	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	erg	lvl_math_g6	Natural Numbers	natural-numbers	1	active
ic3-l1-topic-1	2026-07-11 04:26:23.554306+00	2026-07-11 04:26:23.554306+00	erg	ic3-l1	Thiet bi va he dieu hanh	ic3-l1-topic-1	1	ACTIVE
ic3-l1-topic-2	2026-07-11 04:26:23.709026+00	2026-07-11 04:26:23.709026+00	erg	ic3-l1	Quan ly tep	ic3-l1-topic-2	2	ACTIVE
ic3-l1-topic-3	2026-07-11 04:26:23.867843+00	2026-07-11 04:26:23.867843+00	erg	ic3-l1	Go phim va nhap lieu	ic3-l1-topic-3	3	ACTIVE
ic3-l1-topic-4	2026-07-11 04:26:24.005405+00	2026-07-11 04:26:24.005405+00	erg	ic3-l1	Internet an toan	ic3-l1-topic-4	4	ACTIVE
ic3-l1-topic-5	2026-07-11 04:26:24.147524+00	2026-07-11 04:26:24.147524+00	erg	ic3-l1	Email co ban	ic3-l1-topic-5	5	ACTIVE
ic3-l1-topic-6	2026-07-11 04:26:24.319004+00	2026-07-11 04:26:24.319004+00	erg	ic3-l1	Word can ban	ic3-l1-topic-6	6	ACTIVE
ic3-l1-topic-7	2026-07-11 04:26:24.466201+00	2026-07-11 04:26:24.466201+00	erg	ic3-l1	On tap level 1	ic3-l1-topic-7	7	ACTIVE
ic3-l2-topic-1	2026-07-11 04:26:24.601749+00	2026-07-11 04:26:24.601749+00	erg	ic3-l2	Word nang cao	ic3-l2-topic-1	1	ACTIVE
ic3-l2-topic-2	2026-07-11 04:26:24.739159+00	2026-07-11 04:26:24.739159+00	erg	ic3-l2	Excel nhap mon	ic3-l2-topic-2	2	ACTIVE
ic3-l2-topic-3	2026-07-11 04:26:24.89566+00	2026-07-11 04:26:24.89566+00	erg	ic3-l2	Cong thuc Excel	ic3-l2-topic-3	3	ACTIVE
ic3-l2-topic-4	2026-07-11 04:26:25.035816+00	2026-07-11 04:26:25.035816+00	erg	ic3-l2	PowerPoint	ic3-l2-topic-4	4	ACTIVE
ic3-l2-topic-5	2026-07-11 04:26:25.175123+00	2026-07-11 04:26:25.175123+00	erg	ic3-l2	Lam viec nhom online	ic3-l2-topic-5	5	ACTIVE
ic3-l2-topic-6	2026-07-11 04:26:25.310725+00	2026-07-11 04:26:25.310725+00	erg	ic3-l2	Tim kiem thong tin	ic3-l2-topic-6	6	ACTIVE
ic3-l2-topic-7	2026-07-11 04:26:25.46168+00	2026-07-11 04:26:25.46168+00	erg	ic3-l2	On tap level 2	ic3-l2-topic-7	7	ACTIVE
ic3-l3-topic-1	2026-07-11 04:26:25.601629+00	2026-07-11 04:26:25.601629+00	erg	ic3-l3	Bao mat tai khoan	ic3-l3-topic-1	1	ACTIVE
ic3-l3-topic-2	2026-07-11 04:26:25.740322+00	2026-07-11 04:26:25.740322+00	erg	ic3-l3	Du lieu va bieu do	ic3-l3-topic-2	2	ACTIVE
ic3-l3-topic-3	2026-07-11 04:26:25.87746+00	2026-07-11 04:26:25.87746+00	erg	ic3-l3	Tu duy thuat toan	ic3-l3-topic-3	3	ACTIVE
ic3-l3-topic-4	2026-07-11 04:26:26.023921+00	2026-07-11 04:26:26.023921+00	erg	ic3-l3	Ban quyen so	ic3-l3-topic-4	4	ACTIVE
ic3-l3-topic-5	2026-07-11 04:26:26.165668+00	2026-07-11 04:26:26.165668+00	erg	ic3-l3	Thuyet trinh du an	ic3-l3-topic-5	5	ACTIVE
ic3-l3-topic-6	2026-07-11 04:26:26.303931+00	2026-07-11 04:26:26.303931+00	erg	ic3-l3	Kiem tra mo phong IC3	ic3-l3-topic-6	6	ACTIVE
ic3-l3-topic-7	2026-07-11 04:26:26.452963+00	2026-07-11 04:26:26.452963+00	erg	ic3-l3	On tap level 3	ic3-l3-topic-7	7	ACTIVE
python-foundation-topic-1	2026-07-11 04:26:26.598292+00	2026-07-11 04:26:26.598292+00	erg	python-foundation	Bien va kieu du lieu	python-foundation-topic-1	1	ACTIVE
python-foundation-topic-2	2026-07-11 04:26:26.737744+00	2026-07-11 04:26:26.737744+00	erg	python-foundation	Dieu kien	python-foundation-topic-2	2	ACTIVE
python-foundation-topic-3	2026-07-11 04:26:26.876736+00	2026-07-11 04:26:26.876736+00	erg	python-foundation	Vong lap	python-foundation-topic-3	3	ACTIVE
python-foundation-topic-4	2026-07-11 04:26:27.014771+00	2026-07-11 04:26:27.014771+00	erg	python-foundation	Ham co ban	python-foundation-topic-4	4	ACTIVE
python-foundation-topic-5	2026-07-11 04:26:27.152938+00	2026-07-11 04:26:27.152938+00	erg	python-foundation	On tap Python foundation	python-foundation-topic-5	5	ACTIVE
python-intermediate-topic-1	2026-07-11 04:26:27.288823+00	2026-07-11 04:26:27.288823+00	erg	python-intermediate	List va dictionary	python-intermediate-topic-1	1	ACTIVE
python-intermediate-topic-2	2026-07-11 04:26:27.433506+00	2026-07-11 04:26:27.433506+00	erg	python-intermediate	Xu ly chuoi	python-intermediate-topic-2	2	ACTIVE
python-intermediate-topic-3	2026-07-11 04:26:27.573008+00	2026-07-11 04:26:27.573008+00	erg	python-intermediate	Doc ghi file	python-intermediate-topic-3	3	ACTIVE
python-intermediate-topic-4	2026-07-11 04:26:27.714056+00	2026-07-11 04:26:27.714056+00	erg	python-intermediate	Debug va test	python-intermediate-topic-4	4	ACTIVE
python-intermediate-topic-5	2026-07-11 04:26:27.851034+00	2026-07-11 04:26:27.851034+00	erg	python-intermediate	Mini project	python-intermediate-topic-5	5	ACTIVE
mos-word-topic-1	2026-07-11 04:26:28.010187+00	2026-07-11 04:26:28.010187+00	erg	mos-word	Dinh dang van ban	mos-word-topic-1	1	ACTIVE
mos-word-topic-2	2026-07-11 04:26:28.150718+00	2026-07-11 04:26:28.150718+00	erg	mos-word	Bang va hinh anh	mos-word-topic-2	2	ACTIVE
mos-word-topic-3	2026-07-11 04:26:28.291517+00	2026-07-11 04:26:28.291517+00	erg	mos-word	Tham chieu va mail merge	mos-word-topic-3	3	ACTIVE
mos-word-topic-4	2026-07-11 04:26:28.43042+00	2026-07-11 04:26:28.43042+00	erg	mos-word	On tap Word	mos-word-topic-4	4	ACTIVE
mos-excel-topic-1	2026-07-11 04:26:28.631461+00	2026-07-11 04:26:28.631461+00	erg	mos-excel	Cong thuc co ban	mos-excel-topic-1	1	ACTIVE
mos-excel-topic-2	2026-07-11 04:26:28.776995+00	2026-07-11 04:26:28.776995+00	erg	mos-excel	Bang du lieu	mos-excel-topic-2	2	ACTIVE
mos-excel-topic-3	2026-07-11 04:26:28.919071+00	2026-07-11 04:26:28.919071+00	erg	mos-excel	Bieu do	mos-excel-topic-3	3	ACTIVE
mos-excel-topic-4	2026-07-11 04:26:29.060849+00	2026-07-11 04:26:29.060849+00	erg	mos-excel	On tap Excel	mos-excel-topic-4	4	ACTIVE
mos-powerpoint-topic-1	2026-07-11 04:26:29.201202+00	2026-07-11 04:26:29.201202+00	erg	mos-powerpoint	Bo cuc slide	mos-powerpoint-topic-1	1	ACTIVE
mos-powerpoint-topic-2	2026-07-11 04:26:29.346223+00	2026-07-11 04:26:29.346223+00	erg	mos-powerpoint	Transition va animation	mos-powerpoint-topic-2	2	ACTIVE
mos-powerpoint-topic-3	2026-07-11 04:26:29.483876+00	2026-07-11 04:26:29.483876+00	erg	mos-powerpoint	Trinh chieu	mos-powerpoint-topic-3	3	ACTIVE
mos-powerpoint-topic-4	2026-07-11 04:26:29.62435+00	2026-07-11 04:26:29.62435+00	erg	mos-powerpoint	On tap PowerPoint	mos-powerpoint-topic-4	4	ACTIVE
\.


--
-- Data for Name: media_assets; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.media_assets (id, created_at, updated_at, tenant_id, sha256, storage_key, url, mime_type, byte_size, width, height, original_name, status, uploaded_by, ref_count) FROM stdin;
\.


--
-- Data for Name: media_references; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.media_references (id, created_at, updated_at, tenant_id, media_id, owner_type, owner_id) FROM stdin;
\.


--
-- Data for Name: notification_delivery_attempts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_delivery_attempts (id, created_at, updated_at, tenant_id, outbox_id, notification_id, device_registration_id, target_kind, target_ref_hash, provider, provider_message_id, http_status, firebase_error_code, status, duration_ms, response_sample) FROM stdin;
\.


--
-- Data for Name: notification_device_registrations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_device_registrations (id, created_at, updated_at, tenant_id, user_id, portal, platform, firebase_installation_id, registration_token, registration_token_hash, endpoint_hash, service_worker_scope, browser_name, browser_version, os_name, user_agent_hash, locale, timezone, permission_state, enabled, last_seen_at, token_updated_at, revoked_at) FROM stdin;
\.


--
-- Data for Name: notification_messages; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_messages (id, created_at, updated_at, tenant_id, event_type, source_type, source_id, title, body_preview, action_url, priority, channel, visibility, collapse_key, dedupe_key, payload_data, created_by, scheduled_at, expires_at) FROM stdin;
\.


--
-- Data for Name: notification_outbox; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_outbox (id, created_at, updated_at, tenant_id, notification_id, target_kind, target_ref, payload, status, attempt_count, max_attempts, next_attempt_at, locked_by, locked_at, last_error_code, last_error_message) FROM stdin;
\.


--
-- Data for Name: notification_preferences; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_preferences (id, created_at, updated_at, tenant_id, user_id, portal, event_type, push_enabled, inbox_enabled, email_enabled, quiet_hours) FROM stdin;
\.


--
-- Data for Name: notification_recipients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_recipients (id, created_at, updated_at, tenant_id, notification_id, user_id, portal, role_key, status, read_at, archived_at, delivery_policy) FROM stdin;
\.


--
-- Data for Name: notification_retention_config; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_retention_config (id, table_name, retention_days, enabled, created_at, updated_at) FROM stdin;
nrc_delivery_attempts	notification_delivery_attempts	90	t	2026-07-11 03:05:13.406579+00	2026-07-11 03:05:13.406579+00
\.


--
-- Data for Name: notification_topic_memberships; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_topic_memberships (id, created_at, updated_at, tenant_id, device_registration_id, topic_name, membership_status, subscribed_at, unsubscribed_at, last_sync_at) FROM stdin;
\.


--
-- Data for Name: permission_bindings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permission_bindings (id, permission_id, surface_type, surface_identifier, description, enforced, created_at, updated_at) FROM stdin;
pb_b5aeb4a093330c64149687ac8c203fd8	p_acct_password_change	SERVICE	ErgPolicyService#isAllowed:account.self.password.change	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_007715b31cacf190724e9fa88443ee1a	p_acct_self_read	SERVICE	ErgPolicyService#isAllowed:account.self.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_61b89542c3be27c777e0805eaa40dfee	p_acct_recovery_email_update	SERVICE	ErgPolicyService#isAllowed:account.self.recovery_email.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_ccd5cb0e4a5cca13e9d60639bf70dd88	p_acct_sessions_read	SERVICE	ErgPolicyService#isAllowed:account.self.sessions.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_810a1f6539a09e9aff505bf143268aed	p_acct_self_update	SERVICE	ErgPolicyService#isAllowed:account.self.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1c7648c9746dc7e24c142dcaadf2b4e2	perm_ai_content_use	SERVICE	ErgPolicyService#isAllowed:ai-content.use	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_baf7179f411ca1f38146cc6849a49e9c	perm_api_keys_manage	SERVICE	ErgPolicyService#isAllowed:api-keys.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_bdf5c5c6d3b2869e4b1f6b6528d819f6	perm_cms_categories_create	SERVICE	ErgPolicyService#isAllowed:cms.categories.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_b228420516b19924cd9789c8949341a8	perm_cms_categories_delete	SERVICE	ErgPolicyService#isAllowed:cms.categories.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_57168718b6156384378396cc89bd3174	perm_cms_categories_update	SERVICE	ErgPolicyService#isAllowed:cms.categories.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_588ca999c04ece89e1b5c34353fe4b5f	perm_cms_posts_create	SERVICE	ErgPolicyService#isAllowed:cms.posts.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_0826cac7baebef71d3b7e96217d6e06b	perm_cms_posts_delete	SERVICE	ErgPolicyService#isAllowed:cms.posts.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_c76b7506d146031c8cea77cb2866ba84	perm_cms_posts_update	SERVICE	ErgPolicyService#isAllowed:cms.posts.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_917d68f5149d5de8182b6258f854a81a	perm_content_manage	SERVICE	ErgPolicyService#isAllowed:content.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1921bcdfcc16039da66941136bead69f	perm_content_read	SERVICE	ErgPolicyService#isAllowed:content.resource.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_7296b174f3b7d7724a8fdccb91be18ee	perm_courses_create	SERVICE	ErgPolicyService#isAllowed:courses.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_fa2acacf951f702e81e609c90bc57210	perm_courses_delete	SERVICE	ErgPolicyService#isAllowed:courses.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_2f2ab9070437d9c9231e85c709dbd553	perm_courses_update	SERVICE	ErgPolicyService#isAllowed:courses.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_2f5d9e59b9f6ae812b48b1c218e803d2	perm_crawler_manage	SERVICE	ErgPolicyService#isAllowed:crawler.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_a6de908b024838805e23f6df7bc5748a	p_crm_analytics_read	SERVICE	ErgPolicyService#isAllowed:crm.analytics.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_079a0743d41d00e8ecfff2fcf8ee0e31	p_crm_deal_manage	SERVICE	ErgPolicyService#isAllowed:crm.deal.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_4fdc13e008b3351c8c4a7997ea1aa4fb	p_crm_school_read	SERVICE	ErgPolicyService#isAllowed:crm.school.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_5724fe1eb97aeed8bf35c86fe3360848	perm_curr_manage	SERVICE	ErgPolicyService#isAllowed:curriculum.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_9336405603269231d141b9ad28d54cf7	perm_documents_manage	SERVICE	ErgPolicyService#isAllowed:documents.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_f416289790d4dd00d80c4a29153264d9	perm_elearning_manage	SERVICE	ErgPolicyService#isAllowed:elearning.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_3772b6c19435a5d01f19ed28e84f053c	p_lms_assignment_assign	SERVICE	ErgPolicyService#isAllowed:lms.assignment.assign	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_6bbd94a3fb654704fab6b1f906099961	p_lms_assignment_create	SERVICE	ErgPolicyService#isAllowed:lms.assignment.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_a9620d1688526291753ec6b8028dca5e	p_lms_assignment_delete	SERVICE	ErgPolicyService#isAllowed:lms.assignment.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_fd2994ee183da28fadea6b6da572d68e	p_lms_assignment_progress_read	SERVICE	ErgPolicyService#isAllowed:lms.assignment.progress.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_d9ee18d13474e0d035cd9c826fecc665	p_lms_assignment_read	SERVICE	ErgPolicyService#isAllowed:lms.assignment.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_8555ab2aeaed53c3bcf4ab8649b4aa05	perm_lms_assign_self	SERVICE	ErgPolicyService#isAllowed:lms.assignment.read_self	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_859b5ed659ff6ad8337c06162cf7dc51	perm_lms_submit	SERVICE	ErgPolicyService#isAllowed:lms.assignment.submit	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_9188a86b2e357ef499810db577d723c2	p_lms_assignment_update	SERVICE	ErgPolicyService#isAllowed:lms.assignment.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_b619ef616313e5563502a7fbccac74bc	perm_lms_class_manage	SERVICE	ErgPolicyService#isAllowed:lms.class.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_9108ff799cac8d95e6c5dfa574c4cbd6	perm_lms_class_read	SERVICE	ErgPolicyService#isAllowed:lms.class.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_0615cf6bec3f18e82e85f7cf03acd365	p_lms_exercise_create	SERVICE	ErgPolicyService#isAllowed:lms.exercise.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_37a57e64e080cb76d45995dafe2919c7	p_lms_exercise_delete	SERVICE	ErgPolicyService#isAllowed:lms.exercise.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_30d48ff7509b28f32582f6a41c357584	p_lms_exercise_read	SERVICE	ErgPolicyService#isAllowed:lms.exercise.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_35c48af8aeea4317f80e4197f3730c4d	p_lms_exercise_update	SERVICE	ErgPolicyService#isAllowed:lms.exercise.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_d388df938435ba2329cddce5e33772bc	p_lms_grade_read	SERVICE	ErgPolicyService#isAllowed:lms.grade.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1c007ae4ed52e7328fc27287350b41bf	perm_lms_manage	SERVICE	ErgPolicyService#isAllowed:lms.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_16a75396622026b0569bdefc51e330e8	p_lms_notification_prefs_update	SERVICE	ErgPolicyService#isAllowed:lms.notification.preferences.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_32c546413a08dda3cbd65babb81c50fb	p_lms_notification_read	SERVICE	ErgPolicyService#isAllowed:lms.notification.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_f953f7c61462684797aa6997f535ca20	perm_lms_progress_read	SERVICE	ErgPolicyService#isAllowed:lms.progress.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_583035e155cc0c4103e0b85675063772	perm_lms_quiz_create	SERVICE	ErgPolicyService#isAllowed:lms.quiz.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_95a7b9dbca483ae07bd7a26cda6300c3	perm_lms_quiz_publish	SERVICE	ErgPolicyService#isAllowed:lms.quiz.publish	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_f13d98c0b0dbd347370f05a4d4241ba4	perm_lms_quiz_read	SERVICE	ErgPolicyService#isAllowed:lms.quiz.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_62185e0a4d272b87da6668b64ae62bbe	p_lms_report_read	SERVICE	ErgPolicyService#isAllowed:lms.report.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1a03d139bd4eeefe70810c4cc05bf041	p_lms_resource_create	SERVICE	ErgPolicyService#isAllowed:lms.resource.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_c90a0b5dbfa8e595fe7aeb7527e21264	p_lms_resource_delete	SERVICE	ErgPolicyService#isAllowed:lms.resource.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_ff9e11fa94a8e538b2e78edc2a971f24	p_lms_resource_read	SERVICE	ErgPolicyService#isAllowed:lms.resource.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_f57544e7b4ef0e3e53d072a595d5a0f6	p_lms_resource_share	SERVICE	ErgPolicyService#isAllowed:lms.resource.share	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_fed7b3b489ce4bdc96d41dd1ec6b00b6	p_lms_resource_update	SERVICE	ErgPolicyService#isAllowed:lms.resource.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_4388b4d99bc6b7b858a71918a797ac4c	p_lms_schedule_create	SERVICE	ErgPolicyService#isAllowed:lms.schedule.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_eb412355282f43e175e9e1596c6fba64	p_lms_schedule_delete	SERVICE	ErgPolicyService#isAllowed:lms.schedule.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_e35a1c8b76884f595a4050aaae3b5338	p_lms_schedule_import	SERVICE	ErgPolicyService#isAllowed:lms.schedule.import	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_b4b45d43a6652fd645df3b957630a363	perm_lms_schedule_manage	SERVICE	ErgPolicyService#isAllowed:lms.schedule.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_204881b9d2a6e6aa1ede11a17c546216	p_lms_schedule_publish	SERVICE	ErgPolicyService#isAllowed:lms.schedule.publish	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_461a5c4ccaea7b8d7f202ad33dc300a6	perm_lms_schedule_read	SERVICE	ErgPolicyService#isAllowed:lms.schedule.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1985c80745fd172c650a932025cf6e30	perm_lms_schedule_read_self	SERVICE	ErgPolicyService#isAllowed:lms.schedule.read_self	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_2b27f57b51b7e3fc0fe3561e0dda1652	p_lms_schedule_update	SERVICE	ErgPolicyService#isAllowed:lms.schedule.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_ca27c082b7324d80695ef6f85ed94a9d	perm_lms_school_archive	SERVICE	ErgPolicyService#isAllowed:lms.school.archive	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_7d8c94384bb0b2b5ac48cfccaf1fbe52	perm_lms_school_create	SERVICE	ErgPolicyService#isAllowed:lms.school.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_e2daa3cb1334fbff406d314807849bd8	perm_lms_school_read	SERVICE	ErgPolicyService#isAllowed:lms.school.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_69155977e02ee89bf374ad6af310d819	perm_lms_school_update	SERVICE	ErgPolicyService#isAllowed:lms.school.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_f3c2cd895658e7bab5a95eaca2110aa0	perm_lms_scope_read	SERVICE	ErgPolicyService#isAllowed:lms.scope.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_53a5889c248914bca1fd7759078177e8	p_lms_student_group_create	SERVICE	ErgPolicyService#isAllowed:lms.student_group.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_c477017599232334c2bec3546e6eae7c	p_lms_student_group_delete	SERVICE	ErgPolicyService#isAllowed:lms.student_group.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_0b7b8b26e275be382e10d9a3624213ec	p_lms_student_group_read	SERVICE	ErgPolicyService#isAllowed:lms.student_group.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_5d8aebe2c3d1fd413efa840f80fbc845	p_lms_student_group_update	SERVICE	ErgPolicyService#isAllowed:lms.student_group.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_391fdd64c464f35c74c408da321d7197	perm_media_delete	SERVICE	ErgPolicyService#isAllowed:media.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_a80bd52939ebf3a70631e53bb3283f48	perm_media_upload	SERVICE	ErgPolicyService#isAllowed:media.upload	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_b00f8b3bf98ae1c914663ac0073ed895	perm_notifications_manage	SERVICE	ErgPolicyService#isAllowed:notifications.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1ff196112e2c40c0e6c4b5b36c3bc659	perm_operations_manage	SERVICE	ErgPolicyService#isAllowed:operations.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_5ab8e9360b37e60dae23ea55d9929702	perm_platform_read	SERVICE	ErgPolicyService#isAllowed:platform.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_94cc143b93944c8404be80015a075b83	perm_profile_self	SERVICE	ErgPolicyService#isAllowed:profile.self	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_6e71ca2b3d8e9e9c4eb4f5db5908659f	perm_public_disclosure_manage	SERVICE	ErgPolicyService#isAllowed:public-disclosure.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_333654e3d6a71ddd0fda5b51969e86c0	perm_rbac_permission_read	SERVICE	ErgPolicyService#isAllowed:rbac.permission.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_1ad76158e7092e5fa8df6e69dd2229ed	perm_rbac_role_assign	SERVICE	ErgPolicyService#isAllowed:rbac.role.assign	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_bf7b8cffa1ba287ba7eceb67fab302db	perm_rbac_role_create	SERVICE	ErgPolicyService#isAllowed:rbac.role.create	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_e71d86be4a5e0f541d184166a5c7c42d	perm_rbac_role_delete	SERVICE	ErgPolicyService#isAllowed:rbac.role.delete	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_6f0b6b955c3ee0505c6e70a06c43eb4c	perm_rbac_role_read	SERVICE	ErgPolicyService#isAllowed:rbac.role.read	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_70db5b5214b069b2336f06ade19ba8c3	perm_rbac_role_update	SERVICE	ErgPolicyService#isAllowed:rbac.role.update	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_23c3316200b389f2869d3d2003f20007	perm_seo_manage	SERVICE	ErgPolicyService#isAllowed:seo.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
pb_2260fc95d89c8bed8bd329e581705796	perm_users_manage	SERVICE	ErgPolicyService#isAllowed:users.manage	Runtime permission evaluated by the shared REST/GraphQL policy engine	t	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00
\.


--
-- Data for Name: permission_groups; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permission_groups (id, created_at, updated_at, name, label, display_order) FROM stdin;
pg_system	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	system	System Administration	10
pg_rbac	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	rbac	Roles and Permissions	20
pg_users	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	users	User Management	30
pg_lms	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms	Learning Management	40
pg_crm	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm	CRM and School Sales	35
pg_org	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	organization	Organization Management	32
pg_lms_schedule	2026-07-11 03:05:03.106126+00	2026-07-11 03:05:03.106126+00	LMS Schedule	Teaching schedule permissions	30
pg_content	2026-07-11 03:06:46.885533+00	2026-07-11 03:06:46.885533+00	content	Content and Curriculum	50
pg_lms_school_ops	2026-07-13 07:52:08.106576+00	2026-07-13 07:52:08.106576+00	LMS School Operations	School operations permissions	31
pg_account	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	account	Account Self Service	10
pg_ai_content	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	ai-content	AI Content	70
pg_api_keys	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	api-keys	API Keys	80
pg_cms	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms	CMS	50
pg_courses	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	courses	Courses	45
pg_crawler	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	crawler	Crawler	90
pg_documents	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	documents	Documents	55
pg_elearning	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	elearning	Elearning	40
pg_media	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	media	Media	60
pg_notifications	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	notifications	Notifications	65
pg_operations	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	operations	Operations	95
pg_platform	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	platform	Platform	100
pg_public_disclosure	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	public-disclosure	Public Disclosure	75
pg_seo	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	seo	SEO	85
\.


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permissions (id, created_at, updated_at, name, group_name, label, description, resource, action, portal, supported_scope, is_system, status, deprecated_at) FROM stdin;
perm_users_manage	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	users.manage	users	Manage users	Create, update, ban, unban, and manage user accounts	manage	manage	users	\N	f	ACTIVE	\N
perm_rbac_manage	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	rbac.*	rbac	Manage RBAC	Manage roles, permissions, and overrides	*	*	rbac	\N	f	ACTIVE	\N
perm_lms_read	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.read	lms	Read LMS	Read LMS data	read	read	lms	\N	f	ACTIVE	\N
perm_lms_manage	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.manage	lms	Manage LMS	Manage LMS data	manage	manage	lms	\N	f	ACTIVE	\N
perm_lms_assign_self	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.assignment.read_self	lms	Read own assignments	Students can read their own assignments	assignment.read_self	read_self	lms	\N	f	ACTIVE	\N
perm_lms_submit	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.assignment.submit	lms	Submit assignment	Students can submit attempts	assignment.submit	submit	lms	\N	f	ACTIVE	\N
p_lms_portal_access	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.portal.access	lms	Access LMS portal	Enter the LMS teacher portal	portal.access	access	lms	\N	f	ACTIVE	\N
p_crm_school_create	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm.school.create	crm	Create prospect schools	Create school master records from CRM prospecting	school.create	create	crm	\N	f	ACTIVE	\N
p_crm_school_read	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm.school.read	crm	Read CRM schools	Read schools and their CRM profile	school.read	read	crm	\N	f	ACTIVE	\N
p_crm_deal_manage	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm.deal.manage	crm	Manage school deals	Manage CRM deal pipeline, signed revenue, and lost reasons	deal.manage	manage	crm	\N	f	ACTIVE	\N
p_crm_contact_manage	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm.contact.manage	crm	Manage school contacts	Manage principal, vice principal, teacher, and other school contacts	contact.manage	manage	crm	\N	f	ACTIVE	\N
p_crm_activity_manage	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm.activity.manage	crm	Manage CRM activities	Manage calls, meetings, demos, notes, and follow-up activities	activity.manage	manage	crm	\N	f	ACTIVE	\N
p_crm_analytics_read	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	crm.analytics.read	crm	Read CRM analytics	Read conversion, signed revenue, lost rate, and SEO performance metrics	analytics.read	read	crm	\N	f	ACTIVE	\N
p_org_school_activate	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	organization.school.activate	organization	Activate schools	Activate signed schools for operational LMS usage	school.activate	activate	organization	\N	f	ACTIVE	\N
perm_lms_schedule_read_self	2026-07-11 03:05:03.106126+00	2026-07-11 03:05:03.106126+00	lms.schedule.read_self	lms	View own schedule	View teaching schedule assigned to the current user	schedule.read_self	read_self	lms	\N	f	ACTIVE	\N
perm_lms_schedule_read	2026-07-11 03:05:03.106126+00	2026-07-11 03:05:03.106126+00	lms.schedule.read	lms	View aggregate schedule	View teaching schedule across managed education units	schedule.read	read	lms	\N	f	ACTIVE	\N
perm_lms_schedule_manage	2026-07-11 03:05:03.106126+00	2026-07-11 03:05:03.106126+00	lms.schedule.manage	lms	Manage teaching schedule	Create, update, delete, and publish teaching schedules	schedule.manage	manage	lms	\N	f	ACTIVE	\N
perm_content_read	2026-07-11 03:06:46.885533+00	2026-07-11 03:06:46.885533+00	content.resource.read	content	Read content resources	Read curriculum-linked learning resources	resource.read	read	content	\N	f	ACTIVE	\N
perm_content_manage	2026-07-11 03:06:46.885533+00	2026-07-11 03:06:46.885533+00	content.manage	content	Manage content	Manage curriculum-linked content resources and assets	manage	manage	content	\N	f	ACTIVE	\N
perm_curr_manage	2026-07-11 03:06:46.885533+00	2026-07-11 03:06:46.885533+00	curriculum.manage	content	Manage curriculum	Manage subjects, levels, and topics	manage	manage	curriculum	\N	f	ACTIVE	\N
perm_all	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:15.990115+00	*	system	Full system access	Full system access	*	*	*	\N	f	ACTIVE	\N
perm_profile_self	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:16.15781+00	profile.self	users	Access own session/profile	Access own session/profile	self	self	profile	\N	f	ACTIVE	\N
perm_lms_unit_read	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:16.561083+00	lms.unit.read	lms	Read education units	Read education units	unit.read	read	lms	\N	f	ACTIVE	\N
perm_lms_class_read	2026-07-11 03:03:41.741114+00	2026-07-11 04:26:17.479925+00	lms.class.read	lms	Read classes	Read classes	class.read	read	lms	\N	f	ACTIVE	\N
perm_lms_school_read	2026-07-13 07:52:08.106576+00	2026-07-13 07:52:08.106576+00	lms.school.read	lms	View schools	View school directory and operational workspaces	school.read	read	lms	\N	f	ACTIVE	\N
perm_lms_school_create	2026-07-13 07:52:08.106576+00	2026-07-13 07:52:08.106576+00	lms.school.create	lms	Create schools	Create a school and its initial academic structure	school.create	create	lms	\N	f	ACTIVE	\N
p_lms_report_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.report.read	lms	Read reports	View LMS reports	report.read	read	lms	\N	f	ACTIVE	\N
perm_lms_school_update	2026-07-13 07:52:08.106576+00	2026-07-13 07:52:08.106576+00	lms.school.update	lms	Update schools	Update school profile, academic years, and default grades	school.update	update	lms	\N	f	ACTIVE	\N
perm_lms_school_archive	2026-07-13 07:52:08.106576+00	2026-07-13 07:52:08.106576+00	lms.school.archive	lms	Archive schools	Archive and restore schools	school.archive	archive	lms	\N	f	ACTIVE	\N
perm_lms_class_manage	2026-07-13 07:52:08.106576+00	2026-07-13 07:52:08.106576+00	lms.class.manage	lms	Manage classes	Manage grades and classes within an allowed school	class.manage	manage	lms	\N	f	ACTIVE	\N
p_lms_dashboard_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.dashboard.read	lms	Read LMS dashboard	View the LMS teacher dashboard	dashboard.read	read	lms	\N	f	ACTIVE	\N
p_lms_assignment_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.assignment.read	lms	Read assignments	View homework assignments	assignment.read	read	lms	\N	f	ACTIVE	\N
p_lms_assignment_create	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.assignment.create	lms	Create assignments	Create homework assignments	assignment.create	create	lms	\N	f	ACTIVE	\N
p_lms_assignment_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.assignment.update	lms	Update assignments	Edit homework assignments	assignment.update	update	lms	\N	f	ACTIVE	\N
p_lms_assignment_delete	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.assignment.delete	lms	Delete assignments	Remove homework assignments	assignment.delete	delete	lms	\N	f	ACTIVE	\N
p_lms_assignment_assign	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.assignment.assign	lms	Assign homework	Assign homework to classes or students	assignment.assign	assign	lms	\N	f	ACTIVE	\N
p_lms_assignment_progress_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.assignment.progress.read	lms	Read assignment progress	View homework submission progress	assignment.progress.read	read	lms	\N	f	ACTIVE	\N
p_lms_exercise_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.exercise.read	lms	Read exercise bank	View the exercise bank	exercise.read	read	lms	\N	f	ACTIVE	\N
p_lms_exercise_create	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.exercise.create	lms	Create exercises	Create exercise-bank items	exercise.create	create	lms	\N	f	ACTIVE	\N
p_lms_exercise_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.exercise.update	lms	Update exercises	Edit exercise-bank items	exercise.update	update	lms	\N	f	ACTIVE	\N
p_lms_exercise_delete	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.exercise.delete	lms	Delete exercises	Remove exercise-bank items	exercise.delete	delete	lms	\N	f	ACTIVE	\N
p_lms_exercise_assign	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.exercise.assign	lms	Assign exercises	Assign exercise-bank items to homework	exercise.assign	assign	lms	\N	f	ACTIVE	\N
p_lms_student_group_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.student_group.read	lms	Read student groups	View student groups	student_group.read	read	lms	\N	f	ACTIVE	\N
p_lms_student_group_create	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.student_group.create	lms	Create student groups	Create student groups	student_group.create	create	lms	\N	f	ACTIVE	\N
p_lms_student_group_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.student_group.update	lms	Update student groups	Edit student groups	student_group.update	update	lms	\N	f	ACTIVE	\N
p_lms_student_group_delete	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.student_group.delete	lms	Delete student groups	Remove student groups	student_group.delete	delete	lms	\N	f	ACTIVE	\N
p_lms_student_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.student.read	lms	Read students	View student profiles within scope	student.read	read	lms	\N	f	ACTIVE	\N
p_lms_grade_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.grade.read	lms	Read grades	View the grade book	grade.read	read	lms	\N	f	ACTIVE	\N
p_lms_grade_write	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.grade.write	lms	Write grades	Enter and edit grades	grade.write	write	lms	\N	f	ACTIVE	\N
p_lms_grade_finalize	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.grade.finalize	lms	Finalize grades	Finalize and lock grade books	grade.finalize	finalize	lms	\N	f	ACTIVE	\N
p_lms_grade_export	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.grade.export	lms	Export grades	Export grade books	grade.export	export	lms	\N	f	ACTIVE	\N
p_lms_attendance_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.attendance.read	lms	Read attendance	View attendance records	attendance.read	read	lms	\N	f	ACTIVE	\N
p_lms_attendance_write	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.attendance.write	lms	Write attendance	Record and edit attendance	attendance.write	write	lms	\N	f	ACTIVE	\N
p_lms_attendance_export	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.attendance.export	lms	Export attendance	Export attendance records	attendance.export	export	lms	\N	f	ACTIVE	\N
p_lms_schedule_create	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.schedule.create	lms	Create schedule	Create calendar events	schedule.create	create	lms	\N	f	ACTIVE	\N
p_lms_schedule_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.schedule.update	lms	Update schedule	Edit calendar events	schedule.update	update	lms	\N	f	ACTIVE	\N
p_lms_schedule_delete	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.schedule.delete	lms	Delete schedule	Remove calendar events	schedule.delete	delete	lms	\N	f	ACTIVE	\N
p_lms_schedule_publish	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.schedule.publish	lms	Publish schedule	Publish the teaching calendar	schedule.publish	publish	lms	\N	f	ACTIVE	\N
p_lms_schedule_import	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.schedule.import	lms	Import schedule	Import teaching calendar data	schedule.import	import	lms	\N	f	ACTIVE	\N
p_lms_class_log_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.class_log.read	lms	Read class log	View the class log	class_log.read	read	lms	\N	f	ACTIVE	\N
p_lms_class_log_write	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.class_log.write	lms	Write class log	Record class-log entries	class_log.write	write	lms	\N	f	ACTIVE	\N
p_lms_resource_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.resource.read	lms	Read resources	View learning resources	resource.read	read	lms	\N	f	ACTIVE	\N
p_lms_resource_create	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.resource.create	lms	Create resources	Create learning resources	resource.create	create	lms	\N	f	ACTIVE	\N
p_lms_resource_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.resource.update	lms	Update resources	Edit learning resources	resource.update	update	lms	\N	f	ACTIVE	\N
p_lms_resource_delete	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.resource.delete	lms	Delete resources	Remove learning resources	resource.delete	delete	lms	\N	f	ACTIVE	\N
p_lms_resource_share	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.resource.share	lms	Share resources	Share learning resources	resource.share	share	lms	\N	f	ACTIVE	\N
p_lms_report_export	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.report.export	lms	Export reports	Export LMS reports	report.export	export	lms	\N	f	ACTIVE	\N
p_lms_notification_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.notification.read	lms	Read notifications	View LMS notifications	notification.read	read	lms	\N	f	ACTIVE	\N
p_lms_notification_prefs_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms.notification.preferences.update	lms	Update notification preferences	Manage notification preferences	notification.preferences.update	update	lms	\N	f	ACTIVE	\N
p_acct_self_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	account.self.read	account	Read own account	View own account and profile	self.read	read	account	\N	f	ACTIVE	\N
p_acct_self_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	account.self.update	account	Update own account	Edit own profile and phone	self.update	update	account	\N	f	ACTIVE	\N
p_acct_recovery_email_update	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	account.self.recovery_email.update	account	Update recovery email	Set and verify own recovery email	self.recovery_email.update	update	account	\N	f	ACTIVE	\N
p_acct_password_change	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	account.self.password.change	account	Change own password	Change own password	self.password.change	change	account	\N	f	ACTIVE	\N
p_acct_sessions_read	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	account.self.sessions.read	account	Read own sessions	View own active sessions	self.sessions.read	read	account	\N	f	ACTIVE	\N
perm_rbac_role_read	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	rbac.role.read	rbac	Xem nhóm quyền	Read role definitions	role	read	rbac	system	t	ACTIVE	\N
perm_rbac_role_create	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	rbac.role.create	rbac	Tạo nhóm quyền	Create role definitions	role	create	rbac	system	t	ACTIVE	\N
perm_rbac_role_update	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	rbac.role.update	rbac	Sửa nhóm quyền	Update role definitions	role	update	rbac	system	t	ACTIVE	\N
perm_rbac_role_delete	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	rbac.role.delete	rbac	Xóa nhóm quyền	Delete role definitions	role	delete	rbac	system	t	ACTIVE	\N
perm_rbac_role_assign	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	rbac.role.assign	rbac	Gán nhóm quyền	Assign roles to users	role	assign	rbac	tenant,school	t	ACTIVE	\N
perm_rbac_permission_read	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	rbac.permission.read	rbac	Xem quyền hệ thống	Read the permission catalog	permission	read	rbac	system	t	ACTIVE	\N
perm_lms_quiz_read	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	lms.quiz.read	lms	Xem bài kiểm tra	Read quizzes	quiz	read	lms	school,class	f	ACTIVE	\N
perm_lms_quiz_create	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	lms.quiz.create	lms	Tạo bài kiểm tra	Create quizzes	quiz	create	lms	school,class	f	ACTIVE	\N
perm_lms_quiz_publish	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	lms.quiz.publish	lms	Xuất bản bài kiểm tra	Publish quizzes	quiz	publish	lms	school,class	f	ACTIVE	\N
perm_api_keys_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	api-keys.manage	api-keys	Quản lý API key	Manage API keys	api-key	manage	admin	system	t	ACTIVE	\N
perm_ai_content_use	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	ai-content.use	ai-content	Sử dụng AI nội dung	Use AI content services	content	use	lcms	tenant,school	f	ACTIVE	\N
perm_operations_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	operations.manage	operations	Quản lý vận hành	Manage platform operations	operations	manage	admin	system	t	ACTIVE	\N
perm_platform_read	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	platform.read	platform	Xem nền tảng	Read platform configuration	platform	read	admin	system	t	ACTIVE	\N
perm_crawler_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	crawler.manage	crawler	Quản lý crawler	Manage crawler jobs	crawler	manage	admin	system	t	ACTIVE	\N
perm_notifications_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	notifications.manage	notifications	Quản lý thông báo	Manage administrative notifications	notification	manage	admin	tenant	t	ACTIVE	\N
perm_elearning_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	elearning.manage	elearning	Quản lý Elearning	Manage elearning operations	elearning	manage	elearning	tenant,school	f	ACTIVE	\N
perm_seo_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	seo.manage	seo	Quản lý SEO	Manage SEO operations	seo	manage	crm	tenant	f	ACTIVE	\N
perm_cms_posts_create	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms.posts.create	cms	Tạo bài viết	Create CMS posts	posts	create	lcms	tenant	f	ACTIVE	\N
perm_cms_posts_update	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms.posts.update	cms	Sửa bài viết	Update CMS posts	posts	update	lcms	tenant	f	ACTIVE	\N
perm_cms_posts_delete	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms.posts.delete	cms	Xóa bài viết	Delete CMS posts	posts	delete	lcms	tenant	f	ACTIVE	\N
perm_cms_categories_create	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms.categories.create	cms	Tạo danh mục	Create CMS categories	categories	create	lcms	tenant	f	ACTIVE	\N
perm_cms_categories_update	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms.categories.update	cms	Sửa danh mục	Update CMS categories	categories	update	lcms	tenant	f	ACTIVE	\N
perm_cms_categories_delete	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	cms.categories.delete	cms	Xóa danh mục	Delete CMS categories	categories	delete	lcms	tenant	f	ACTIVE	\N
perm_media_upload	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	media.upload	media	Tải media lên	Upload media	media	upload	lcms	tenant	f	ACTIVE	\N
perm_media_delete	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	media.delete	media	Xóa media	Delete media	media	delete	lcms	tenant	f	ACTIVE	\N
perm_courses_create	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	courses.create	courses	Tạo khóa học	Create courses	courses	create	elearning	tenant,school	f	ACTIVE	\N
perm_courses_update	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	courses.update	courses	Sửa khóa học	Update courses	courses	update	elearning	tenant,school	f	ACTIVE	\N
perm_courses_delete	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	courses.delete	courses	Xóa khóa học	Delete courses	courses	delete	elearning	tenant,school	f	ACTIVE	\N
perm_documents_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	documents.manage	documents	Quản lý tài liệu	Manage documents	documents	manage	lcms	tenant,school	f	ACTIVE	\N
perm_public_disclosure_manage	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	public-disclosure.manage	public-disclosure	Quản lý công khai	Manage public disclosure	public-disclosure	manage	admin	tenant	t	ACTIVE	\N
perm_lms_progress_read	2026-07-15 12:08:03.77414+00	2026-07-15 12:08:03.77414+00	lms.progress.read	lms	Xem tiến độ LMS	Read learning progress	progress	read	lms	school,class	f	ACTIVE	\N
perm_lms_scope_read	2026-07-11 04:26:16.380825+00	2026-07-15 12:13:38.531539+00	lms.scope.read	lms	Read LMS scope	Read LMS scope	scope	read	lms	tenant,school	f	ACTIVE	\N
\.


--
-- Data for Name: profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.profiles (id, created_at, updated_at, user_id, full_name, bio, phone, date_of_birth, gender, address, city, district, social_links_json, avatar_url, is_profile_completed, teaching_philosophy, specialties_json, rating, internal_note) FROM stdin;
prof_super_admin	2026-07-11 03:03:57.34837+00	2026-07-11 03:03:57.34837+00	usr_super_admin	ERG Super Admin	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	Seeded by Flyway enterprise admin account migration
prof_stu_cl_0001	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	usr_stu_cl_0001	Tran Binh	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	Seeded by relationship integrity cleanup
prof_stu_ba_0001	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	usr_stu_ba_0001	Nguyen An	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	Seeded by relationship integrity cleanup
prof_teacher_cl	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	usr_teacher_cl	Cat Lai Teacher	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	Seeded by relationship integrity cleanup
prof_teacher_ba	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	usr_teacher_ba	Binh An Teacher	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	Seeded by relationship integrity cleanup
prof_usr_teacher_tram	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	usr_teacher_tram	Dương Ngọc Trâm	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	\N
prof_usr_teacher_truc	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	usr_teacher_truc	Ngô Xuân Trúc	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	\N
prof_usr_teacher_tuan	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	usr_teacher_tuan	Trần Tướng Tuấn	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	\N
prof_usr_teacher_vang	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	usr_teacher_vang	K Vảng	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	\N
prof_usr_teacher_vuong	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	usr_teacher_vuong	Trần Quốc Vương	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	\N	\N	\N
\.


--
-- Data for Name: question_bank_categories; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.question_bank_categories (id, created_at, updated_at, tenant_id, subject_id, level_id, label) FROM stdin;
\.


--
-- Data for Name: question_bank_levels; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.question_bank_levels (id, created_at, updated_at, tenant_id, subject_id, label, description, order_index) FROM stdin;
\.


--
-- Data for Name: question_bank_subjects; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.question_bank_subjects (id, created_at, updated_at, tenant_id, label, description, company_scope_label) FROM stdin;
\.


--
-- Data for Name: question_types; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.question_types (id, created_at, updated_at, label, authoring_type, runtime_kind, grading_policy, is_auto_gradable, sort_order, status) FROM stdin;
single_choice	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Single choice	multiple-choice	single_choice	auto	t	10	ACTIVE
multiple_choice	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Multiple choice	multiple-choice	single_choice	auto	t	11	ACTIVE
multiple_response	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Multiple response	multiple-response	multiple_response	auto	t	20	ACTIVE
true_false	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	True / false	true-false	true_false	auto	t	30	ACTIVE
short_answer	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Short answer	short-answer	short_answer	auto	t	40	ACTIVE
numeric	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Numeric	numeric	numeric	auto	t	50	ACTIVE
sequence	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Sequence	sequence	sequence	auto	t	60	ACTIVE
matching	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Matching	matching	matching	auto	t	70	ACTIVE
fill_blank	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Fill in the blanks	fill-in-the-blanks	fill_blank	auto	t	80	ACTIVE
select_from_lists	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Select from lists	select-from-lists	select_from_lists	auto	t	90	ACTIVE
drag_words	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Drag the words	drag-the-words	drag_words	auto	t	100	ACTIVE
hotspot	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Hotspot	hotspot	hotspot	auto	t	110	ACTIVE
drag_drop	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Drag and drop	drag-and-drop	drag_drop	auto	t	120	ACTIVE
likert_scale	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Likert scale	likert-scale	likert_scale	survey	f	130	ACTIVE
essay	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	Essay	essay	essay	manual	f	140	ACTIVE
\.


--
-- Data for Name: quiz_bank_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_bank_items (id, created_at, updated_at, tenant_id, title, kind, status, subject_id, level_id, question_ids, source_mode, duration_label, owner_user_id) FROM stdin;
\.


--
-- Data for Name: quiz_drafts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_drafts (id, created_at, updated_at, quiz_id, tenant_id, settings, player_template, tree, schema_version, doc_version, base_version_id, updated_by) FROM stdin;
019f4f6d-882c-7bc2-b8af-0423c7a25700	2026-07-11 04:26:46.18848+00	2026-07-15 13:48:36.081571+00	quiz-sample-editor-question-types	erg	{"info": {"title": "Đây là bài kiểm tra mẫu", "subtitle": "IC3 GS6 - Level 1", "introduction": "Bộ câu hỏi mẫu từ ngân hàng câu hỏi.", "showIntroductionPage": true}, "result": {"failMessage": "Hãy xem lại các câu cần cải thiện.", "passMessage": "Hoàn thành tốt bài luyện tập."}, "settings": {"mode": "training", "shuffleChoices": false, "shuffleQuestions": false, "revealFeedbackPerStep": true}}	{"layout": "classic", "showPanel": true, "showToolbar": true}	[{"id": "sample-editor-group", "rule": "all", "title": "GS6 LV3 OTTHBS (Luyện tập)", "slides": [{"id": "qb-sample-q01-single-choice", "kind": "multiple-choice", "title": "Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?", "slideId": "qb-sample-q01-single-choice", "questionId": "qb-sample-q01-single-choice"}, {"id": "qb-sample-q02-multiple-response", "kind": "multiple-response", "title": "Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn.", "slideId": "qb-sample-q02-multiple-response", "questionId": "qb-sample-q02-multiple-response"}, {"id": "qb-sample-q03-true-false", "kind": "true-false", "title": "Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn.", "slideId": "qb-sample-q03-true-false", "questionId": "qb-sample-q03-true-false"}, {"id": "qb-sample-q04-short-answer", "kind": "short-answer", "title": "Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?", "slideId": "qb-sample-q04-short-answer", "questionId": "qb-sample-q04-short-answer"}, {"id": "qb-sample-q05-numeric", "kind": "numeric", "title": "Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?", "slideId": "qb-sample-q05-numeric", "questionId": "qb-sample-q05-numeric"}, {"id": "qb-sample-q06-sequence", "kind": "sequence", "title": "Sắp xếp quy trình học tập online theo thứ tự hợp lý.", "slideId": "qb-sample-q06-sequence", "questionId": "qb-sample-q06-sequence"}, {"id": "qb-sample-q07-matching", "kind": "matching", "title": "Ghép các bước trong chu trình quản lý dự án với mô tả của nó.", "slideId": "qb-sample-q07-matching", "questionId": "qb-sample-q07-matching"}, {"id": "qb-sample-q08-matching-image", "kind": "matching", "title": "Ghép tên từng đầu kết nối với đúng hình minh họa.", "slideId": "qb-sample-q08-matching-image", "questionId": "qb-sample-q08-matching-image"}, {"id": "qb-sample-q09-fill-blank", "kind": "fill-blank", "title": "Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản.", "slideId": "qb-sample-q09-fill-blank", "questionId": "qb-sample-q09-fill-blank"}, {"id": "qb-sample-q10-select-from-lists", "kind": "select-from-lists", "title": "Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần.", "slideId": "qb-sample-q10-select-from-lists", "questionId": "qb-sample-q10-select-from-lists"}, {"id": "qb-sample-q11-drag-words", "kind": "drag-words", "title": "Bấm từ trong ngân hàng để đưa vào đúng chỗ trống.", "slideId": "qb-sample-q11-drag-words", "questionId": "qb-sample-q11-drag-words"}, {"id": "qb-sample-q12-hotspot", "kind": "hotspot", "title": "Bấm vào vùng thông tin phiên bản trong màn hình minh họa.", "slideId": "qb-sample-q12-hotspot", "questionId": "qb-sample-q12-hotspot"}, {"id": "qb-sample-q13-drag-drop", "kind": "drag-and-drop", "title": "Phân loại các món vào nhóm phù hợp.", "slideId": "qb-sample-q13-drag-drop", "questionId": "qb-sample-q13-drag-drop"}, {"id": "qb-sample-q14-likert", "kind": "likert-scale", "title": "Đánh giá mức độ đồng ý của em sau bài học.", "slideId": "qb-sample-q14-likert", "questionId": "qb-sample-q14-likert"}, {"id": "qb-sample-q15-essay", "kind": "essay", "title": "Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.", "slideId": "qb-sample-q15-essay", "questionId": "qb-sample-q15-essay"}], "randomCount": 0}]	1	33	\N	seed.question.bank
019f5010-85cf-78a4-99e4-1470fb3b00b2	2026-07-11 07:24:47.951521+00	2026-07-11 07:24:47.951521+00	erg-training-demo	erg	{"info": {"title": "ERG on tap ky nang so (Training)", "author": "ERG Teacher Hub", "subtitle": "Che do luyen tap tung cau", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "runtime": {"mode": "training", "passPercent": 80, "shuffleChoices": false, "shuffleQuestions": false, "timeLimitMinutes": 30, "revealFeedbackPerStep": true}}	{"layout": "classic", "showPanel": true, "playerSize": "standard", "accentColor": "#d12b2b", "showToolbar": true, "backgroundTone": "soft-blue", "highlightColor": "#ffb35c", "roundedCorners": true}	[{"rule": "all", "title": "Demo tat ca loai cau hoi", "slides": [{"type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang.", "correct": true}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh.", "correct": false}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K.", "correct": false}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3.", "correct": false}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau.", "correct": true}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon.", "correct": true}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop.", "correct": true}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh.", "correct": false}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "choices": [{"id": "q3-true", "label": "Dung", "correct": true}, {"id": "q3-false", "label": "Sai", "correct": false}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}], "bankQuestionId": "qb-sample-q04-short-answer", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, {"type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang", "tolerance": 0, "correctValue": 400}, "bankQuestionId": "qb-sample-q05-numeric"}, {"type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh", "correctAnswers": ["mat khau"]}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the.", "correctAnswers": ["hai lop", "2 lop"]}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang.", "correctOptionId": "yes"}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu.", "correctOptionId": "no"}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo.", "correctOptionId": "yes"}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra", "correctWordId": "word-source"}, {"id": "slot-update", "label": "va luon", "correctWordId": "word-update"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh", "correctTargetId": "target-healthy"}, {"id": "item-apple", "label": "Tao", "correctTargetId": "target-healthy"}, {"id": "item-cookie", "label": "Banh quy", "correctTargetId": "target-snack"}, {"id": "item-chips", "label": "Khoai tay chien", "correctTargetId": "target-snack"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}], "groupId": "group-question-types", "randomCount": 0}]	1	1	\N	seed.quiz.demo
019f5010-9697-7180-ad82-54a36196b1ad	2026-07-11 07:24:52.247478+00	2026-07-11 07:24:52.247478+00	erg-testing-demo	erg	{"info": {"title": "ERG kiem tra ky nang so (Testing)", "author": "ERG Teacher Hub", "subtitle": "Che do kiem tra day du", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "runtime": {"mode": "testing", "passPercent": 80, "shuffleChoices": true, "shuffleQuestions": true, "timeLimitMinutes": 20, "revealFeedbackPerStep": false}}	{"layout": "classic", "showPanel": true, "playerSize": "standard", "accentColor": "#d12b2b", "showToolbar": true, "backgroundTone": "soft-blue", "highlightColor": "#ffb35c", "roundedCorners": true}	[{"rule": "all", "title": "Demo tat ca loai cau hoi", "slides": [{"type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang.", "correct": true}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh.", "correct": false}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K.", "correct": false}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3.", "correct": false}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau.", "correct": true}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon.", "correct": true}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop.", "correct": true}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh.", "correct": false}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "choices": [{"id": "q3-true", "label": "Dung", "correct": true}, {"id": "q3-false", "label": "Sai", "correct": false}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}], "bankQuestionId": "qb-sample-q04-short-answer", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, {"type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang", "tolerance": 0, "correctValue": 400}, "bankQuestionId": "qb-sample-q05-numeric"}, {"type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh", "correctAnswers": ["mat khau"]}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the.", "correctAnswers": ["hai lop", "2 lop"]}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang.", "correctOptionId": "yes"}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu.", "correctOptionId": "no"}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo.", "correctOptionId": "yes"}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra", "correctWordId": "word-source"}, {"id": "slot-update", "label": "va luon", "correctWordId": "word-update"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh", "correctTargetId": "target-healthy"}, {"id": "item-apple", "label": "Tao", "correctTargetId": "target-healthy"}, {"id": "item-cookie", "label": "Banh quy", "correctTargetId": "target-snack"}, {"id": "item-chips", "label": "Khoai tay chien", "correctTargetId": "target-snack"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}], "groupId": "group-question-types", "randomCount": 0}]	1	1	\N	seed.quiz.demo
\.


--
-- Data for Name: quiz_packages; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_packages (id, created_at, updated_at, quiz_id, quiz_version_id, quiz_version, tenant_id, content_hash, signature, public_key_id, grading_mode, schema_version, package_format, assets_manifest, package_json, generated_at, expires_at) FROM stdin;
pkg_erg-training-demo_v1-training-demo_sha256:0	2026-07-11 07:24:49.774704+00	2026-07-11 07:24:49.774704+00	erg-training-demo	019f5010-8c5e-7046-9ef8-e4f8331a17fd	v1-training-demo	erg	sha256:09a9204c6ebb711f25d954a6353d2b6ba17795b543ef0824f1cdd650f5ce432a	+FAyO9tDxaCpWdRs3S5kCWRDrdWyDDhr7A722jp9qQc=	erg-dev-key-2026-01	client-first	1	single	{"assets": []}	{"quiz": {"info": {"title": "ERG on tap ky nang so (Training)", "author": "ERG Teacher Hub", "subtitle": "Che do luyen tap tung cau", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "sections": [{"id": "019f5010-89a6-7bbf-b90a-0af3f818b253", "title": "Demo tat ca loai cau hoi", "questions": [{"id": "qb-sample-q01-single-choice", "kind": "single_choice", "type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "points": 71.43, "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang."}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh."}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K."}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3."}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"id": "qb-sample-q02-multiple-response", "kind": "multiple_response", "type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "points": 71.43, "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau."}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon."}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop."}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh."}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"id": "qb-sample-q03-true-false", "kind": "true_false", "type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "points": 71.43, "choices": [{"id": "q3-true", "label": "Dung"}, {"id": "q3-false", "label": "Sai"}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"id": "qb-sample-q04-short-answer", "kind": "short_answer", "type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "points": 71.43, "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF"}], "bankQuestionId": "qb-sample-q04-short-answer"}, {"id": "qb-sample-q05-numeric", "kind": "numeric", "type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "points": 71.43, "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang"}, "bankQuestionId": "qb-sample-q05-numeric"}, {"id": "qb-sample-q06-sequence", "kind": "sequence", "type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "points": 71.43, "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"id": "qb-sample-q07-matching", "kind": "matching", "type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "points": 71.43, "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"id": "qb-sample-q09-fill-blank", "kind": "fill_blank", "type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "points": 71.43, "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh"}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the."}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"id": "qb-sample-q10-select-from-lists", "kind": "select_from_lists", "type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "points": 71.43, "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang."}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu."}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo."}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"id": "qb-sample-q11-drag-words", "kind": "drag_words", "type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "points": 71.43, "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra"}, {"id": "slot-update", "label": "va luon"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"id": "qb-sample-q12-hotspot", "kind": "hotspot", "type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "points": 71.43, "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"id": "qb-sample-q13-drag-drop", "kind": "drag_drop", "type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "points": 71.43, "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh"}, {"id": "item-apple", "label": "Tao"}, {"id": "item-cookie", "label": "Banh quy"}, {"id": "item-chips", "label": "Khoai tay chien"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"id": "qb-sample-q14-likert", "kind": "likert_scale", "type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "points": 71.42, "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"id": "qb-sample-q15-essay", "kind": "essay", "type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "points": 71.42, "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}]}], "settings": {"mode": "training", "passPercent": 80, "shuffleChoices": false, "shuffleQuestions": false, "timeLimitMinutes": 30, "revealFeedbackPerStep": true}, "schemaVersion": 1}, "quizId": "erg-training-demo", "version": "v1-training-demo", "answerKey": {"qb-sample-q05-numeric": {"value": 400, "tolerance": 0}, "qb-sample-q12-hotspot": {"regions": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}]}, "qb-sample-q06-sequence": {"order": ["q6-step-1", "q6-step-2", "q6-step-3", "q6-step-4"]}, "qb-sample-q07-matching": {"pairs": [{"promptId": "q7-pair-1", "responseId": "q7-pair-1"}, {"promptId": "q7-pair-2", "responseId": "q7-pair-2"}, {"promptId": "q7-pair-3", "responseId": "q7-pair-3"}, {"promptId": "q7-pair-4", "responseId": "q7-pair-4"}]}, "qb-sample-q13-drag-drop": {"targets": [{"itemId": "item-broccoli", "targetId": "target-healthy"}, {"itemId": "item-apple", "targetId": "target-healthy"}, {"itemId": "item-cookie", "targetId": "target-snack"}, {"itemId": "item-chips", "targetId": "target-snack"}]}, "qb-sample-q03-true-false": {"correctChoiceId": "q3-true"}, "qb-sample-q09-fill-blank": {"blanks": [{"id": "q8-blank-1", "answers": ["mat khau"]}, {"id": "q8-blank-2", "answers": ["hai lop", "2 lop"]}]}, "qb-sample-q11-drag-words": {"blanks": [{"id": "slot-source", "answers": ["word-source"]}, {"id": "slot-update", "answers": ["word-update"]}]}, "qb-sample-q04-short-answer": {"blanks": [{"id": "q4-answer", "answers": ["PDF", "file PDF", "tep PDF"]}], "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, "qb-sample-q01-single-choice": {"correctChoiceId": "q1-a"}, "qb-sample-q02-multiple-response": {"acceptPartial": true, "correctChoiceIds": ["q2-a", "q2-b", "q2-c"]}, "qb-sample-q10-select-from-lists": {"blanks": [{"id": "q9-blank-1", "answers": ["yes"]}, {"id": "q9-blank-2", "answers": ["no"]}, {"id": "q9-blank-3", "answers": ["yes"]}]}}, "gradingMode": "client-first", "schemaVersion": 1}	2026-07-11 07:24:49.656453+00	\N
pkg_erg-testing-demo_v1-testing-demo_sha256:5	2026-07-11 07:24:54.118708+00	2026-07-11 07:24:54.118708+00	erg-testing-demo	019f5010-9d45-7a3f-ae2a-5e9ecf565950	v1-testing-demo	erg	sha256:5ed58eb02b0da4ed6471be4863583b651653989f65b5109998c4d3ee15349605	5SDzUbpR8iJcnEVYarulbuzyK3GtWZM+YD56vSTbKPY=	erg-dev-key-2026-01	server-authoritative	1	single	{"assets": []}	{"quiz": {"info": {"title": "ERG kiem tra ky nang so (Testing)", "author": "ERG Teacher Hub", "subtitle": "Che do kiem tra day du", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "sections": [{"id": "019f5010-9b5a-70f2-8560-6847a2c52586", "title": "Demo tat ca loai cau hoi", "questions": [{"id": "qb-sample-q01-single-choice", "kind": "single_choice", "type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "points": 71.43, "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang."}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh."}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K."}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3."}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"id": "qb-sample-q02-multiple-response", "kind": "multiple_response", "type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "points": 71.43, "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau."}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon."}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop."}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh."}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"id": "qb-sample-q03-true-false", "kind": "true_false", "type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "points": 71.43, "choices": [{"id": "q3-true", "label": "Dung"}, {"id": "q3-false", "label": "Sai"}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"id": "qb-sample-q04-short-answer", "kind": "short_answer", "type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "points": 71.43, "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF"}], "bankQuestionId": "qb-sample-q04-short-answer"}, {"id": "qb-sample-q05-numeric", "kind": "numeric", "type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "points": 71.43, "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang"}, "bankQuestionId": "qb-sample-q05-numeric"}, {"id": "qb-sample-q06-sequence", "kind": "sequence", "type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "points": 71.43, "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"id": "qb-sample-q07-matching", "kind": "matching", "type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "points": 71.43, "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"id": "qb-sample-q09-fill-blank", "kind": "fill_blank", "type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "points": 71.43, "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh"}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the."}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"id": "qb-sample-q10-select-from-lists", "kind": "select_from_lists", "type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "points": 71.43, "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang."}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu."}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo."}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"id": "qb-sample-q11-drag-words", "kind": "drag_words", "type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "points": 71.43, "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra"}, {"id": "slot-update", "label": "va luon"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"id": "qb-sample-q12-hotspot", "kind": "hotspot", "type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "points": 71.43, "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"id": "qb-sample-q13-drag-drop", "kind": "drag_drop", "type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "points": 71.43, "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh"}, {"id": "item-apple", "label": "Tao"}, {"id": "item-cookie", "label": "Banh quy"}, {"id": "item-chips", "label": "Khoai tay chien"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"id": "qb-sample-q14-likert", "kind": "likert_scale", "type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "points": 71.42, "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"id": "qb-sample-q15-essay", "kind": "essay", "type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "points": 71.42, "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}]}], "settings": {"mode": "testing", "passPercent": 80, "shuffleChoices": true, "shuffleQuestions": true, "timeLimitMinutes": 20, "revealFeedbackPerStep": false}, "schemaVersion": 1}, "quizId": "erg-testing-demo", "version": "v1-testing-demo", "gradingMode": "server-authoritative", "schemaVersion": 1}	2026-07-11 07:24:53.987678+00	\N
\.


--
-- Data for Name: quiz_version_questions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_version_questions (id, created_at, updated_at, quiz_version_id, section_id, tenant_id, runtime_question_id, source_slide_id, bank_question_id, kind, authoring_type, points, is_auto_gradable, grading_policy, order_index, body, answer_key) FROM stdin;
019f5010-8c69-7d0c-90b8-4d56321f3870	2026-07-11 07:24:49.641934+00	2026-07-11 07:24:49.641934+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q01-single-choice	q1-single-choice	qb-sample-q01-single-choice	single_choice	multiple-choice	71.43	t	auto	0	{"type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang."}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh."}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K."}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3."}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}	{"correctChoiceId": "q1-a"}
019f5010-8c6b-7e6d-b016-985823f35cf7	2026-07-11 07:24:49.643935+00	2026-07-11 07:24:49.643935+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q02-multiple-response	q2-multiple-response	qb-sample-q02-multiple-response	multiple_response	multiple-response	71.43	t	auto	1	{"type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau."}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon."}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop."}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh."}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}	{"acceptPartial": true, "correctChoiceIds": ["q2-a", "q2-b", "q2-c"]}
019f5010-8c6c-78f4-94b9-605c8c932c14	2026-07-11 07:24:49.644931+00	2026-07-11 07:24:49.644931+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q03-true-false	q3-true-false	qb-sample-q03-true-false	true_false	true-false	71.43	t	auto	2	{"type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "choices": [{"id": "q3-true", "label": "Dung"}, {"id": "q3-false", "label": "Sai"}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}	{"correctChoiceId": "q3-true"}
019f5010-8c6d-7909-8a19-228c8db87512	2026-07-11 07:24:49.644931+00	2026-07-11 07:24:49.644931+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q04-short-answer	q4-short-answer	qb-sample-q04-short-answer	short_answer	short-answer	71.43	t	auto	3	{"type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF"}], "bankQuestionId": "qb-sample-q04-short-answer"}	{"blanks": [{"id": "q4-answer", "answers": ["PDF", "file PDF", "tep PDF"]}], "correctAnswers": ["PDF", "file PDF", "tep PDF"]}
019f5010-8c6d-7147-996a-a4bdf0395345	2026-07-11 07:24:49.645933+00	2026-07-11 07:24:49.645933+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q05-numeric	q5-numeric	qb-sample-q05-numeric	numeric	numeric	71.43	t	auto	4	{"type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang"}, "bankQuestionId": "qb-sample-q05-numeric"}	{"value": 400, "tolerance": 0}
019f5010-8c6e-796d-b543-06c97fd974a3	2026-07-11 07:24:49.646929+00	2026-07-11 07:24:49.646929+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q06-sequence	q6-sequence	qb-sample-q06-sequence	sequence	sequence	71.43	t	auto	5	{"type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}	{"order": ["q6-step-1", "q6-step-2", "q6-step-3", "q6-step-4"]}
019f5010-8c6e-7388-8c90-ffa6d0719096	2026-07-11 07:24:49.646929+00	2026-07-11 07:24:49.646929+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q07-matching	q7-matching	qb-sample-q07-matching	matching	matching	71.43	t	auto	6	{"type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}	{"pairs": [{"promptId": "q7-pair-1", "responseId": "q7-pair-1"}, {"promptId": "q7-pair-2", "responseId": "q7-pair-2"}, {"promptId": "q7-pair-3", "responseId": "q7-pair-3"}, {"promptId": "q7-pair-4", "responseId": "q7-pair-4"}]}
019f5010-8c6f-78bc-a601-222afd278fad	2026-07-11 07:24:49.647937+00	2026-07-11 07:24:49.647937+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q09-fill-blank	q8-fill-blank	qb-sample-q09-fill-blank	fill_blank	fill-in-the-blanks	71.43	t	auto	7	{"type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh"}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the."}], "bankQuestionId": "qb-sample-q09-fill-blank"}	{"blanks": [{"id": "q8-blank-1", "answers": ["mat khau"]}, {"id": "q8-blank-2", "answers": ["hai lop", "2 lop"]}]}
019f5010-8c70-7bef-a0c8-0a65082b5305	2026-07-11 07:24:49.648926+00	2026-07-11 07:24:49.648926+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q10-select-from-lists	q9-select-from-lists	qb-sample-q10-select-from-lists	select_from_lists	select-from-lists	71.43	t	auto	8	{"type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang."}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu."}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo."}], "bankQuestionId": "qb-sample-q10-select-from-lists"}	{"blanks": [{"id": "q9-blank-1", "answers": ["yes"]}, {"id": "q9-blank-2", "answers": ["no"]}, {"id": "q9-blank-3", "answers": ["yes"]}]}
019f5010-8c70-722a-b07f-8bb06935acfe	2026-07-11 07:24:49.648926+00	2026-07-11 07:24:49.648926+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q11-drag-words	q10-drag-words	qb-sample-q11-drag-words	drag_words	drag-the-words	71.43	t	auto	9	{"type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra"}, {"id": "slot-update", "label": "va luon"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}	{"blanks": [{"id": "slot-source", "answers": ["word-source"]}, {"id": "slot-update", "answers": ["word-update"]}]}
019f5010-8c71-7cdf-b441-71abf18244b6	2026-07-11 07:24:49.64993+00	2026-07-11 07:24:49.64993+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q12-hotspot	q11-hotspot	qb-sample-q12-hotspot	hotspot	hotspot	71.43	t	auto	10	{"type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}	{"regions": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}]}
019f5010-8c72-7872-8a56-746f954a4c97	2026-07-11 07:24:49.650931+00	2026-07-11 07:24:49.650931+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q13-drag-drop	q12-drag-drop	qb-sample-q13-drag-drop	drag_drop	drag-and-drop	71.43	t	auto	11	{"type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh"}, {"id": "item-apple", "label": "Tao"}, {"id": "item-cookie", "label": "Banh quy"}, {"id": "item-chips", "label": "Khoai tay chien"}], "bankQuestionId": "qb-sample-q13-drag-drop"}	{"targets": [{"itemId": "item-broccoli", "targetId": "target-healthy"}, {"itemId": "item-apple", "targetId": "target-healthy"}, {"itemId": "item-cookie", "targetId": "target-snack"}, {"itemId": "item-chips", "targetId": "target-snack"}]}
019f5010-8c72-7a71-8bf1-f08d8a9aa8a5	2026-07-11 07:24:49.650931+00	2026-07-11 07:24:49.650931+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q14-likert	q13-likert	qb-sample-q14-likert	likert_scale	likert-scale	71.42	f	survey	12	{"type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}	\N
019f5010-8c73-7929-9bb4-f6c386a17247	2026-07-11 07:24:49.651923+00	2026-07-11 07:24:49.651923+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	019f5010-8c68-79e6-b5d1-e733b2d721d2	erg	qb-sample-q15-essay	q14-essay	qb-sample-q15-essay	essay	essay	71.42	f	manual	13	{"type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}	\N
019f5010-9d4a-72e8-a245-711f662a7b97	2026-07-11 07:24:53.962176+00	2026-07-11 07:24:53.962176+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q01-single-choice	q1-single-choice	qb-sample-q01-single-choice	single_choice	multiple-choice	71.43	t	auto	0	{"type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang."}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh."}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K."}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3."}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}	{"correctChoiceId": "q1-a"}
019f5010-9d4b-7863-8ab8-16f1cfe8058f	2026-07-11 07:24:53.963179+00	2026-07-11 07:24:53.963179+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q02-multiple-response	q2-multiple-response	qb-sample-q02-multiple-response	multiple_response	multiple-response	71.43	t	auto	1	{"type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau."}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon."}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop."}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh."}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}	{"acceptPartial": true, "correctChoiceIds": ["q2-a", "q2-b", "q2-c"]}
019f5010-9d56-7745-ab63-ec057c7f1753	2026-07-11 07:24:53.973081+00	2026-07-11 07:24:53.973081+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q03-true-false	q3-true-false	qb-sample-q03-true-false	true_false	true-false	71.43	t	auto	2	{"type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "choices": [{"id": "q3-true", "label": "Dung"}, {"id": "q3-false", "label": "Sai"}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}	{"correctChoiceId": "q3-true"}
019f5010-9d59-7284-8eee-680b7de20e4d	2026-07-11 07:24:53.977081+00	2026-07-11 07:24:53.977081+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q04-short-answer	q4-short-answer	qb-sample-q04-short-answer	short_answer	short-answer	71.43	t	auto	3	{"type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF"}], "bankQuestionId": "qb-sample-q04-short-answer"}	{"blanks": [{"id": "q4-answer", "answers": ["PDF", "file PDF", "tep PDF"]}], "correctAnswers": ["PDF", "file PDF", "tep PDF"]}
019f5010-9d5a-75e5-b3ca-2624055ceb61	2026-07-11 07:24:53.978086+00	2026-07-11 07:24:53.978086+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q05-numeric	q5-numeric	qb-sample-q05-numeric	numeric	numeric	71.43	t	auto	4	{"type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang"}, "bankQuestionId": "qb-sample-q05-numeric"}	{"value": 400, "tolerance": 0}
019f5010-9d5b-7db3-8e76-1bb7ebfa2b5a	2026-07-11 07:24:53.979592+00	2026-07-11 07:24:53.979592+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q06-sequence	q6-sequence	qb-sample-q06-sequence	sequence	sequence	71.43	t	auto	5	{"type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}	{"order": ["q6-step-1", "q6-step-2", "q6-step-3", "q6-step-4"]}
019f5010-9d5b-7d0a-8243-a5927cf70f50	2026-07-11 07:24:53.979592+00	2026-07-11 07:24:53.979592+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q07-matching	q7-matching	qb-sample-q07-matching	matching	matching	71.43	t	auto	6	{"type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}	{"pairs": [{"promptId": "q7-pair-1", "responseId": "q7-pair-1"}, {"promptId": "q7-pair-2", "responseId": "q7-pair-2"}, {"promptId": "q7-pair-3", "responseId": "q7-pair-3"}, {"promptId": "q7-pair-4", "responseId": "q7-pair-4"}]}
019f5010-9d5c-7489-91f8-7b4952542b98	2026-07-11 07:24:53.980602+00	2026-07-11 07:24:53.980602+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q09-fill-blank	q8-fill-blank	qb-sample-q09-fill-blank	fill_blank	fill-in-the-blanks	71.43	t	auto	7	{"type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh"}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the."}], "bankQuestionId": "qb-sample-q09-fill-blank"}	{"blanks": [{"id": "q8-blank-1", "answers": ["mat khau"]}, {"id": "q8-blank-2", "answers": ["hai lop", "2 lop"]}]}
019f5010-9d5d-7960-8c45-33d872edaff7	2026-07-11 07:24:53.981603+00	2026-07-11 07:24:53.981603+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q10-select-from-lists	q9-select-from-lists	qb-sample-q10-select-from-lists	select_from_lists	select-from-lists	71.43	t	auto	8	{"type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang."}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu."}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo."}], "bankQuestionId": "qb-sample-q10-select-from-lists"}	{"blanks": [{"id": "q9-blank-1", "answers": ["yes"]}, {"id": "q9-blank-2", "answers": ["no"]}, {"id": "q9-blank-3", "answers": ["yes"]}]}
019f5010-9d5e-717b-a066-e81efa46fa3e	2026-07-11 07:24:53.98261+00	2026-07-11 07:24:53.98261+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q11-drag-words	q10-drag-words	qb-sample-q11-drag-words	drag_words	drag-the-words	71.43	t	auto	9	{"type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra"}, {"id": "slot-update", "label": "va luon"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}	{"blanks": [{"id": "slot-source", "answers": ["word-source"]}, {"id": "slot-update", "answers": ["word-update"]}]}
019f5010-9d5f-7b15-a867-bcd9467bb6af	2026-07-11 07:24:53.983615+00	2026-07-11 07:24:53.983615+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q12-hotspot	q11-hotspot	qb-sample-q12-hotspot	hotspot	hotspot	71.43	t	auto	10	{"type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}	{"regions": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}]}
019f5010-9d5f-70d9-b95a-52916fa2c849	2026-07-11 07:24:53.983615+00	2026-07-11 07:24:53.983615+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q13-drag-drop	q12-drag-drop	qb-sample-q13-drag-drop	drag_drop	drag-and-drop	71.43	t	auto	11	{"type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh"}, {"id": "item-apple", "label": "Tao"}, {"id": "item-cookie", "label": "Banh quy"}, {"id": "item-chips", "label": "Khoai tay chien"}], "bankQuestionId": "qb-sample-q13-drag-drop"}	{"targets": [{"itemId": "item-broccoli", "targetId": "target-healthy"}, {"itemId": "item-apple", "targetId": "target-healthy"}, {"itemId": "item-cookie", "targetId": "target-snack"}, {"itemId": "item-chips", "targetId": "target-snack"}]}
019f5010-9d61-71f8-89b0-246cfc80c084	2026-07-11 07:24:53.985131+00	2026-07-11 07:24:53.985131+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q14-likert	q13-likert	qb-sample-q14-likert	likert_scale	likert-scale	71.42	f	survey	12	{"type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}	\N
019f5010-9d61-771f-bb81-68997c1adb9b	2026-07-11 07:24:53.985131+00	2026-07-11 07:24:53.985131+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	019f5010-9d49-7b46-8636-a07f6456a4dc	erg	qb-sample-q15-essay	q14-essay	qb-sample-q15-essay	essay	essay	71.42	f	manual	13	{"type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}	\N
\.


--
-- Data for Name: quiz_version_sections; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_version_sections (id, created_at, updated_at, quiz_version_id, tenant_id, title, order_index) FROM stdin;
019f5010-8c68-79e6-b5d1-e733b2d721d2	2026-07-11 07:24:49.64093+00	2026-07-11 07:24:49.64093+00	019f5010-8c5e-7046-9ef8-e4f8331a17fd	erg	Demo tat ca loai cau hoi	0
019f5010-9d49-7b46-8636-a07f6456a4dc	2026-07-11 07:24:53.96116+00	2026-07-11 07:24:53.96116+00	019f5010-9d45-7a3f-ae2a-5e9ecf565950	erg	Demo tat ca loai cau hoi	0
\.


--
-- Data for Name: quiz_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_versions (id, created_at, updated_at, quiz_id, tenant_id, version_label, version_number, authoring_snapshot, runtime_quiz, answer_key, grading_mode, schema_version, content_hash, published_by, published_at, is_active, idempotency_key) FROM stdin;
019f5010-8c5e-7046-9ef8-e4f8331a17fd	2026-07-11 07:24:49.630209+00	2026-07-11 07:24:54.121712+00	erg-training-demo	erg	v1-training-demo	1	{"tree": [{"rule": "all", "title": "Demo tat ca loai cau hoi", "slides": [{"type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang.", "correct": true}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh.", "correct": false}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K.", "correct": false}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3.", "correct": false}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau.", "correct": true}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon.", "correct": true}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop.", "correct": true}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh.", "correct": false}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "choices": [{"id": "q3-true", "label": "Dung", "correct": true}, {"id": "q3-false", "label": "Sai", "correct": false}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}], "bankQuestionId": "qb-sample-q04-short-answer", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, {"type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang", "tolerance": 0, "correctValue": 400}, "bankQuestionId": "qb-sample-q05-numeric"}, {"type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh", "correctAnswers": ["mat khau"]}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the.", "correctAnswers": ["hai lop", "2 lop"]}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang.", "correctOptionId": "yes"}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu.", "correctOptionId": "no"}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo.", "correctOptionId": "yes"}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra", "correctWordId": "word-source"}, {"id": "slot-update", "label": "va luon", "correctWordId": "word-update"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh", "correctTargetId": "target-healthy"}, {"id": "item-apple", "label": "Tao", "correctTargetId": "target-healthy"}, {"id": "item-cookie", "label": "Banh quy", "correctTargetId": "target-snack"}, {"id": "item-chips", "label": "Khoai tay chien", "correctTargetId": "target-snack"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}], "groupId": "group-question-types", "randomCount": 0}], "settings": {"info": {"title": "ERG on tap ky nang so (Training)", "author": "ERG Teacher Hub", "subtitle": "Che do luyen tap tung cau", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "runtime": {"mode": "training", "passPercent": 80, "shuffleChoices": false, "shuffleQuestions": false, "timeLimitMinutes": 30, "revealFeedbackPerStep": true}}, "schemaVersion": 1, "playerTemplate": {"layout": "classic", "showPanel": true, "playerSize": "standard", "accentColor": "#d12b2b", "showToolbar": true, "backgroundTone": "soft-blue", "highlightColor": "#ffb35c", "roundedCorners": true}, "curriculumSnapshot": {"level": {"id": "ic3-l3", "label": "Level 3", "sourceTable": "lms_levels"}, "topic": {"id": "ic3-l3-topic-1", "label": "Bao mat tai khoan", "sourceTable": "lms_topics"}, "bridge": "lms", "subject": {"id": "ic3-gs6", "label": "IC3 GS6", "sourceTable": "lms_subjects"}, "canonicalPending": true}}	{"info": {"title": "ERG on tap ky nang so (Training)", "author": "ERG Teacher Hub", "subtitle": "Che do luyen tap tung cau", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "sections": [{"id": "019f5010-89a6-7bbf-b90a-0af3f818b253", "title": "Demo tat ca loai cau hoi", "questions": [{"id": "qb-sample-q01-single-choice", "kind": "single_choice", "type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "points": 71.43, "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang."}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh."}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K."}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3."}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"id": "qb-sample-q02-multiple-response", "kind": "multiple_response", "type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "points": 71.43, "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau."}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon."}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop."}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh."}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"id": "qb-sample-q03-true-false", "kind": "true_false", "type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "points": 71.43, "choices": [{"id": "q3-true", "label": "Dung"}, {"id": "q3-false", "label": "Sai"}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"id": "qb-sample-q04-short-answer", "kind": "short_answer", "type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "points": 71.43, "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF"}], "bankQuestionId": "qb-sample-q04-short-answer"}, {"id": "qb-sample-q05-numeric", "kind": "numeric", "type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "points": 71.43, "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang"}, "bankQuestionId": "qb-sample-q05-numeric"}, {"id": "qb-sample-q06-sequence", "kind": "sequence", "type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "points": 71.43, "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"id": "qb-sample-q07-matching", "kind": "matching", "type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "points": 71.43, "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"id": "qb-sample-q09-fill-blank", "kind": "fill_blank", "type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "points": 71.43, "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh"}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the."}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"id": "qb-sample-q10-select-from-lists", "kind": "select_from_lists", "type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "points": 71.43, "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang."}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu."}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo."}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"id": "qb-sample-q11-drag-words", "kind": "drag_words", "type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "points": 71.43, "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra"}, {"id": "slot-update", "label": "va luon"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"id": "qb-sample-q12-hotspot", "kind": "hotspot", "type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "points": 71.43, "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"id": "qb-sample-q13-drag-drop", "kind": "drag_drop", "type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "points": 71.43, "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh"}, {"id": "item-apple", "label": "Tao"}, {"id": "item-cookie", "label": "Banh quy"}, {"id": "item-chips", "label": "Khoai tay chien"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"id": "qb-sample-q14-likert", "kind": "likert_scale", "type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "points": 71.42, "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"id": "qb-sample-q15-essay", "kind": "essay", "type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "points": 71.42, "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}]}], "settings": {"mode": "training", "passPercent": 80, "shuffleChoices": false, "shuffleQuestions": false, "timeLimitMinutes": 30, "revealFeedbackPerStep": true}, "schemaVersion": 1}	{"qb-sample-q05-numeric": {"value": 400, "tolerance": 0}, "qb-sample-q12-hotspot": {"regions": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}]}, "qb-sample-q06-sequence": {"order": ["q6-step-1", "q6-step-2", "q6-step-3", "q6-step-4"]}, "qb-sample-q07-matching": {"pairs": [{"promptId": "q7-pair-1", "responseId": "q7-pair-1"}, {"promptId": "q7-pair-2", "responseId": "q7-pair-2"}, {"promptId": "q7-pair-3", "responseId": "q7-pair-3"}, {"promptId": "q7-pair-4", "responseId": "q7-pair-4"}]}, "qb-sample-q13-drag-drop": {"targets": [{"itemId": "item-broccoli", "targetId": "target-healthy"}, {"itemId": "item-apple", "targetId": "target-healthy"}, {"itemId": "item-cookie", "targetId": "target-snack"}, {"itemId": "item-chips", "targetId": "target-snack"}]}, "qb-sample-q03-true-false": {"correctChoiceId": "q3-true"}, "qb-sample-q09-fill-blank": {"blanks": [{"id": "q8-blank-1", "answers": ["mat khau"]}, {"id": "q8-blank-2", "answers": ["hai lop", "2 lop"]}]}, "qb-sample-q11-drag-words": {"blanks": [{"id": "slot-source", "answers": ["word-source"]}, {"id": "slot-update", "answers": ["word-update"]}]}, "qb-sample-q04-short-answer": {"blanks": [{"id": "q4-answer", "answers": ["PDF", "file PDF", "tep PDF"]}], "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, "qb-sample-q01-single-choice": {"correctChoiceId": "q1-a"}, "qb-sample-q02-multiple-response": {"acceptPartial": true, "correctChoiceIds": ["q2-a", "q2-b", "q2-c"]}, "qb-sample-q10-select-from-lists": {"blanks": [{"id": "q9-blank-1", "answers": ["yes"]}, {"id": "q9-blank-2", "answers": ["no"]}, {"id": "q9-blank-3", "answers": ["yes"]}]}}	client-first	1	sha256:09a9204c6ebb711f25d954a6353d2b6ba17795b543ef0824f1cdd650f5ce432a	seed.quiz.demo	2026-07-11 07:24:49.630209+00	t	seed-publish:erg-training-demo:v1-training-demo
019f5010-9d45-7a3f-ae2a-5e9ecf565950	2026-07-11 07:24:53.957643+00	2026-07-11 07:24:54.125707+00	erg-testing-demo	erg	v1-testing-demo	1	{"tree": [{"rule": "all", "title": "Demo tat ca loai cau hoi", "slides": [{"type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang.", "correct": true}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh.", "correct": false}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K.", "correct": false}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3.", "correct": false}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau.", "correct": true}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon.", "correct": true}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop.", "correct": true}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh.", "correct": false}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "choices": [{"id": "q3-true", "label": "Dung", "correct": true}, {"id": "q3-false", "label": "Sai", "correct": false}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}], "bankQuestionId": "qb-sample-q04-short-answer", "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, {"type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang", "tolerance": 0, "correctValue": 400}, "bankQuestionId": "qb-sample-q05-numeric"}, {"type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh", "correctAnswers": ["mat khau"]}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the.", "correctAnswers": ["hai lop", "2 lop"]}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang.", "correctOptionId": "yes"}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu.", "correctOptionId": "no"}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo.", "correctOptionId": "yes"}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra", "correctWordId": "word-source"}, {"id": "slot-update", "label": "va luon", "correctWordId": "word-update"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh", "correctTargetId": "target-healthy"}, {"id": "item-apple", "label": "Tao", "correctTargetId": "target-healthy"}, {"id": "item-cookie", "label": "Banh quy", "correctTargetId": "target-snack"}, {"id": "item-chips", "label": "Khoai tay chien", "correctTargetId": "target-snack"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}], "groupId": "group-question-types", "randomCount": 0}], "settings": {"info": {"title": "ERG kiem tra ky nang so (Testing)", "author": "ERG Teacher Hub", "subtitle": "Che do kiem tra day du", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "runtime": {"mode": "testing", "passPercent": 80, "shuffleChoices": true, "shuffleQuestions": true, "timeLimitMinutes": 20, "revealFeedbackPerStep": false}}, "schemaVersion": 1, "playerTemplate": {"layout": "classic", "showPanel": true, "playerSize": "standard", "accentColor": "#d12b2b", "showToolbar": true, "backgroundTone": "soft-blue", "highlightColor": "#ffb35c", "roundedCorners": true}, "curriculumSnapshot": {"level": {"id": "ic3-l3", "label": "Level 3", "sourceTable": "lms_levels"}, "topic": {"id": "ic3-l3-topic-1", "label": "Bao mat tai khoan", "sourceTable": "lms_topics"}, "bridge": "lms", "subject": {"id": "ic3-gs6", "label": "IC3 GS6", "sourceTable": "lms_subjects"}, "canonicalPending": true}}	{"info": {"title": "ERG kiem tra ky nang so (Testing)", "author": "ERG Teacher Hub", "subtitle": "Che do kiem tra day du", "introduction": "Demo du lieu that cho FE tich hop quiz runtime."}, "theme": {"accentEnd": "#e82828", "headerText": "#ffffff", "optionText": "#172033", "accentStart": "#000088", "canvasBorder": "#000088", "pageBackground": "#f8fafc", "inputBackground": "#ffffff", "headerBackground": "#000088", "playerBackground": "#ffffff", "sidebarActiveText": "#000088", "sidebarActiveBackground": "#eef2ff", "optionSelectedBackground": "#eef2ff"}, "result": {"failMessage": "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.", "passMessage": "Chuc mung, em da hoan thanh tot bai demo.", "confirmNoLabel": "KHONG", "submitAllLabel": "NOP BAI", "confirmYesLabel": "CO", "submitAllPrompt": "Em da tra loi het cau hoi. Em muon nop bai ngay khong?", "thankYouMessage": "Cam on em da hoan thanh bai lam.", "showReviewButton": true, "returnToQuizLabel": "QUAY LAI", "reviewButtonLabel": "XEM LAI BAI", "confirmSubmitPrompt": "Em chac chan muon nop bai va ket thuc luot lam nay chu?"}, "sections": [{"id": "019f5010-9b5a-70f2-8560-6847a2c52586", "title": "Demo tat ca loai cau hoi", "questions": [{"id": "qb-sample-q01-single-choice", "kind": "single_choice", "type": "multiple-choice", "title": "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?", "points": 71.43, "choices": [{"id": "q1-a", "label": "Tap tin van ban thuan dai 400 trang."}, {"id": "q1-b", "label": "Hinh GIF dong 1080p voi 30 khung hinh."}, {"id": "q1-c", "label": "Video dai 1 phut dinh dang AVI 8K."}, {"id": "q1-d", "label": "Podcast dai 5 phut dinh dang MP3."}], "slideId": "q1-single-choice", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q01-single-choice", "bankQuestionId": "qb-sample-q01-single-choice"}, {"id": "qb-sample-q02-multiple-response", "kind": "multiple_response", "type": "multiple-response", "title": "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.", "points": 71.43, "choices": [{"id": "q2-a", "label": "Dat ten file theo cu phap giao vien yeu cau."}, {"id": "q2-b", "label": "Nen anh hoac video neu dung luong qua lon."}, {"id": "q2-c", "label": "Kiem tra lai file truoc khi nop."}, {"id": "q2-d", "label": "Tai mot file bat ky de hoan thanh cho nhanh."}], "slideId": "q2-multiple-response", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q02-multiple-response", "acceptPartial": true, "bankQuestionId": "qb-sample-q02-multiple-response"}, {"id": "qb-sample-q03-true-false", "kind": "true_false", "type": "true-false", "title": "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.", "points": 71.43, "choices": [{"id": "q3-true", "label": "Dung"}, {"id": "q3-false", "label": "Sai"}], "slideId": "q3-true-false", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q03-true-false", "bankQuestionId": "qb-sample-q03-true-false"}, {"id": "qb-sample-q04-short-answer", "kind": "short_answer", "type": "short-answer", "title": "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?", "points": 71.43, "slideId": "q4-short-answer", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q04-short-answer", "textBlanks": [{"id": "q4-answer", "label": "Cau tra loi ngan", "placeholder": "Vi du: PDF"}], "bankQuestionId": "qb-sample-q04-short-answer"}, {"id": "qb-sample-q05-numeric", "kind": "numeric", "type": "numeric", "title": "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?", "points": 71.43, "slideId": "q5-numeric", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q05-numeric", "numericAnswer": {"unit": "trang"}, "bankQuestionId": "qb-sample-q05-numeric"}, {"id": "qb-sample-q06-sequence", "kind": "sequence", "type": "sequence", "title": "Sap xep quy trinh hoc tap online theo thu tu hop ly.", "points": 71.43, "slideId": "q6-sequence", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q06-sequence", "sequenceItems": [{"id": "q6-step-1", "label": "Doc yeu cau bai hoc"}, {"id": "q6-step-2", "label": "Xem tai lieu hoac video huong dan"}, {"id": "q6-step-3", "label": "Lam bai luyen tap"}, {"id": "q6-step-4", "label": "Xem phan hoi va sua loi"}], "bankQuestionId": "qb-sample-q06-sequence"}, {"id": "qb-sample-q07-matching", "kind": "matching", "type": "matching", "title": "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.", "points": 71.43, "slideId": "q7-matching", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "matching": [{"id": "q7-pair-1", "prompt": "Giam sat", "response": "Theo doi tien do va rui ro du an."}, {"id": "q7-pair-2", "prompt": "Khoi tao", "response": "Quyet dinh co theo duoi du an hay khong."}, {"id": "q7-pair-3", "prompt": "Thuc hien", "response": "Tao ra san pham du an."}, {"id": "q7-pair-4", "prompt": "Dong", "response": "Ban giao va danh gia du an."}], "questionId": "qb-sample-q07-matching", "bankQuestionId": "qb-sample-q07-matching"}, {"id": "qb-sample-q09-fill-blank", "kind": "fill_blank", "type": "fill-in-the-blanks", "title": "Dien tu con thieu de hoan thanh cau bao mat tai khoan.", "points": 71.43, "slideId": "q8-fill-blank", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q09-fill-blank", "textBlanks": [{"id": "q8-blank-1", "label": "mat khau", "prefix": "Em nen dung", "suffix": "manh"}, {"id": "q8-blank-2", "label": "hai lop", "prefix": "va bat xac thuc", "suffix": "khi co the."}], "bankQuestionId": "qb-sample-q09-fill-blank"}, {"id": "qb-sample-q10-select-from-lists", "kind": "select_from_lists", "type": "select-from-lists", "title": "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.", "points": 71.43, "slideId": "q9-select-from-lists", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q10-select-from-lists", "inlineBlanks": [{"id": "q9-blank-1", "options": ["yes", "no"], "statement": "Ban sao bai kiem tra bi dang len mang."}, {"id": "q9-blank-2", "options": ["yes", "no"], "statement": "Anh chup vui khong chua thong tin rieng tu."}, {"id": "q9-blank-3", "options": ["yes", "no"], "statement": "De doa bao luc trong cau lac bo."}], "bankQuestionId": "qb-sample-q10-select-from-lists"}, {"id": "qb-sample-q11-drag-words", "kind": "drag_words", "type": "drag-the-words", "title": "Bam tu trong ngan hang de dua vao dung cho trong.", "points": 71.43, "slideId": "q10-drag-words", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "wordBank": [{"id": "word-source", "label": "nguon"}, {"id": "word-update", "label": "cap nhat"}, {"id": "word-safe", "label": "an toan"}], "wordSlots": [{"id": "slot-source", "label": "Khi tai tai lieu, hay kiem tra"}, {"id": "slot-update", "label": "va luon"}], "questionId": "qb-sample-q11-drag-words", "bankQuestionId": "qb-sample-q11-drag-words"}, {"id": "qb-sample-q12-hotspot", "kind": "hotspot", "type": "hotspot", "title": "Bam vao vung thong tin phien ban trong man hinh minh hoa.", "points": 71.43, "slideId": "q11-hotspot", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q12-hotspot", "hotspotAreas": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22}], "hotspotImage": {"url": "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width": 1200, "height": 675}, "bankQuestionId": "qb-sample-q12-hotspot"}, {"id": "qb-sample-q13-drag-drop", "kind": "drag_drop", "type": "drag-and-drop", "title": "Phan loai cac mon vao nhom phu hop.", "points": 71.43, "slideId": "q12-drag-drop", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q13-drag-drop", "dropTargets": [{"id": "target-healthy", "label": "Thuc pham tot"}, {"id": "target-snack", "label": "Do an vat"}], "dragDropItems": [{"id": "item-broccoli", "label": "Bong cai xanh"}, {"id": "item-apple", "label": "Tao"}, {"id": "item-cookie", "label": "Banh quy"}, {"id": "item-chips", "label": "Khoai tay chien"}], "bankQuestionId": "qb-sample-q13-drag-drop"}, {"id": "qb-sample-q14-likert", "kind": "likert_scale", "type": "likert-scale", "title": "Danh gia muc do dong y cua em sau bai hoc.", "points": 71.42, "slideId": "q13-likert", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "likertRows": [{"id": "likert-understand", "label": "Em hieu noi dung bai hoc"}, {"id": "likert-confident", "label": "Em tu tin lam bai tap tuong tu"}, {"id": "likert-practice", "label": "Em muon luyen them dang nay"}], "questionId": "qb-sample-q14-likert", "likertScale": [{"id": "scale-1", "label": "Rat thap", "value": 1}, {"id": "scale-2", "label": "Thap", "value": 2}, {"id": "scale-3", "label": "Vua", "value": 3}, {"id": "scale-4", "label": "Tot", "value": 4}, {"id": "scale-5", "label": "Rat tot", "value": 5}], "bankQuestionId": "qb-sample-q14-likert"}, {"id": "qb-sample-q15-essay", "kind": "essay", "type": "essay", "title": "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.", "points": 71.42, "slideId": "q14-essay", "feedback": {"correct": "Chinh xac.", "partial": "Dung mot phan.", "incorrect": "Chua chinh xac."}, "questionId": "qb-sample-q15-essay", "essayRubric": [{"id": "rubric-example", "label": "Co vi du ro rang", "points": 4}, {"id": "rubric-explain", "label": "Giai thich de hieu", "points": 4}, {"id": "rubric-clean", "label": "Trinh bay sach", "points": 2}], "bankQuestionId": "qb-sample-q15-essay"}]}], "settings": {"mode": "testing", "passPercent": 80, "shuffleChoices": true, "shuffleQuestions": true, "timeLimitMinutes": 20, "revealFeedbackPerStep": false}, "schemaVersion": 1}	{"qb-sample-q05-numeric": {"value": 400, "tolerance": 0}, "qb-sample-q12-hotspot": {"regions": [{"x": 0.52, "y": 0.42, "id": "q11-area-version", "shape": "rect", "width": 0.26, "height": 0.22, "correct": true}]}, "qb-sample-q06-sequence": {"order": ["q6-step-1", "q6-step-2", "q6-step-3", "q6-step-4"]}, "qb-sample-q07-matching": {"pairs": [{"promptId": "q7-pair-1", "responseId": "q7-pair-1"}, {"promptId": "q7-pair-2", "responseId": "q7-pair-2"}, {"promptId": "q7-pair-3", "responseId": "q7-pair-3"}, {"promptId": "q7-pair-4", "responseId": "q7-pair-4"}]}, "qb-sample-q13-drag-drop": {"targets": [{"itemId": "item-broccoli", "targetId": "target-healthy"}, {"itemId": "item-apple", "targetId": "target-healthy"}, {"itemId": "item-cookie", "targetId": "target-snack"}, {"itemId": "item-chips", "targetId": "target-snack"}]}, "qb-sample-q03-true-false": {"correctChoiceId": "q3-true"}, "qb-sample-q09-fill-blank": {"blanks": [{"id": "q8-blank-1", "answers": ["mat khau"]}, {"id": "q8-blank-2", "answers": ["hai lop", "2 lop"]}]}, "qb-sample-q11-drag-words": {"blanks": [{"id": "slot-source", "answers": ["word-source"]}, {"id": "slot-update", "answers": ["word-update"]}]}, "qb-sample-q04-short-answer": {"blanks": [{"id": "q4-answer", "answers": ["PDF", "file PDF", "tep PDF"]}], "correctAnswers": ["PDF", "file PDF", "tep PDF"]}, "qb-sample-q01-single-choice": {"correctChoiceId": "q1-a"}, "qb-sample-q02-multiple-response": {"acceptPartial": true, "correctChoiceIds": ["q2-a", "q2-b", "q2-c"]}, "qb-sample-q10-select-from-lists": {"blanks": [{"id": "q9-blank-1", "answers": ["yes"]}, {"id": "q9-blank-2", "answers": ["no"]}, {"id": "q9-blank-3", "answers": ["yes"]}]}}	server-authoritative	1	sha256:5ed58eb02b0da4ed6471be4863583b651653989f65b5109998c4d3ee15349605	seed.quiz.demo	2026-07-11 07:24:53.957643+00	t	seed-publish:erg-testing-demo:v1-testing-demo
\.


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.role_permissions (role_id, permission_id) FROM stdin;
role_lms_teacher_standard	p_lms_notification_prefs_update
role_lms_admin	perm_lms_read
role_lms_admin	perm_lms_manage
role_lms_teacher	perm_lms_class_read
role_lms_teacher	perm_lms_unit_read
role_lms_student	perm_lms_assign_self
role_lms_student	perm_lms_submit
role_seo_staff	p_crm_school_create
role_seo_staff	p_crm_school_read
role_seo_staff	p_crm_deal_manage
role_seo_staff	p_crm_contact_manage
role_seo_staff	p_crm_activity_manage
role_seo_lead	p_crm_school_read
role_seo_lead	p_crm_deal_manage
role_seo_lead	p_crm_contact_manage
role_seo_lead	p_crm_activity_manage
role_seo_lead	p_crm_analytics_read
role_seo_manager	p_crm_school_read
role_seo_manager	p_crm_deal_manage
role_seo_manager	p_crm_contact_manage
role_seo_manager	p_crm_activity_manage
role_seo_manager	p_crm_analytics_read
role_center_manager	perm_lms_manage
role_center_manager	p_org_school_activate
role_school_manager	perm_lms_manage
role_executive_director	p_crm_analytics_read
role_executive_director	perm_lms_read
role_lms_admin	perm_lms_schedule_manage
role_lms_admin	perm_lms_schedule_read
role_lms_teacher	perm_lms_schedule_read_self
role_lms_student	perm_lms_schedule_read_self
role_lms_teacher_standard	p_lms_notification_read
role_lms_teacher_standard	p_lms_report_read
role_lms_teacher_standard	p_lms_resource_share
role_lms_teacher_standard	p_lms_resource_read
role_lms_teacher_standard	p_lms_class_log_read
role_lms_teacher_standard	perm_lms_schedule_read
role_lms_teacher_standard	p_lms_attendance_read
role_lms_teacher_standard	p_lms_grade_read
role_lms_teacher_standard	p_lms_student_read
role_lms_teacher_standard	perm_lms_class_read
role_lms_teacher_standard	p_lms_exercise_update
role_lms_teacher_standard	p_lms_exercise_create
role_lms_teacher_standard	p_lms_exercise_read
role_lms_teacher_standard	p_lms_assignment_progress_read
role_lms_teacher_standard	p_lms_assignment_assign
role_lms_teacher_standard	p_lms_assignment_update
role_lms_teacher_standard	p_lms_assignment_create
role_lms_teacher_standard	p_lms_assignment_read
role_lms_teacher_standard	p_lms_dashboard_read
role_lms_teacher_standard	p_acct_sessions_read
role_lms_teacher_standard	p_acct_password_change
role_lms_teacher_standard	p_acct_recovery_email_update
role_lms_teacher_standard	p_acct_self_update
role_lms_teacher_standard	p_acct_self_read
role_lms_homeroom_teacher	p_lms_notification_prefs_update
role_lms_homeroom_teacher	p_lms_notification_read
role_lms_homeroom_teacher	p_lms_report_read
role_lms_homeroom_teacher	p_lms_resource_share
role_lms_homeroom_teacher	p_lms_resource_read
role_lms_homeroom_teacher	p_lms_class_log_read
role_lms_homeroom_teacher	perm_lms_schedule_read
role_lms_homeroom_teacher	p_lms_attendance_read
role_lms_homeroom_teacher	p_lms_grade_read
role_lms_homeroom_teacher	p_lms_student_group_delete
role_lms_homeroom_teacher	p_lms_student_group_update
role_lms_homeroom_teacher	p_lms_student_group_create
role_lms_homeroom_teacher	p_lms_student_group_read
role_lms_homeroom_teacher	p_lms_student_read
role_lms_homeroom_teacher	perm_lms_class_manage
role_lms_homeroom_teacher	perm_lms_class_read
role_lms_homeroom_teacher	p_lms_exercise_update
role_lms_homeroom_teacher	p_lms_exercise_create
role_lms_homeroom_teacher	p_lms_exercise_read
role_lms_homeroom_teacher	p_lms_assignment_progress_read
role_lms_homeroom_teacher	p_lms_assignment_assign
role_lms_homeroom_teacher	p_lms_assignment_update
role_lms_admin	perm_lms_class_manage
role_center_manager	perm_lms_class_manage
role_school_manager	perm_lms_class_manage
role_lms_admin	perm_lms_school_archive
role_center_manager	perm_lms_school_archive
role_school_manager	perm_lms_school_archive
role_lms_admin	perm_lms_school_create
role_center_manager	perm_lms_school_create
role_school_manager	perm_lms_school_create
role_lms_admin	perm_lms_school_read
role_center_manager	perm_lms_school_read
role_school_manager	perm_lms_school_read
role_lms_admin	perm_lms_school_update
role_center_manager	perm_lms_school_update
role_school_manager	perm_lms_school_update
role_lms_homeroom_teacher	p_lms_assignment_create
role_lms_homeroom_teacher	p_lms_assignment_read
role_lms_homeroom_teacher	p_lms_dashboard_read
role_lms_homeroom_teacher	p_acct_sessions_read
role_lms_homeroom_teacher	p_acct_password_change
role_lms_homeroom_teacher	p_acct_recovery_email_update
role_lms_homeroom_teacher	p_acct_self_update
role_lms_homeroom_teacher	p_acct_self_read
role_lms_subject_teacher	p_lms_notification_prefs_update
role_lms_subject_teacher	p_lms_notification_read
role_lms_subject_teacher	p_lms_report_read
role_lms_subject_teacher	p_lms_resource_read
role_lms_subject_teacher	p_lms_class_log_read
role_lms_subject_teacher	perm_lms_schedule_read
role_lms_subject_teacher	p_lms_attendance_read
role_lms_subject_teacher	p_lms_grade_read
role_lms_subject_teacher	perm_lms_class_read
role_lms_subject_teacher	p_lms_exercise_update
role_lms_subject_teacher	p_lms_exercise_create
role_lms_subject_teacher	p_lms_exercise_read
role_lms_subject_teacher	p_lms_assignment_progress_read
role_lms_subject_teacher	p_lms_assignment_assign
role_lms_subject_teacher	p_lms_assignment_update
role_lms_subject_teacher	p_lms_assignment_create
role_lms_subject_teacher	p_lms_assignment_read
role_lms_subject_teacher	p_lms_dashboard_read
role_lms_subject_teacher	p_acct_sessions_read
role_lms_subject_teacher	p_acct_password_change
role_lms_subject_teacher	p_acct_recovery_email_update
role_lms_subject_teacher	p_acct_self_update
role_lms_subject_teacher	p_acct_self_read
role_lms_teacher_assistant	p_lms_notification_read
role_lms_teacher_assistant	p_lms_report_read
role_lms_teacher_assistant	p_lms_resource_read
role_lms_teacher_assistant	p_lms_class_log_read
role_lms_teacher_assistant	perm_lms_schedule_read
role_lms_teacher_assistant	p_lms_attendance_read
role_lms_teacher_assistant	p_lms_grade_read
role_lms_teacher_assistant	perm_lms_class_read
role_lms_teacher_assistant	p_lms_exercise_read
role_lms_teacher_assistant	p_lms_assignment_progress_read
role_lms_teacher_assistant	p_lms_assignment_read
role_lms_teacher_assistant	p_lms_dashboard_read
role_lms_teacher_assistant	p_acct_sessions_read
role_lms_teacher_assistant	p_acct_password_change
role_lms_teacher_assistant	p_acct_recovery_email_update
role_lms_teacher_assistant	p_acct_self_update
role_lms_teacher_assistant	p_acct_self_read
role_lms_teacher_readonly	p_lms_notification_read
role_lms_teacher_readonly	p_lms_report_read
role_lms_teacher_readonly	p_lms_resource_read
role_lms_teacher_readonly	p_lms_class_log_read
role_lms_teacher_readonly	perm_lms_schedule_read
role_lms_teacher_readonly	p_lms_attendance_read
role_lms_teacher_readonly	p_lms_grade_read
role_lms_teacher_readonly	perm_lms_class_read
role_lms_teacher_readonly	p_lms_assignment_progress_read
role_lms_teacher_readonly	p_lms_assignment_read
role_lms_teacher_readonly	p_lms_dashboard_read
role_lms_teacher_readonly	p_acct_sessions_read
role_lms_teacher_readonly	p_acct_password_change
role_lms_teacher_readonly	p_acct_self_read
role_lms_teacher_lead	p_lms_notification_prefs_update
role_lms_teacher_lead	p_lms_notification_read
role_lms_teacher_lead	p_lms_report_read
role_lms_teacher_lead	p_lms_resource_share
role_lms_teacher_lead	p_lms_resource_update
role_lms_teacher_lead	p_lms_resource_create
role_lms_teacher_lead	p_lms_resource_read
role_lms_teacher_lead	p_lms_class_log_read
role_lms_teacher_lead	p_lms_schedule_import
role_lms_teacher_lead	p_lms_schedule_publish
role_lms_teacher_lead	p_lms_schedule_update
role_lms_teacher_lead	p_lms_schedule_create
role_lms_teacher_lead	perm_lms_schedule_read
role_lms_teacher_lead	p_lms_attendance_read
role_lms_teacher_lead	p_lms_grade_read
role_lms_teacher_lead	p_lms_student_group_update
role_lms_teacher_lead	p_lms_student_group_create
role_lms_teacher_lead	p_lms_student_group_read
role_lms_teacher_lead	p_lms_student_read
role_lms_teacher_lead	perm_lms_class_read
role_lms_teacher_lead	p_lms_exercise_update
role_lms_teacher_lead	p_lms_exercise_create
role_lms_teacher_lead	p_lms_exercise_read
role_lms_teacher_lead	p_lms_assignment_progress_read
role_lms_teacher_lead	p_lms_assignment_assign
role_lms_teacher_lead	p_lms_assignment_update
role_lms_teacher_lead	p_lms_assignment_create
role_lms_teacher_lead	p_lms_assignment_read
role_lms_teacher_lead	p_lms_dashboard_read
role_lms_teacher_lead	p_acct_sessions_read
role_lms_teacher_lead	p_acct_password_change
role_lms_teacher_lead	p_acct_recovery_email_update
role_lms_teacher_lead	p_acct_self_update
role_lms_teacher_lead	p_acct_self_read
role_lms_teacher_readonly	p_lms_student_read
role_lms_teacher_assistant	p_lms_student_group_read
role_lms_subject_teacher	p_lms_resource_share
role_lms_teacher_standard	p_lms_schedule_update
role_lms_teacher_standard	p_lms_resource_create
role_lms_teacher_standard	p_lms_student_group_update
role_lms_teacher_standard	p_lms_resource_update
role_lms_teacher_assistant	p_lms_student_read
role_lms_teacher_standard	p_lms_schedule_create
role_lms_teacher_standard	p_lms_student_group_create
role_lms_teacher_standard	p_lms_student_group_read
role_lms_teacher_assistant	p_lms_assignment_update
role_lms_subject_teacher	p_lms_student_group_read
role_lms_subject_teacher	p_lms_student_group_create
role_lms_subject_teacher	p_lms_schedule_create
role_lms_homeroom_teacher	p_lms_schedule_update
role_lms_subject_teacher	p_lms_resource_update
role_lms_subject_teacher	p_lms_student_group_update
role_lms_subject_teacher	p_lms_resource_create
role_lms_subject_teacher	p_lms_schedule_update
role_lms_teacher_readonly	p_lms_exercise_read
role_lms_homeroom_teacher	p_lms_resource_create
role_lms_homeroom_teacher	p_lms_resource_update
role_lms_homeroom_teacher	p_lms_schedule_create
role_lms_subject_teacher	p_lms_student_read
role_lms_teacher_readonly	p_lms_portal_access
role_lms_teacher_standard	p_lms_portal_access
role_lms_teacher_assistant	p_lms_portal_access
role_lms_teacher_lead	p_lms_portal_access
role_lms_homeroom_teacher	p_lms_portal_access
role_lms_subject_teacher	p_lms_portal_access
role_super_admin	perm_lms_scope_read
role_super_admin	perm_profile_self
role_super_admin	perm_lms_unit_read
role_super_admin	perm_all
role_super_admin	perm_lms_class_read
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (id, created_at, updated_at, name, description, is_default, updated_by) FROM stdin;
role_lms_admin	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.admin	LMS administrator	f	\N
role_lms_teacher	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.instructor	LMS teacher	f	\N
role_lms_student	2026-07-11 03:03:41.741114+00	2026-07-11 03:03:41.741114+00	lms.learner	LMS learner	t	\N
role_seo_staff	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	seo.staff	SEO staff who prospects and manages own school pipeline	f	\N
role_seo_lead	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	seo.lead	SEO lead who supports a team pipeline	f	\N
role_seo_manager	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	seo.manager	SEO manager with CRM analytics and pipeline oversight	f	\N
role_school_manager	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	school.manager	School manager with school-level operation access	f	\N
role_executive_director	2026-07-11 03:03:53.436363+00	2026-07-11 03:03:53.436363+00	executive.director	Executive director with organization and analytics visibility	f	\N
role_super_admin	2026-07-11 03:03:41.741114+00	2026-07-11 18:37:21.860187+00	system.super_admin	System super admin for ERG bootstrap data	f	\N
role_lms_teacher_standard	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms_teacher_standard	Teacher operating assigned classes: homework, grades, attendance, schedule, class log, resources, reports	f	\N
role_lms_homeroom_teacher	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms_homeroom_teacher	Standard plus student/group management and class reports within homeroom scope	f	\N
role_lms_subject_teacher	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms_subject_teacher	Assigned subject/classes only; no student profile management beyond scope	f	\N
role_lms_teacher_assistant	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms_teacher_assistant	Read classes; assist homework/attendance with limited rights; no finalize/sensitive export	f	\N
role_lms_teacher_readonly	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms_teacher_readonly	Read-only dashboard, classes, homework, grades, attendance, schedule, reports	f	\N
role_lms_teacher_lead	2026-07-15 01:30:29.952351+00	2026-07-15 01:30:29.952351+00	lms_teacher_lead	Coordinates teaching within school/center scope; manages schedule/resources/reports; no IAM admin	f	\N
role_center_manager	2026-07-11 03:03:53.436363+00	2026-07-15 12:13:46.022527+00	center_manager	Education center manager role	f	\N
role_it_operator	2026-07-15 12:13:46.022527+00	2026-07-15 12:13:46.022527+00	it_operator	IT operations role	f	\N
role_pdt_operator	2026-07-15 12:13:46.022527+00	2026-07-15 12:13:46.022527+00	pdt_operator	Academic operations role	f	\N
role_system_admin	2026-07-15 12:13:46.022527+00	2026-07-15 12:13:46.022527+00	system.admin	System administrator role	f	\N
role_god_mode	2026-07-15 12:13:46.022527+00	2026-07-15 12:13:46.022527+00	system.god_mode	Reserved break-glass role; disabled for ordinary assignment	f	\N
\.


--
-- Data for Name: school_academic_years; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.school_academic_years (id, tenant_id, school_id, start_year, end_year, status, starts_on, ends_on, version, created_by, updated_by, created_at, updated_at) FROM stdin;
019f5a8b-b605-780f-b71d-f8ceb21c3876	erg	019f5a8b-b528-7507-b347-52e990580f45	2026	2027	CURRENT	\N	\N	0	usr_super_admin	usr_super_admin	2026-07-13 08:15:33.468417+00	2026-07-13 08:15:33.468417+00
019f5a8b-baf9-7513-a331-7511e3d40af3	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	2026	2027	CURRENT	\N	\N	0	usr_super_admin	usr_super_admin	2026-07-13 08:15:34.735556+00	2026-07-13 08:15:34.735556+00
019f5a8b-bf7b-7e74-bef3-6445cee13769	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	2026	2027	CURRENT	\N	\N	0	usr_super_admin	usr_super_admin	2026-07-13 08:15:35.897405+00	2026-07-13 08:15:35.897405+00
019f5a8b-c391-713e-9673-0caf23abebbb	erg	019f5a8b-c2e5-785c-8cac-0a74b2b724de	2026	2027	CURRENT	\N	\N	0	usr_super_admin	usr_super_admin	2026-07-13 08:15:36.936351+00	2026-07-13 08:15:36.936351+00
019f5a8b-c696-72f2-9c13-1a41e3548da7	erg	019f5a8b-c5ea-730e-937c-293628da4628	2026	2027	CURRENT	\N	\N	0	usr_super_admin	usr_super_admin	2026-07-13 08:15:37.708218+00	2026-07-13 08:15:37.708218+00
\.


--
-- Data for Name: school_grades; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.school_grades (id, tenant_id, school_id, academic_year_id, grade_code, label, sort_order, status, source, created_at, updated_at) FROM stdin;
019f5a8b-b720-773b-b9ae-c3e5f25b28ad	erg	019f5a8b-b528-7507-b347-52e990580f45	019f5a8b-b605-780f-b71d-f8ceb21c3876	1	Khối 1	1	ACTIVE	DEFAULT	2026-07-13 08:15:33.664776+00	2026-07-13 08:15:33.664776+00
019f5a8b-b721-7c3f-b37f-7d3f3101b7ee	erg	019f5a8b-b528-7507-b347-52e990580f45	019f5a8b-b605-780f-b71d-f8ceb21c3876	2	Khối 2	2	ACTIVE	DEFAULT	2026-07-13 08:15:33.665311+00	2026-07-13 08:15:33.665311+00
019f5a8b-b722-7ffc-ac48-1fb80f03494f	erg	019f5a8b-b528-7507-b347-52e990580f45	019f5a8b-b605-780f-b71d-f8ceb21c3876	3	Khối 3	3	ACTIVE	DEFAULT	2026-07-13 08:15:33.666328+00	2026-07-13 08:15:33.666328+00
019f5a8b-b723-7f56-b26e-666c497e0e34	erg	019f5a8b-b528-7507-b347-52e990580f45	019f5a8b-b605-780f-b71d-f8ceb21c3876	4	Khối 4	4	ACTIVE	DEFAULT	2026-07-13 08:15:33.667345+00	2026-07-13 08:15:33.667345+00
019f5a8b-b724-75e8-a08a-feed29fad700	erg	019f5a8b-b528-7507-b347-52e990580f45	019f5a8b-b605-780f-b71d-f8ceb21c3876	5	Khối 5	5	ACTIVE	DEFAULT	2026-07-13 08:15:33.668339+00	2026-07-13 08:15:33.668339+00
019f5a8b-bc21-7a57-a477-84df0a64b9ca	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	019f5a8b-baf9-7513-a331-7511e3d40af3	6	Khối 6	6	ACTIVE	DEFAULT	2026-07-13 08:15:34.945535+00	2026-07-13 08:15:34.945535+00
019f5a8b-bc22-7a83-87d9-551a7a72fddb	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	019f5a8b-baf9-7513-a331-7511e3d40af3	7	Khối 7	7	ACTIVE	DEFAULT	2026-07-13 08:15:34.946546+00	2026-07-13 08:15:34.946546+00
019f5a8b-bc23-7a75-a6e2-d7675c695b25	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	019f5a8b-baf9-7513-a331-7511e3d40af3	8	Khối 8	8	ACTIVE	DEFAULT	2026-07-13 08:15:34.947564+00	2026-07-13 08:15:34.947564+00
019f5a8b-bc23-78b2-a893-bca169a499f5	erg	019f5a8b-ba4d-7351-882a-4ef79911b783	019f5a8b-baf9-7513-a331-7511e3d40af3	9	Khối 9	9	ACTIVE	DEFAULT	2026-07-13 08:15:34.947564+00	2026-07-13 08:15:34.947564+00
019f5a8b-c08a-7143-85cc-f4ea0b99a94d	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	019f5a8b-bf7b-7e74-bef3-6445cee13769	10	Khối 10	10	ACTIVE	DEFAULT	2026-07-13 08:15:36.074834+00	2026-07-13 08:15:36.074834+00
019f5a8b-c08b-7592-9662-4a915cd3545f	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	019f5a8b-bf7b-7e74-bef3-6445cee13769	11	Khối 11	11	ACTIVE	DEFAULT	2026-07-13 08:15:36.075354+00	2026-07-13 08:15:36.075354+00
019f5a8b-c08c-722d-baf6-bc278cc24577	erg	019f5a8b-bece-7119-b8eb-77c56f9708e3	019f5a8b-bf7b-7e74-bef3-6445cee13769	12	Khối 12	12	ACTIVE	DEFAULT	2026-07-13 08:15:36.076364+00	2026-07-13 08:15:36.076364+00
\.


--
-- Data for Name: school_operational_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.school_operational_profiles (school_id, tenant_id, school_type, address, district, principal_name, contact_phone, contact_email, onboarding_status, current_academic_year_id, version, created_by, updated_by, created_at, updated_at) FROM stdin;
019f5a8b-b528-7507-b347-52e990580f45	erg	PRIMARY	Thành phố Hồ Chí Minh	Quận 1	Nguyễn Minh Anh	02873001200	school1@demo.erg.edu.vn	ACTIVE	019f5a8b-b605-780f-b71d-f8ceb21c3876	1	usr_super_admin	usr_super_admin	2026-07-13 08:15:33.562678+00	2026-07-13 09:07:11.281401+00
019f5a8b-ba4d-7351-882a-4ef79911b783	erg	SECONDARY	Thành phố Hồ Chí Minh	Thành phố Thủ Đức	Trần Thu Hà	02873001201	school2@demo.erg.edu.vn	ACTIVE	019f5a8b-baf9-7513-a331-7511e3d40af3	1	usr_super_admin	usr_super_admin	2026-07-13 08:15:34.821881+00	2026-07-13 09:13:38.516206+00
019f5a8b-bece-7119-b8eb-77c56f9708e3	erg	HIGH_SCHOOL	Thành phố Hồ Chí Minh	Quận 3	Lê Mạnh Hùng	02873001202	school3@demo.erg.edu.vn	ACTIVE	019f5a8b-bf7b-7e74-bef3-6445cee13769	1	usr_super_admin	usr_super_admin	2026-07-13 08:15:35.983163+00	2026-07-13 09:15:06.287185+00
019f5a8b-c2e5-785c-8cac-0a74b2b724de	erg	UNIVERSITY	Thành phố Hồ Chí Minh	Thành phố Thủ Đức	Vũ Thanh Mai	02873001203	school4@demo.erg.edu.vn	ACTIVE	019f5a8b-c391-713e-9673-0caf23abebbb	1	usr_super_admin	usr_super_admin	2026-07-13 08:15:37.022394+00	2026-07-13 09:23:49.395896+00
019f5a8b-c5ea-730e-937c-293628da4628	erg	TRAINING_CENTER	Thành phố Hồ Chí Minh	Quận Bình Thạnh	Phạm Quốc Bảo	02873001204	school5@demo.erg.edu.vn	ACTIVE	019f5a8b-c696-72f2-9c13-1a41e3548da7	1	usr_super_admin	usr_super_admin	2026-07-13 08:15:37.794824+00	2026-07-13 10:48:39.790394+00
\.


--
-- Data for Name: security_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.security_events (id, tenant_id, user_id, event_type, severity, portal, device_id, ip_address, reason, occurred_at, created_at, updated_at, platform, result, risk_code, session_id, metadata) FROM stdin;
019f65b8-0b90-7544-ae3b-3e8685a36133	erg	usr_super_admin	SESSION_REPLACED	MEDIUM	lcms	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	0:0:0:0:0:0:0:1	web_desktop session replaced by a new login	2026-07-15 12:19:48.241691+00	2026-07-15 12:19:48.325329+00	2026-07-15 12:19:48.325329+00	WEB_DESKTOP	SUCCESS	SESSION_REPLACED	\N	{"portal": "lcms", "deviceName": "Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"}
019f65b8-8222-7b82-b7bc-3e2cf99a2002	erg	usr_super_admin	SESSION_REPLACED	MEDIUM	lcms	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	0:0:0:0:0:0:0:1	web_desktop session replaced by a new login	2026-07-15 12:20:18.594071+00	2026-07-15 12:20:18.675478+00	2026-07-15 12:20:18.675478+00	WEB_DESKTOP	SUCCESS	SESSION_REPLACED	\N	{"portal": "lcms", "deviceName": "Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"}
019f65d8-f60d-7c3d-b463-f0cb8ee8a105	erg	usr_super_admin	SESSION_REPLACED	MEDIUM	lms	device-7d554b0e-da68-4881-b44f-1e98a828ee51	0:0:0:0:0:0:0:1	web_desktop session replaced by a new login	2026-07-15 12:55:45.421968+00	2026-07-15 12:55:45.501445+00	2026-07-15 12:55:45.501445+00	WEB_DESKTOP	SUCCESS	SESSION_REPLACED	\N	{"portal": "lms", "deviceName": "Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"}
\.


--
-- Data for Name: student_group_members; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.student_group_members (group_id, student_id, tenant_id, created_at) FROM stdin;
\.


--
-- Data for Name: student_groups; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.student_groups (id, created_at, updated_at, tenant_id, name, class_id, owner_user_id, student_count, note, color) FROM stdin;
\.


--
-- Data for Name: student_progress; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.student_progress (id, created_at, updated_at, tenant_id, student_user_id, student_name, quiz_id, quiz_title, assignment_id, class_id, mode, status, current_question_index, current_question_title, answered_count, total_questions, percent_complete, score, passed, started_at, submitted_at, last_active_at) FROM stdin;
\.


--
-- Data for Name: teaching_schedule_batches; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teaching_schedule_batches (id, tenant_id, school_id, academic_year, apply_from, repeat_weeks, status, source, idempotency_key, payload_hash, note, created_by, published_by, published_at, created_at, updated_at) FROM stdin;
batch_ba_demo_001	erg	sch_binh_an	2025-2026	2026-06-15	4	PUBLISHED	LCMS_BULK_GRID	\N	\N	Lịch HK1 - THCS Bình An	usr_super_admin	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001	erg	sch_cat_lai	2025-2026	2026-06-15	4	PUBLISHED	LCMS_BULK_GRID	\N	\N	Lịch HK1 - THCS Cát Lái	usr_super_admin	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001	erg	sch_truong_cong_dinh	2025-2026	2026-06-15	4	PUBLISHED	LCMS_BULK_GRID	\N	\N	Lịch HK1 - THCS Trương Công Định	usr_super_admin	\N	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
\.


--
-- Data for Name: teaching_schedule_conflicts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teaching_schedule_conflicts (id, tenant_id, batch_id, event_id, conflicting_event_id, conflict_type, severity, message, client_row_id, resolved, resolved_by, resolved_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: teaching_schedule_event_teachers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, color_key, created_at, updated_at) FROM stdin;
batch_ba_demo_001_e1_m1	erg	batch_ba_demo_001_e1	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e1_m2	erg	batch_ba_demo_001_e1	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e1_a1	erg	batch_ba_demo_001_e1	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e2_m1	erg	batch_ba_demo_001_e2	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e2_m2	erg	batch_ba_demo_001_e2	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e2_a1	erg	batch_ba_demo_001_e2	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e3_m1	erg	batch_ba_demo_001_e3	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e3_m2	erg	batch_ba_demo_001_e3	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e3_a1	erg	batch_ba_demo_001_e3	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e4_m1	erg	batch_ba_demo_001_e4	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e4_m2	erg	batch_ba_demo_001_e4	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e4_a1	erg	batch_ba_demo_001_e4	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e5_m1	erg	batch_ba_demo_001_e5	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e5_m2	erg	batch_ba_demo_001_e5	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e5_a1	erg	batch_ba_demo_001_e5	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e6_m1	erg	batch_ba_demo_001_e6	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e6_m2	erg	batch_ba_demo_001_e6	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e6_a1	erg	batch_ba_demo_001_e6	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e7_m1	erg	batch_ba_demo_001_e7	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e7_m2	erg	batch_ba_demo_001_e7	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e7_a1	erg	batch_ba_demo_001_e7	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e8_m1	erg	batch_ba_demo_001_e8	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e8_m2	erg	batch_ba_demo_001_e8	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e8_a1	erg	batch_ba_demo_001_e8	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e9_m1	erg	batch_ba_demo_001_e9	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e9_a1	erg	batch_ba_demo_001_e9	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e10_m1	erg	batch_ba_demo_001_e10	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e10_a1	erg	batch_ba_demo_001_e10	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e11_m1	erg	batch_ba_demo_001_e11	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e11_a1	erg	batch_ba_demo_001_e11	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e12_m1	erg	batch_ba_demo_001_e12	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e12_a1	erg	batch_ba_demo_001_e12	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e13_m1	erg	batch_ba_demo_001_e13	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e13_a1	erg	batch_ba_demo_001_e13	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e14_m1	erg	batch_ba_demo_001_e14	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e14_a1	erg	batch_ba_demo_001_e14	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e15_m1	erg	batch_ba_demo_001_e15	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e15_a1	erg	batch_ba_demo_001_e15	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e16_m1	erg	batch_ba_demo_001_e16	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e16_a1	erg	batch_ba_demo_001_e16	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e17_m1	erg	batch_ba_demo_001_e17	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e17_m2	erg	batch_ba_demo_001_e17	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e18_m1	erg	batch_ba_demo_001_e18	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e18_m2	erg	batch_ba_demo_001_e18	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e19_m1	erg	batch_ba_demo_001_e19	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e19_m2	erg	batch_ba_demo_001_e19	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e20_m1	erg	batch_ba_demo_001_e20	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e20_m2	erg	batch_ba_demo_001_e20	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e21_m1	erg	batch_ba_demo_001_e21	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e21_m2	erg	batch_ba_demo_001_e21	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e21_a1	erg	batch_ba_demo_001_e21	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e22_m1	erg	batch_ba_demo_001_e22	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e22_m2	erg	batch_ba_demo_001_e22	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e22_a1	erg	batch_ba_demo_001_e22	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e23_m1	erg	batch_ba_demo_001_e23	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e23_m2	erg	batch_ba_demo_001_e23	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e23_a1	erg	batch_ba_demo_001_e23	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e24_m1	erg	batch_ba_demo_001_e24	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e24_m2	erg	batch_ba_demo_001_e24	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e24_a1	erg	batch_ba_demo_001_e24	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e25_m1	erg	batch_ba_demo_001_e25	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e25_m2	erg	batch_ba_demo_001_e25	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e25_a1	erg	batch_ba_demo_001_e25	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e26_m1	erg	batch_ba_demo_001_e26	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e26_m2	erg	batch_ba_demo_001_e26	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e26_a1	erg	batch_ba_demo_001_e26	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e27_m1	erg	batch_ba_demo_001_e27	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e27_m2	erg	batch_ba_demo_001_e27	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e27_a1	erg	batch_ba_demo_001_e27	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e28_m1	erg	batch_ba_demo_001_e28	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e28_m2	erg	batch_ba_demo_001_e28	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e28_a1	erg	batch_ba_demo_001_e28	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e29_m1	erg	batch_ba_demo_001_e29	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e29_a1	erg	batch_ba_demo_001_e29	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e30_m1	erg	batch_ba_demo_001_e30	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e30_a1	erg	batch_ba_demo_001_e30	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e31_m1	erg	batch_ba_demo_001_e31	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e31_a1	erg	batch_ba_demo_001_e31	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e32_m1	erg	batch_ba_demo_001_e32	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e32_a1	erg	batch_ba_demo_001_e32	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e33_m1	erg	batch_ba_demo_001_e33	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e33_a1	erg	batch_ba_demo_001_e33	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e34_m1	erg	batch_ba_demo_001_e34	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e34_a1	erg	batch_ba_demo_001_e34	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e35_m1	erg	batch_ba_demo_001_e35	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e35_a1	erg	batch_ba_demo_001_e35	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e36_m1	erg	batch_ba_demo_001_e36	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e36_a1	erg	batch_ba_demo_001_e36	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e37_m1	erg	batch_ba_demo_001_e37	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e37_m2	erg	batch_ba_demo_001_e37	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e38_m1	erg	batch_ba_demo_001_e38	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e38_m2	erg	batch_ba_demo_001_e38	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e39_m1	erg	batch_ba_demo_001_e39	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e39_m2	erg	batch_ba_demo_001_e39	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e40_m1	erg	batch_ba_demo_001_e40	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e40_m2	erg	batch_ba_demo_001_e40	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e41_m1	erg	batch_cl_demo_001_e41	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e41_m2	erg	batch_cl_demo_001_e41	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e41_a1	erg	batch_cl_demo_001_e41	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e42_m1	erg	batch_cl_demo_001_e42	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e42_m2	erg	batch_cl_demo_001_e42	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e42_a1	erg	batch_cl_demo_001_e42	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e43_m1	erg	batch_cl_demo_001_e43	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e43_m2	erg	batch_cl_demo_001_e43	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e43_a1	erg	batch_cl_demo_001_e43	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e44_m1	erg	batch_cl_demo_001_e44	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e44_m2	erg	batch_cl_demo_001_e44	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e44_a1	erg	batch_cl_demo_001_e44	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e45_m1	erg	batch_cl_demo_001_e45	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e45_m2	erg	batch_cl_demo_001_e45	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e45_a1	erg	batch_cl_demo_001_e45	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e46_m1	erg	batch_cl_demo_001_e46	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e46_m2	erg	batch_cl_demo_001_e46	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e46_a1	erg	batch_cl_demo_001_e46	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e47_m1	erg	batch_cl_demo_001_e47	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e47_m2	erg	batch_cl_demo_001_e47	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e47_a1	erg	batch_cl_demo_001_e47	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e48_m1	erg	batch_cl_demo_001_e48	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e48_m2	erg	batch_cl_demo_001_e48	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e48_a1	erg	batch_cl_demo_001_e48	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e49_m1	erg	batch_cl_demo_001_e49	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e49_a1	erg	batch_cl_demo_001_e49	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e50_m1	erg	batch_cl_demo_001_e50	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e50_a1	erg	batch_cl_demo_001_e50	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e51_m1	erg	batch_cl_demo_001_e51	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e51_a1	erg	batch_cl_demo_001_e51	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e52_m1	erg	batch_cl_demo_001_e52	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e52_a1	erg	batch_cl_demo_001_e52	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e53_m1	erg	batch_cl_demo_001_e53	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e53_a1	erg	batch_cl_demo_001_e53	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e54_m1	erg	batch_cl_demo_001_e54	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e54_a1	erg	batch_cl_demo_001_e54	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e55_m1	erg	batch_cl_demo_001_e55	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e55_a1	erg	batch_cl_demo_001_e55	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e56_m1	erg	batch_cl_demo_001_e56	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e56_a1	erg	batch_cl_demo_001_e56	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e57_m1	erg	batch_cl_demo_001_e57	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e57_m2	erg	batch_cl_demo_001_e57	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e58_m1	erg	batch_cl_demo_001_e58	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e58_m2	erg	batch_cl_demo_001_e58	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e59_m1	erg	batch_cl_demo_001_e59	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e59_m2	erg	batch_cl_demo_001_e59	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e60_m1	erg	batch_cl_demo_001_e60	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e60_m2	erg	batch_cl_demo_001_e60	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e61_m1	erg	batch_cl_demo_001_e61	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e61_m2	erg	batch_cl_demo_001_e61	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e61_a1	erg	batch_cl_demo_001_e61	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e62_m1	erg	batch_cl_demo_001_e62	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e62_m2	erg	batch_cl_demo_001_e62	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e62_a1	erg	batch_cl_demo_001_e62	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e63_m1	erg	batch_cl_demo_001_e63	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e63_m2	erg	batch_cl_demo_001_e63	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e63_a1	erg	batch_cl_demo_001_e63	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e64_m1	erg	batch_cl_demo_001_e64	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e64_m2	erg	batch_cl_demo_001_e64	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e64_a1	erg	batch_cl_demo_001_e64	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e65_m1	erg	batch_cl_demo_001_e65	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e65_m2	erg	batch_cl_demo_001_e65	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e65_a1	erg	batch_cl_demo_001_e65	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e66_m1	erg	batch_cl_demo_001_e66	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e66_m2	erg	batch_cl_demo_001_e66	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e66_a1	erg	batch_cl_demo_001_e66	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e67_m1	erg	batch_cl_demo_001_e67	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e67_m2	erg	batch_cl_demo_001_e67	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e67_a1	erg	batch_cl_demo_001_e67	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e68_m1	erg	batch_cl_demo_001_e68	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e68_m2	erg	batch_cl_demo_001_e68	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e68_a1	erg	batch_cl_demo_001_e68	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e69_m1	erg	batch_cl_demo_001_e69	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e69_a1	erg	batch_cl_demo_001_e69	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e70_m1	erg	batch_cl_demo_001_e70	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e70_a1	erg	batch_cl_demo_001_e70	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e71_m1	erg	batch_cl_demo_001_e71	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e71_a1	erg	batch_cl_demo_001_e71	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e72_m1	erg	batch_cl_demo_001_e72	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e72_a1	erg	batch_cl_demo_001_e72	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e73_m1	erg	batch_cl_demo_001_e73	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e73_a1	erg	batch_cl_demo_001_e73	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e74_m1	erg	batch_cl_demo_001_e74	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e74_a1	erg	batch_cl_demo_001_e74	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e75_m1	erg	batch_cl_demo_001_e75	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e75_a1	erg	batch_cl_demo_001_e75	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e76_m1	erg	batch_cl_demo_001_e76	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e76_a1	erg	batch_cl_demo_001_e76	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e77_m1	erg	batch_cl_demo_001_e77	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e77_m2	erg	batch_cl_demo_001_e77	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e78_m1	erg	batch_cl_demo_001_e78	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e78_m2	erg	batch_cl_demo_001_e78	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e79_m1	erg	batch_cl_demo_001_e79	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e79_m2	erg	batch_cl_demo_001_e79	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e80_m1	erg	batch_cl_demo_001_e80	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e80_m2	erg	batch_cl_demo_001_e80	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e81_m1	erg	batch_tcd_demo_001_e81	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e81_m2	erg	batch_tcd_demo_001_e81	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e81_a1	erg	batch_tcd_demo_001_e81	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e82_m1	erg	batch_tcd_demo_001_e82	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e82_m2	erg	batch_tcd_demo_001_e82	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e82_a1	erg	batch_tcd_demo_001_e82	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e83_m1	erg	batch_tcd_demo_001_e83	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e83_m2	erg	batch_tcd_demo_001_e83	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e83_a1	erg	batch_tcd_demo_001_e83	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e84_m1	erg	batch_tcd_demo_001_e84	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e84_m2	erg	batch_tcd_demo_001_e84	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e84_a1	erg	batch_tcd_demo_001_e84	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e85_m1	erg	batch_tcd_demo_001_e85	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e85_m2	erg	batch_tcd_demo_001_e85	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e85_a1	erg	batch_tcd_demo_001_e85	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e86_m1	erg	batch_tcd_demo_001_e86	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e86_m2	erg	batch_tcd_demo_001_e86	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e86_a1	erg	batch_tcd_demo_001_e86	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e87_m1	erg	batch_tcd_demo_001_e87	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e87_m2	erg	batch_tcd_demo_001_e87	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e87_a1	erg	batch_tcd_demo_001_e87	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e88_m1	erg	batch_tcd_demo_001_e88	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e88_m2	erg	batch_tcd_demo_001_e88	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e88_a1	erg	batch_tcd_demo_001_e88	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e89_m1	erg	batch_tcd_demo_001_e89	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e89_a1	erg	batch_tcd_demo_001_e89	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e90_m1	erg	batch_tcd_demo_001_e90	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e90_a1	erg	batch_tcd_demo_001_e90	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e91_m1	erg	batch_tcd_demo_001_e91	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e91_a1	erg	batch_tcd_demo_001_e91	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e92_m1	erg	batch_tcd_demo_001_e92	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e92_a1	erg	batch_tcd_demo_001_e92	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e93_m1	erg	batch_tcd_demo_001_e93	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e93_a1	erg	batch_tcd_demo_001_e93	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e94_m1	erg	batch_tcd_demo_001_e94	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e94_a1	erg	batch_tcd_demo_001_e94	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e95_m1	erg	batch_tcd_demo_001_e95	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e95_a1	erg	batch_tcd_demo_001_e95	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e96_m1	erg	batch_tcd_demo_001_e96	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e96_a1	erg	batch_tcd_demo_001_e96	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e97_m1	erg	batch_tcd_demo_001_e97	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e97_m2	erg	batch_tcd_demo_001_e97	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e98_m1	erg	batch_tcd_demo_001_e98	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e98_m2	erg	batch_tcd_demo_001_e98	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e99_m1	erg	batch_tcd_demo_001_e99	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e99_m2	erg	batch_tcd_demo_001_e99	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e100_m1	erg	batch_tcd_demo_001_e100	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e100_m2	erg	batch_tcd_demo_001_e100	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e101_m1	erg	batch_tcd_demo_001_e101	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e101_m2	erg	batch_tcd_demo_001_e101	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e101_a1	erg	batch_tcd_demo_001_e101	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e102_m1	erg	batch_tcd_demo_001_e102	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e102_m2	erg	batch_tcd_demo_001_e102	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e102_a1	erg	batch_tcd_demo_001_e102	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e103_m1	erg	batch_tcd_demo_001_e103	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e103_m2	erg	batch_tcd_demo_001_e103	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e103_a1	erg	batch_tcd_demo_001_e103	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e104_m1	erg	batch_tcd_demo_001_e104	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e104_m2	erg	batch_tcd_demo_001_e104	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e104_a1	erg	batch_tcd_demo_001_e104	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e105_m1	erg	batch_tcd_demo_001_e105	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e105_m2	erg	batch_tcd_demo_001_e105	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e105_a1	erg	batch_tcd_demo_001_e105	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e106_m1	erg	batch_tcd_demo_001_e106	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e106_m2	erg	batch_tcd_demo_001_e106	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e106_a1	erg	batch_tcd_demo_001_e106	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e107_m1	erg	batch_tcd_demo_001_e107	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e107_m2	erg	batch_tcd_demo_001_e107	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e107_a1	erg	batch_tcd_demo_001_e107	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e108_m1	erg	batch_tcd_demo_001_e108	usr_teacher_truc	MAIN	Ngô Xuân Trúc	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e108_m2	erg	batch_tcd_demo_001_e108	usr_teacher_vuong	MAIN	Trần Quốc Vương	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e108_a1	erg	batch_tcd_demo_001_e108	usr_teacher_vang	ASSISTANT	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e109_m1	erg	batch_tcd_demo_001_e109	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e109_a1	erg	batch_tcd_demo_001_e109	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e110_m1	erg	batch_tcd_demo_001_e110	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e110_a1	erg	batch_tcd_demo_001_e110	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e111_m1	erg	batch_tcd_demo_001_e111	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e111_a1	erg	batch_tcd_demo_001_e111	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e112_m1	erg	batch_tcd_demo_001_e112	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e112_a1	erg	batch_tcd_demo_001_e112	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e113_m1	erg	batch_tcd_demo_001_e113	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e113_a1	erg	batch_tcd_demo_001_e113	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e114_m1	erg	batch_tcd_demo_001_e114	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e114_a1	erg	batch_tcd_demo_001_e114	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e115_m1	erg	batch_tcd_demo_001_e115	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e115_a1	erg	batch_tcd_demo_001_e115	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e116_m1	erg	batch_tcd_demo_001_e116	usr_teacher_tuan	MAIN	Trần Tướng Tuấn	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e116_a1	erg	batch_tcd_demo_001_e116	usr_teacher_tram	ASSISTANT	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e117_m1	erg	batch_tcd_demo_001_e117	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e117_m2	erg	batch_tcd_demo_001_e117	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e118_m1	erg	batch_tcd_demo_001_e118	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e118_m2	erg	batch_tcd_demo_001_e118	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e119_m1	erg	batch_tcd_demo_001_e119	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e119_m2	erg	batch_tcd_demo_001_e119	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e120_m1	erg	batch_tcd_demo_001_e120	usr_teacher_vang	MAIN	K Vảng	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e120_m2	erg	batch_tcd_demo_001_e120	usr_teacher_tram	MAIN	Dương Ngọc Trâm	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
\.


--
-- Data for Name: teaching_schedule_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id, level_id, room_id, room_name, title, start_at, end_at, weekday, session, period_start, period_count, status, note, created_by, updated_by, created_at, updated_at) FROM stdin;
batch_ba_demo_001_e1	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-06-15 00:00:00+00	2026-06-15 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e2	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-06-22 00:00:00+00	2026-06-22 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e3	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-06-29 00:00:00+00	2026-06-29 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e4	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-07-06 00:00:00+00	2026-07-06 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e5	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-06-17 00:00:00+00	2026-06-17 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e6	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-06-24 00:00:00+00	2026-06-24 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e7	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-07-01 00:00:00+00	2026-07-01 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e8	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A1	2026-07-08 00:00:00+00	2026-07-08 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e9	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-06-16 02:00:00+00	2026-06-16 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e10	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-06-23 02:00:00+00	2026-06-23 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e11	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-06-30 02:00:00+00	2026-06-30 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e12	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-07-07 02:00:00+00	2026-07-07 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e13	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-06-18 02:00:00+00	2026-06-18 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e14	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-06-25 02:00:00+00	2026-06-25 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e15	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-07-02 02:00:00+00	2026-07-02 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e16	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A1	2026-07-09 02:00:00+00	2026-07-09 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e17	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-06-19 06:00:00+00	2026-06-19 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e18	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-06-26 06:00:00+00	2026-06-26 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e19	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-07-03 06:00:00+00	2026-07-03 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e20	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-07-10 06:00:00+00	2026-07-10 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e21	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-06-15 00:00:00+00	2026-06-15 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e22	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-06-22 00:00:00+00	2026-06-22 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e23	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-06-29 00:00:00+00	2026-06-29 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e24	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-07-06 00:00:00+00	2026-07-06 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e25	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-06-17 00:00:00+00	2026-06-17 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e26	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-06-24 00:00:00+00	2026-06-24 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e27	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-07-01 00:00:00+00	2026-07-01 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e28	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_toan	lvl_toan_cb	room_ba_101	Phòng 101	Toán - 6A2	2026-07-08 00:00:00+00	2026-07-08 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e29	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-06-16 02:00:00+00	2026-06-16 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e30	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-06-23 02:00:00+00	2026-06-23 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e31	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-06-30 02:00:00+00	2026-06-30 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e32	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-07-07 02:00:00+00	2026-07-07 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e33	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-06-18 02:00:00+00	2026-06-18 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e34	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-06-25 02:00:00+00	2026-06-25 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e35	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-07-02 02:00:00+00	2026-07-02 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e36	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_tieng_anh	\N	room_ba_102	Phòng 102	Tiếng Anh - 6A2	2026-07-09 02:00:00+00	2026-07-09 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e37	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-06-19 06:00:00+00	2026-06-19 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e38	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-06-26 06:00:00+00	2026-06-26 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e39	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-07-03 06:00:00+00	2026-07-03 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_ba_demo_001_e40	erg	batch_ba_demo_001	sch_binh_an	sch_binh_an_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-07-10 06:00:00+00	2026-07-10 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e41	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-06-15 00:00:00+00	2026-06-15 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e42	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-06-22 00:00:00+00	2026-06-22 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e43	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-06-29 00:00:00+00	2026-06-29 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e44	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-07-06 00:00:00+00	2026-07-06 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e45	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-06-17 00:00:00+00	2026-06-17 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e46	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-06-24 00:00:00+00	2026-06-24 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e47	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-07-01 00:00:00+00	2026-07-01 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e48	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A1	2026-07-08 00:00:00+00	2026-07-08 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e49	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-06-16 02:00:00+00	2026-06-16 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e50	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-06-23 02:00:00+00	2026-06-23 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e51	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-06-30 02:00:00+00	2026-06-30 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e52	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-07-07 02:00:00+00	2026-07-07 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e53	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-06-18 02:00:00+00	2026-06-18 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e54	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-06-25 02:00:00+00	2026-06-25 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e55	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-07-02 02:00:00+00	2026-07-02 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e56	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A1	2026-07-09 02:00:00+00	2026-07-09 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e57	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-06-19 06:00:00+00	2026-06-19 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e58	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-06-26 06:00:00+00	2026-06-26 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e59	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-07-03 06:00:00+00	2026-07-03 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e60	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-07-10 06:00:00+00	2026-07-10 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e61	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-06-15 00:00:00+00	2026-06-15 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e62	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-06-22 00:00:00+00	2026-06-22 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e63	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-06-29 00:00:00+00	2026-06-29 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e64	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-07-06 00:00:00+00	2026-07-06 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e65	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-06-17 00:00:00+00	2026-06-17 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e66	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-06-24 00:00:00+00	2026-06-24 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e67	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-07-01 00:00:00+00	2026-07-01 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e68	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_toan	lvl_toan_cb	room_cl_101	Phòng 101	Toán - 6A2	2026-07-08 00:00:00+00	2026-07-08 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e69	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-06-16 02:00:00+00	2026-06-16 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e70	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-06-23 02:00:00+00	2026-06-23 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e71	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-06-30 02:00:00+00	2026-06-30 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e72	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-07-07 02:00:00+00	2026-07-07 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e73	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-06-18 02:00:00+00	2026-06-18 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e74	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-06-25 02:00:00+00	2026-06-25 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e75	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-07-02 02:00:00+00	2026-07-02 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e76	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_tieng_anh	\N	room_cl_102	Phòng 102	Tiếng Anh - 6A2	2026-07-09 02:00:00+00	2026-07-09 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e77	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-06-19 06:00:00+00	2026-06-19 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e78	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-06-26 06:00:00+00	2026-06-26 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e79	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-07-03 06:00:00+00	2026-07-03 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_cl_demo_001_e80	erg	batch_cl_demo_001	sch_cat_lai	sch_cat_lai_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-07-10 06:00:00+00	2026-07-10 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e81	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-06-15 00:00:00+00	2026-06-15 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e82	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-06-22 00:00:00+00	2026-06-22 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e83	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-06-29 00:00:00+00	2026-06-29 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e84	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-07-06 00:00:00+00	2026-07-06 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e85	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-06-17 00:00:00+00	2026-06-17 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e86	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-06-24 00:00:00+00	2026-06-24 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e87	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-07-01 00:00:00+00	2026-07-01 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e88	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A1	2026-07-08 00:00:00+00	2026-07-08 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e89	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-06-16 02:00:00+00	2026-06-16 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e90	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-06-23 02:00:00+00	2026-06-23 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e91	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-06-30 02:00:00+00	2026-06-30 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e92	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-07-07 02:00:00+00	2026-07-07 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e93	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-06-18 02:00:00+00	2026-06-18 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e94	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-06-25 02:00:00+00	2026-06-25 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e95	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-07-02 02:00:00+00	2026-07-02 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e96	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A1	2026-07-09 02:00:00+00	2026-07-09 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e97	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-06-19 06:00:00+00	2026-06-19 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e98	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-06-26 06:00:00+00	2026-06-26 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e99	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-07-03 06:00:00+00	2026-07-03 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e100	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a1	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A1	2026-07-10 06:00:00+00	2026-07-10 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e101	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-06-15 00:00:00+00	2026-06-15 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e102	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-06-22 00:00:00+00	2026-06-22 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e103	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-06-29 00:00:00+00	2026-06-29 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e104	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-07-06 00:00:00+00	2026-07-06 01:30:00+00	1	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e105	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-06-17 00:00:00+00	2026-06-17 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e106	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-06-24 00:00:00+00	2026-06-24 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e107	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-07-01 00:00:00+00	2026-07-01 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e108	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_toan	lvl_toan_cb	room_tcd_101	Phòng 101	Toán - 6A2	2026-07-08 00:00:00+00	2026-07-08 01:30:00+00	3	MORNING	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e109	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-06-16 02:00:00+00	2026-06-16 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e110	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-06-23 02:00:00+00	2026-06-23 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e111	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-06-30 02:00:00+00	2026-06-30 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e112	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-07-07 02:00:00+00	2026-07-07 03:30:00+00	2	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e113	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-06-18 02:00:00+00	2026-06-18 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e114	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-06-25 02:00:00+00	2026-06-25 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e115	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-07-02 02:00:00+00	2026-07-02 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e116	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_tieng_anh	\N	room_tcd_102	Phòng 102	Tiếng Anh - 6A2	2026-07-09 02:00:00+00	2026-07-09 03:30:00+00	4	MORNING	3	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e117	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-06-19 06:00:00+00	2026-06-19 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e118	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-06-26 06:00:00+00	2026-06-26 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e119	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-07-03 06:00:00+00	2026-07-03 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
batch_tcd_demo_001_e120	erg	batch_tcd_demo_001	sch_truong_cong_dinh	sch_truong_cong_dinh_g6a2	sub_ngu_van	lvl_van_cb	\N	Phòng Văn	Ngữ Văn - 6A2	2026-07-10 06:00:00+00	2026-07-10 07:30:00+00	5	AFTERNOON	1	2	PUBLISHED	\N	usr_super_admin	\N	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
\.


--
-- Data for Name: teaching_schedule_rooms; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at) FROM stdin;
room_ba_101	erg	sch_binh_an	Phòng 101	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
room_ba_102	erg	sch_binh_an	Phòng 102	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
room_ba_201	erg	sch_binh_an	Phòng 201	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
room_cl_101	erg	sch_cat_lai	Phòng 101	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
room_cl_102	erg	sch_cat_lai	Phòng 102	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
room_tcd_101	erg	sch_truong_cong_dinh	Phòng 101	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
room_tcd_102	erg	sch_truong_cong_dinh	Phòng 102	ACTIVE	usr_super_admin	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00
\.


--
-- Data for Name: topic_content_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.topic_content_items (id, tenant_id, topic_id, content_item_id, content_role, sort_order, status, created_by, created_at, updated_at) FROM stdin;
tci_ic3_intro_deck	erg	top_ic3_intro	cnt_ic3_intro_deck	LECTURE	1	active	seed	2026-07-11 03:06:44.128568+00	2026-07-11 04:21:36.320291+00
tci_ic3_safety_quiz	erg	top_ic3_safety	cnt_ic3_safety_quiz	EXERCISE	1	active	seed	2026-07-11 03:06:44.128568+00	2026-07-11 04:21:36.320291+00
tci_tin_hoc_algo_pdf	erg	top_tin_hoc_algo	cnt_tin_hoc_algo_pdf	LECTURE	1	active	seed	2026-07-11 03:06:44.128568+00	2026-07-11 04:21:36.320291+00
\.


--
-- Data for Name: user_access_scopes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_access_scopes (id, created_at, updated_at, user_id, education_unit_id, modules, role) FROM stdin;
scope_super_admin	2026-07-11 03:03:57.34837+00	2026-07-11 03:03:57.34837+00	usr_super_admin	ctr_system	*	system.super_admin
scope_stu_ba_0001	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	usr_stu_ba_0001	sch_binh_an	lms,elearning	lms.learner
scope_stu_cl_0001	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	usr_stu_cl_0001	sch_cat_lai	lms,elearning	lms.learner
scope_teacher_ba	2026-07-11 03:04:15.942238+00	2026-07-11 03:06:46.885533+00	usr_teacher_ba	sch_binh_an	lms,content	lms.instructor
scope_teacher_cl	2026-07-11 03:04:15.942238+00	2026-07-11 03:06:46.885533+00	usr_teacher_cl	sch_cat_lai	lms,content	lms.instructor
scope_admin_system_001	2026-07-11 04:26:17.475914+00	2026-07-11 04:26:17.475914+00	usr_super_admin	ctr_system	lms,content,cms	system.super_admin
\.


--
-- Data for Name: user_activities; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_activities (id, user_id, tenant_id, action, target_user_id, ip_address, user_agent, description, created_at, request_id, metadata) FROM stdin;
\.


--
-- Data for Name: user_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_permissions (id, user_id, permission, grant_type, reason, expires_at, created_by, created_at) FROM stdin;
\.


--
-- Data for Name: user_role_assignments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_role_assignments (id, user_id, role_id, tenant_id, scope_type, scope_id, expires_at, assigned_by, revoked_at, revoked_by, revoke_reason, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_roles (user_id, role_id) FROM stdin;
usr_teacher_ba	role_lms_teacher
usr_teacher_cl	role_lms_teacher
usr_stu_ba_0001	role_lms_student
usr_stu_cl_0001	role_lms_student
usr_teacher_tram	role_lms_teacher
usr_teacher_truc	role_lms_teacher
usr_teacher_tuan	role_lms_teacher
usr_teacher_vang	role_lms_teacher
usr_teacher_vuong	role_lms_teacher
usr_super_admin	role_super_admin
\.


--
-- Data for Name: user_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_sessions (id, created_at, updated_at, user_id, session_id, device_id, device_name, portal, ip_address, user_agent, refresh_token_hash, access_token_hash, tenant_id, last_active_at, expires_at, revoked_at, revoked_reason, device_fingerprint, platform) FROM stdin;
019f65b8-0ce5-7717-b57b-ce4e5c8ef4dc	2026-07-15 12:19:48.6687+00	2026-07-15 12:20:18.677485+00	usr_super_admin	019f65b8-0ce5-740d-bfda-3da73a55288b	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	uBOIaR72Hj_BJxlB1-WSiWp-5IXjoQQoRqMQ61LM0r8	FGSeKLSTDrl3tZNDokMAWeq11bPDJ3hUUaGwPIS42_I	erg	2026-07-15 12:19:48.144785+00	2026-07-15 20:19:48.144785+00	2026-07-15 12:20:18.507403+00	session_replaced	fp-nnq57j	WEB_DESKTOP
019f65b8-8369-7d85-9903-4454c1d372fc	2026-07-15 12:20:19.001684+00	2026-07-15 12:55:45.508972+00	usr_super_admin	019f65b8-8369-7219-aa16-4baeb610ef67	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	hfSFcdczZU82bb1M4fGfQdsJnfjpswmBEZ5Kaqn-G7s	ldA951XziBYTNAkSLLRGfSzaf9kMYugFfVLrleOiBiY	erg	2026-07-15 12:20:18.507403+00	2026-07-15 20:20:18.507403+00	2026-07-15 12:55:45.257289+00	session_replaced	fp-nnq57j	WEB_DESKTOP
019f4f6d-6179-77c2-8e97-fb0d005c20ed	2026-07-11 04:26:36.446938+00	2026-07-11 07:47:13.743833+00	usr_super_admin	019f4f6d-6179-7c0c-976f-57366f3669f7	codex-runtime-check	Codex runtime check	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	LN0mGZJtnGTaiMYC8I4Ovur5tv7B0LsxsmkBwXc6A_I	w-C1hQkBCKCJ7r0NxbzqOdBWAJdzNMZ9_3YgCWk1qS0	erg	2026-07-11 04:26:36.177657+00	2026-07-11 12:26:36.177657+00	2026-07-11 07:47:13.743833+00	logout_all	codex-runtime-check	WEB_DESKTOP
019f65d8-f74a-750d-8c9c-1272c3e3a747	2026-07-15 12:55:45.816301+00	2026-07-15 12:55:45.816301+00	usr_super_admin	019f65d8-f74a-7f32-b9c8-adfd04d86a65	device-7d554b0e-da68-4881-b44f-1e98a828ee51	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	qvhI6o_qEiBfdAFoYDu35CPPhcUbTQuGldCCWiyx4gk	klwYIIMoqxfhS6zY9iRMAkbeLkouA_6UsfLXhizuFbg	erg	2026-07-15 12:55:45.257289+00	2026-07-15 20:55:45.257289+00	\N	\N	fp-f67ald	WEB_DESKTOP
019f5022-6bfe-7bb5-a5b9-78b80f198a17	2026-07-11 07:44:21.110268+00	2026-07-11 07:47:13.743833+00	usr_super_admin	019f5022-6bfe-709c-8b10-6a6ac37b2029	web	Browser on Desktop	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	yL77QxOZDE0n6no5ThY_meEgPKY9knG7-ZXSlTckJ-E	FTfU9117Mmriy8oAlWaU9x0e1KyL89xU42ylpkB8X_g	erg	2026-07-11 07:44:20.863234+00	2026-07-11 15:44:20.863234+00	2026-07-11 07:47:13.743833+00	logout_all	\N	WEB_DESKTOP
019f5025-12d0-7d93-aa11-285740a84e77	2026-07-11 07:47:14.889745+00	2026-07-11 07:47:14.889745+00	usr_super_admin	019f5025-12d1-7498-86a2-2667bc4fe0ad	smoke-lms	Browser on Desktop	lms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	XfvbfWMATqjxvr3kwHn92YxZAzKlbKIDcGY_z7Mbbiw	52O-L2450zdhoLQSOereaawyHgYqL1f3Wf80KdgCJKQ	erg	2026-07-11 07:47:14.647053+00	2026-07-11 15:47:14.647053+00	\N	\N	\N	WEB_DESKTOP
019f5274-a001-7f30-b905-759fc8fab9ac	2026-07-11 18:33:22.815626+00	2026-07-11 18:33:24.356096+00	usr_super_admin	019f5274-a002-7142-9271-f903859352fc	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	VPz60Gsb2hof9y9GiqOBg4vyxR7jvblArMj0HsJJzHU	-VP3z8pvkx76gPHE13LpKMnwUa0hLPlqL3JjlCa4nEs	erg	2026-07-11 18:33:22.564099+00	2026-07-12 02:33:22.564099+00	2026-07-11 18:33:23.997171+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f5287-417d-71c1-87b6-fd58fba17ac2	2026-07-11 18:53:43.794669+00	2026-07-11 18:53:43.794669+00	usr_super_admin	019f5287-417e-7450-b6bf-6ede4cfa900c	taxonomy-smoke	Browser on Desktop	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT; Windows NT 10.0; en-US) WindowsPowerShell/5.1.26100.8655	Mr6b4dCctDGQwBbZXb2zSbQNbRRfcE2g3ocit278cdk	s2QzxfQE27MTDk8934MLRnmDAWRVxdKYrSzrzks7ojE	erg	2026-07-11 18:53:43.556833+00	2026-07-12 02:53:43.556833+00	\N	\N	\N	WEB_DESKTOP
019f5b1d-7c68-72cd-bd8e-5946d2e367a8	2026-07-13 10:54:47.014248+00	2026-07-13 10:54:47.014248+00	usr_super_admin	019f5b1d-7c68-74d6-b00d-2db45fe6d646	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	_l36HgoHO_V4f5tTXrOzfS4z8GxPFTcwzKi6GqxGpLA	C0Gzt8E67zxc9TX5vzEVvHS87ge9eiG6-UzwPffUSP4	erg	2026-07-13 10:54:46.745002+00	2026-07-13 18:54:46.745002+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f5274-a596-7c29-bf59-d84793720387	2026-07-11 18:33:24.236331+00	2026-07-11 19:29:11.222045+00	usr_super_admin	019f5274-a596-75db-904c-3deee8c18a87	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	yJxuC6kt00GGYueaH-yWmRKkhQIIQiVJLwZ1sYblwjA	QbSjE0gL5shUkXUq2bKYPq0Gmk3sTiymg67fB33OVWo	erg	2026-07-11 18:33:23.997171+00	2026-07-12 02:33:23.997171+00	2026-07-11 19:29:10.838448+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f52a7-b73a-79f3-af65-91ccf6f16775	2026-07-11 19:29:11.093636+00	2026-07-11 19:29:38.876628+00	usr_super_admin	019f52a7-b73a-725a-8fcf-c5504c5c0bf0	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	AbE280X6wo3H9kCwG2Fnohd26N8eKwG9KAGzAD1XHMQ	kLdKtgzNZO3D6LJe1SVtHqoEAj_5RRur39sxJNZmJBo	erg	2026-07-11 19:29:10.838448+00	2026-07-12 03:29:10.838448+00	2026-07-11 19:29:38.514611+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f52a9-8195-7f89-af99-da3c22e4bae3	2026-07-11 19:31:08.428779+00	2026-07-11 19:31:08.428779+00	usr_super_admin	019f52a9-8195-7963-af9e-c34c36ba1b2b	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	uAadPmYUebceUv4u6ZQTQUImEEuWx-Plodu-Nu5FHcA	_FliarJzjiEFEZbMoVPMoqIn39A4uTcEvNSSI22o0qg	erg	2026-07-11 19:31:08.186598+00	2026-07-12 03:31:08.186598+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f52a8-234c-7ab7-8034-ac71aef37656	2026-07-11 19:29:38.755106+00	2026-07-11 19:31:08.54957+00	usr_super_admin	019f52a8-234c-7023-85e3-2d7a2137b1d3	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	AXuYZPBxm0HRo4oqAYgjyXjLZWXfpLF0QZieUFiBiXk	KNOmk-IIYxty6cgLCo_m0SS7ZfG8EHhG0vXBc2BW7AQ	erg	2026-07-11 19:29:38.514611+00	2026-07-12 03:29:38.514611+00	2026-07-11 19:31:08.186598+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f553f-b06b-7c52-8e42-4daefdda7a84	2026-07-12 07:34:25.254702+00	2026-07-12 07:34:27.122411+00	usr_super_admin	019f553f-b06b-77f3-86a8-8644fd144ddd	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	Q8Pnm0VsrzUdcG0kNrBjy5sQhMGOpQYBTY3Uegx-Oso	qzCaY8J39jPhsDa8CorFn7PtYVmlp2iZ94UqDvcB__I	erg	2026-07-12 07:34:24.594235+00	2026-07-12 15:34:24.594235+00	2026-07-12 07:34:26.380733+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f553f-b716-7fa1-8f0e-27a8aceeed15	2026-07-12 07:34:26.973777+00	2026-07-12 12:12:03.369824+00	usr_super_admin	019f553f-b716-7fce-99a7-57ef20afe33e	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	5r3xs0JthOkaHyXgAIiENdP6VsFbt_UTYgNjrF0rQMM	SB1BprM5AGlUpUhZ_2eee3vsITLEpe-ivWLQLOmzX6Y	erg	2026-07-12 09:58:53.865129+00	2026-07-12 15:34:26.380733+00	2026-07-12 12:12:03.017466+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f563d-ded0-7d31-b1d4-a58b9e1db55b	2026-07-12 12:12:03.258673+00	2026-07-12 12:12:04.669007+00	usr_super_admin	019f563d-ded0-796e-9852-ac0c5a09c4ea	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	5SEXKa5tMEDRBD6-EKSi5SoQEWjRfAJrkW4I2emr-Is	UZRreaL7USnZeiFUuagixPQFWl06IevUskgIVorVqYs	erg	2026-07-12 12:12:03.017466+00	2026-07-12 20:12:03.017466+00	2026-07-12 12:12:04.33501+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f563d-e3df-7666-939a-f33e84502ca1	2026-07-12 12:12:04.558611+00	2026-07-12 12:12:06.551063+00	usr_super_admin	019f563d-e3df-73a0-a300-b153739d5d90	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	_oPITd2Z4OI92V3waTe1XB_a71VhIRPorQmx1j3pEWw	iCKLPGkNOss4MP7quRGgxMkgkWAcd8O-1EbxamIZpVA	erg	2026-07-12 12:12:06.550568+00	2026-07-12 20:12:04.33501+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f594f-4da2-7af2-ab5d-fc48807ced76	2026-07-13 02:29:57.373745+00	2026-07-13 02:29:57.373745+00	usr_super_admin	019f594f-4da2-7903-8f1e-2926c3fb4a15	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	dsXwxu1J0Ia_xeqUEGHkkriBV6olpLy19JxtYpeUWsg	p3slY5DiotYMOYnPSLwAaF1puTMfYJfo1A6coh5N_t0	erg	2026-07-13 02:29:57.185515+00	2026-07-13 10:29:57.185515+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f593c-9b90-793c-8355-590b754f9a37	2026-07-13 02:09:32.134692+00	2026-07-13 02:29:57.45952+00	usr_super_admin	019f593c-9b90-7377-b797-ce4790ef103f	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	7pwrvKti_n-C_fPHEROX1y1l1XbwBJhTQ-YhLkbGwkg	G8JfmTqeFWUKQYVuTQmfIEcVwydUzuK_BPf8Fm992YU	erg	2026-07-13 02:09:31.950148+00	2026-07-13 10:09:31.950148+00	2026-07-13 02:29:57.185515+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f5ead-b604-7beb-b094-b903630d162f	2026-07-14 03:31:10.56676+00	2026-07-14 03:31:50.845162+00	usr_super_admin	019f5ead-b604-7b39-899f-c17add81c5b3	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	7bJjkpiXVE_qjhF082B666AiNkCIgSkJgiDknJdj8fQ	FPiUQEwh2xTwiU9mck1DmBkMnF5X-J5gzabJ6AEF41E	erg	2026-07-14 03:31:10.359462+00	2026-07-14 11:31:10.359462+00	2026-07-14 03:31:50.560047+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f5eae-8e37-77d9-88df-c116709d9fbf	2026-07-14 03:32:05.903592+00	2026-07-14 03:32:05.903592+00	usr_super_admin	019f5eae-8e37-78d2-b9dd-504176965ce6	device-7d554b0e-da68-4881-b44f-1e98a828ee51	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	-SMRxlYq9RyO42bkzwBp2a8gyyVySIVI2h4BS9fpQwg	dv4ADlhggHaFRD_Qd1N_8WbAD455gI22xFGgbv0Q688	erg	2026-07-14 03:32:05.725848+00	2026-07-14 11:32:05.725848+00	\N	\N	fp-f67ald	WEB_DESKTOP
019f5eae-5307-7437-8c5c-53dd4ee0425c	2026-07-14 03:31:50.753458+00	2026-07-14 03:42:35.381815+00	usr_super_admin	019f5eae-5307-7de1-a243-8e7a1bcac998	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	nD4HP1EGbqckfojra3yItdOKEU8nLn40GRZDHmiSl4o	IBGPCfNj84UWDNrNn_Vb2snQ650qSwLysIV-2ThbDLU	erg	2026-07-14 03:31:50.560047+00	2026-07-14 11:31:50.560047+00	2026-07-14 03:42:35.10403+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f5f02-f9b8-7a9c-986c-94aab3ed3b21	2026-07-14 05:04:18.445172+00	2026-07-14 05:04:18.445172+00	usr_super_admin	019f5f02-f9b8-750c-99de-2c0f01636260	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	oFHJdWkOyvXZ-LB-VUUv2iDaDcpXXkel_jtzxQTLoN4	wwGMOkH1Ws7pRrGFZDmUK6wVZ_0av0ROSdhQ7WroDJ0	erg	2026-07-14 05:04:18.265979+00	2026-07-14 13:04:18.265979+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f5eb8-28bc-799a-a161-54d7ddea1ca2	2026-07-14 03:42:35.283094+00	2026-07-14 05:04:18.533461+00	usr_super_admin	019f5eb8-28bc-779b-8fea-91ed9dad28ef	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	wZ5NCPe8sM-h378dDEzHET_gSCUtjgx2_M_U9plWfd4	oUbEbEIaGmc7w3GOMn4s1RcnyYlJaTf9GMj8y1YStRQ	erg	2026-07-14 03:42:35.10403+00	2026-07-14 11:42:35.10403+00	2026-07-14 05:04:18.265979+00	replaced_by_new_login	fp-nnq57j	WEB_DESKTOP
019f6368-df02-7371-9409-90ed8a67102e	2026-07-15 01:34:05.133203+00	2026-07-15 02:12:22.530054+00	usr_super_admin	019f6368-df02-759d-a778-d5f63c5e0c3b	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	PYzYQjhswdOWnc1A9Pcu5FJI1sHkfNpO9CrltQboiJU	i1wOjjKEaHJwD81s8F3m6bYHGPFSc9L03grVkRk1cUo	erg	2026-07-15 02:12:21.746232+00	2026-07-15 09:34:04.973347+00	2026-07-15 02:12:22.529059+00	logout	fp-nnq57j	WEB_DESKTOP
019f638c-20c8-7bb3-ace1-2172580504e1	2026-07-15 02:12:35.731175+00	2026-07-15 02:12:35.731175+00	usr_super_admin	019f638c-20c8-7ebd-bdfb-2797411e5ad3	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	6-EEG3uCo0mwxBKeA5fHxKF-C0Tpk6Zm6pyjFApdVgM	FLTE4L7Cs4shPhaHQ25Gy8mt2_jkBObQGaV5eM4U5uE	erg	2026-07-15 02:12:35.57525+00	2026-07-15 10:12:35.57525+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f6490-f9e1-7b82-9ff6-fdaa0dfb1b66	2026-07-15 06:57:30.685753+00	2026-07-15 12:19:48.336873+00	usr_super_admin	019f6490-f9e1-7fd9-ad32-de61108c0f64	web	Chrome on Desktop	elearning	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	rsDVzYOYle85rbpk9PpsuDhN0bCncxifl9tiwk7pN64	QyJSNkobOEnPUt5A6dWpc1r5dCxgEVh0uf9Nj6O7nq4	erg	2026-07-15 06:57:30.499582+00	2026-07-15 14:57:30.499582+00	2026-07-15 12:19:48.144785+00	session_replaced	\N	WEB_DESKTOP
019f65ec-73cd-7f76-9e95-7c57298b075b	2026-07-15 13:17:02.884727+00	2026-07-15 13:17:02.884727+00	usr_super_admin	019f65ec-73cd-73ac-9956-cd38b7be9d0a	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lcms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	ydsWx1z56iMYnVwEg0uYWAj9JedBLlooW8yStwWhPuo	Vd23NnQ2MlbX3w_qkdQWBY99OG2jpnr2sfm0bvJWEg0	erg	2026-07-15 13:17:02.616524+00	2026-07-15 21:17:02.616524+00	\N	\N	fp-nnq57j	WEB_DESKTOP
019f65ee-e2b7-7b98-b4d0-d306c7b93260	2026-07-15 13:19:42.348397+00	2026-07-15 13:19:42.348397+00	usr_super_admin	019f65ee-e2b7-7664-8c7a-72a5e003b283	device-dc0cd0e9-ac9d-4a92-945a-18e737af9a9c	Win32 - Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	lms	0:0:0:0:0:0:0:1	Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36	Qfm11ImoTk3-_eT8XIlpqnrW0juCTUS45dir_3scAMQ	j_DFnS-kPJejNnNTM1v3bRUloKqZrawG9aVd27K4HQs	erg	2026-07-15 13:19:42.093567+00	2026-07-15 21:19:42.093567+00	\N	\N	fp-nnq57j	WEB_DESKTOP
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, created_at, updated_at, email, password_hash, full_name, avatar_url, status, provider, account_type, last_login_provider, tenant_id, phone, bio, gender, date_of_birth, address, city, district, job_title, region, extended_profile, is_profile_completed, last_login_at, login_count, username, must_change_password, recovery_email, recovery_email_verified, authz_version) FROM stdin;
usr_teacher_ba	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	teacher.binh.an@example.test	\N	Binh An Teacher	\N	ACTIVE	seed	erg	seed	erg	\N	\N	\N	\N	\N	\N	\N	LMS teacher	\N	\N	t	\N	0	teacher.binh.an@example.test	f	\N	f	1
usr_teacher_cl	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	teacher.cat.lai@example.test	\N	Cat Lai Teacher	\N	ACTIVE	seed	erg	seed	erg	\N	\N	\N	\N	\N	\N	\N	LMS teacher	\N	\N	t	\N	0	teacher.cat.lai@example.test	f	\N	f	1
usr_stu_ba_0001	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	ba.2025.0001@example.test	\N	Nguyen An	\N	ACTIVE	seed	erg	seed	erg	\N	\N	\N	\N	\N	\N	\N	LMS student	\N	\N	t	\N	0	ba.2025.0001@example.test	f	\N	f	1
usr_stu_cl_0001	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	cl.2025.0001@example.test	\N	Tran Binh	\N	ACTIVE	seed	erg	seed	erg	\N	\N	\N	\N	\N	\N	\N	LMS student	\N	\N	t	\N	0	cl.2025.0001@example.test	f	\N	f	1
usr_relationship_placeholder	2026-07-11 03:04:15.942238+00	2026-07-11 03:04:15.942238+00	relationship-placeholder@example.test	\N	Relationship Placeholder	\N	ACTIVE	seed	system	seed	erg	\N	\N	\N	\N	\N	\N	\N	Data integrity placeholder	\N	\N	t	\N	0	relationship-placeholder@example.test	f	\N	f	1
usr_teacher_truc	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	truc@erg.edu.vn	\N	Ngô Xuân Trúc	\N	ACTIVE	local	lms	\N	erg	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	0	truc@erg.edu.vn	f	\N	f	1
usr_teacher_vuong	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	vuong@erg.edu.vn	\N	Trần Quốc Vương	\N	ACTIVE	local	lms	\N	erg	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	0	vuong@erg.edu.vn	f	\N	f	1
usr_teacher_tuan	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	tuan@erg.edu.vn	\N	Trần Tướng Tuấn	\N	ACTIVE	local	lms	\N	erg	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	0	tuan@erg.edu.vn	f	\N	f	1
usr_teacher_vang	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	vang@erg.edu.vn	\N	K Vảng	\N	ACTIVE	local	lms	\N	erg	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	0	vang@erg.edu.vn	f	\N	f	1
usr_teacher_tram	2026-07-11 03:05:18.427554+00	2026-07-11 03:05:18.427554+00	tram@erg.edu.vn	\N	Dương Ngọc Trâm	\N	ACTIVE	local	lms	\N	erg	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	t	\N	0	tram@erg.edu.vn	f	\N	f	1
seed.question.bank	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	seed.question.bank@erg.local	\N	Question Bank Seed	\N	ACTIVE	seed	seed	\N	erg	\N	\N	\N	\N	\N	\N	\N	System seed	\N	\N	\N	\N	\N	seed.question.bank@erg.local	f	\N	f	1
seed.quiz.demo	2026-07-11 03:06:01.185442+00	2026-07-11 03:06:01.185442+00	seed.quiz.demo@erg.local	\N	Quiz Demo Seed	\N	ACTIVE	seed	seed	\N	erg	\N	\N	\N	\N	\N	\N	\N	System seed	\N	\N	\N	\N	\N	seed.quiz.demo@erg.local	f	\N	f	1
usr_super_admin	2026-07-11 03:03:57.34837+00	2026-07-15 13:19:42.433537+00	admin@erg.edu.vn	$argon2id$v=19$m=16384,t=2,p=1$Y1xo5N7TR2o0n8flxcsehw$TBb8yQcuYHtKBtKQvk9ZbUnXLC6avXqQY6tqlVgmnqs	ERG Super Admin	\N	ACTIVE	local	erg	erg	erg	\N	\N	\N	\N	\N	\N	\N	System Super Admin	\N	\N	t	2026-07-15 13:19:42.093567+00	29	admin@erg.edu.vn	f	\N	t	1
\.


--
-- Data for Name: work_shifts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.work_shifts (id, created_at, updated_at, user_id, school_id, room, teaching_subject, start_time, end_time, type, status, note, remuneration, confirmed_by, actual_start_time, actual_end_time) FROM stdin;
\.


--
-- Name: academic_classes academic_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_classes
    ADD CONSTRAINT academic_classes_pkey PRIMARY KEY (id);


--
-- Name: account_verification_codes account_verification_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account_verification_codes
    ADD CONSTRAINT account_verification_codes_pkey PRIMARY KEY (id);


--
-- Name: assignment_recipients assignment_recipients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_recipients
    ADD CONSTRAINT assignment_recipients_pkey PRIMARY KEY (id);


--
-- Name: assignment_resources assignment_resources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_resources
    ADD CONSTRAINT assignment_resources_pkey PRIMARY KEY (assignment_id, quiz_id);


--
-- Name: attempt_answers attempt_answers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_answers
    ADD CONSTRAINT attempt_answers_pkey PRIMARY KEY (attempt_id, quiz_version_question_id);


--
-- Name: attempt_events attempt_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_events
    ADD CONSTRAINT attempt_events_pkey PRIMARY KEY (id);


--
-- Name: auth_login_attempts auth_login_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_attempts
    ADD CONSTRAINT auth_login_attempts_pkey PRIMARY KEY (id);


--
-- Name: certificates certificates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.certificates
    ADD CONSTRAINT certificates_pkey PRIMARY KEY (id);


--
-- Name: content_assets content_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_assets
    ADD CONSTRAINT content_assets_pkey PRIMARY KEY (id);


--
-- Name: content_item_assets content_item_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_item_assets
    ADD CONSTRAINT content_item_assets_pkey PRIMARY KEY (id);


--
-- Name: content_items content_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_items
    ADD CONSTRAINT content_items_pkey PRIMARY KEY (id);


--
-- Name: crm_referrals crm_referrals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_referrals
    ADD CONSTRAINT crm_referrals_pkey PRIMARY KEY (id);


--
-- Name: crm_school_activities crm_school_activities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_activities
    ADD CONSTRAINT crm_school_activities_pkey PRIMARY KEY (id);


--
-- Name: crm_school_contacts crm_school_contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_contacts
    ADD CONSTRAINT crm_school_contacts_pkey PRIMARY KEY (id);


--
-- Name: crm_school_deals crm_school_deals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_deals
    ADD CONSTRAINT crm_school_deals_pkey PRIMARY KEY (id);


--
-- Name: crm_school_profiles crm_school_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_profiles
    ADD CONSTRAINT crm_school_profiles_pkey PRIMARY KEY (id);


--
-- Name: curriculum_levels curriculum_levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_levels
    ADD CONSTRAINT curriculum_levels_pkey PRIMARY KEY (id);


--
-- Name: curriculum_subjects curriculum_subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_subjects
    ADD CONSTRAINT curriculum_subjects_pkey PRIMARY KEY (id);


--
-- Name: curriculum_topics curriculum_topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_topics
    ADD CONSTRAINT curriculum_topics_pkey PRIMARY KEY (id);


--
-- Name: education_units education_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.education_units
    ADD CONSTRAINT education_units_pkey PRIMARY KEY (id);


--
-- Name: employee_profiles employee_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_profiles
    ADD CONSTRAINT employee_profiles_pkey PRIMARY KEY (id);


--
-- Name: employee_profiles employee_profiles_tenant_id_employee_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_profiles
    ADD CONSTRAINT employee_profiles_tenant_id_employee_code_key UNIQUE (tenant_id, employee_code);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: lms_assignments lms_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_assignments
    ADD CONSTRAINT lms_assignments_pkey PRIMARY KEY (id);


--
-- Name: lms_attempts lms_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT lms_attempts_pkey PRIMARY KEY (id);


--
-- Name: lms_levels lms_levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_levels
    ADD CONSTRAINT lms_levels_pkey PRIMARY KEY (id);


--
-- Name: lms_questions lms_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT lms_questions_pkey PRIMARY KEY (id);


--
-- Name: lms_quiz_questions lms_quiz_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quiz_questions
    ADD CONSTRAINT lms_quiz_questions_pkey PRIMARY KEY (id);


--
-- Name: lms_quizzes lms_quizzes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT lms_quizzes_pkey PRIMARY KEY (id);


--
-- Name: lms_students lms_students_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_students
    ADD CONSTRAINT lms_students_pkey PRIMARY KEY (id);


--
-- Name: lms_subjects lms_subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_subjects
    ADD CONSTRAINT lms_subjects_pkey PRIMARY KEY (id);


--
-- Name: lms_topics lms_topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_topics
    ADD CONSTRAINT lms_topics_pkey PRIMARY KEY (id);


--
-- Name: media_assets media_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_assets
    ADD CONSTRAINT media_assets_pkey PRIMARY KEY (id);


--
-- Name: media_references media_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_references
    ADD CONSTRAINT media_references_pkey PRIMARY KEY (id);


--
-- Name: notification_delivery_attempts notification_delivery_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT notification_delivery_attempts_pkey PRIMARY KEY (id);


--
-- Name: notification_device_registrations notification_device_registrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_device_registrations
    ADD CONSTRAINT notification_device_registrations_pkey PRIMARY KEY (id);


--
-- Name: notification_messages notification_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_messages
    ADD CONSTRAINT notification_messages_pkey PRIMARY KEY (id);


--
-- Name: notification_outbox notification_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_outbox
    ADD CONSTRAINT notification_outbox_pkey PRIMARY KEY (id);


--
-- Name: notification_preferences notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (id);


--
-- Name: notification_recipients notification_recipients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT notification_recipients_pkey PRIMARY KEY (id);


--
-- Name: notification_retention_config notification_retention_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_retention_config
    ADD CONSTRAINT notification_retention_config_pkey PRIMARY KEY (id);


--
-- Name: notification_topic_memberships notification_topic_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_topic_memberships
    ADD CONSTRAINT notification_topic_memberships_pkey PRIMARY KEY (id);


--
-- Name: permission_bindings permission_bindings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_pkey PRIMARY KEY (id);


--
-- Name: permission_groups permission_groups_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_groups
    ADD CONSTRAINT permission_groups_name_key UNIQUE (name);


--
-- Name: permission_groups permission_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_groups
    ADD CONSTRAINT permission_groups_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_name_key UNIQUE (name);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);


--
-- Name: question_bank_categories question_bank_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_bank_categories
    ADD CONSTRAINT question_bank_categories_pkey PRIMARY KEY (id);


--
-- Name: question_bank_levels question_bank_levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_bank_levels
    ADD CONSTRAINT question_bank_levels_pkey PRIMARY KEY (id);


--
-- Name: question_bank_subjects question_bank_subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_bank_subjects
    ADD CONSTRAINT question_bank_subjects_pkey PRIMARY KEY (id);


--
-- Name: question_types question_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_types
    ADD CONSTRAINT question_types_pkey PRIMARY KEY (id);


--
-- Name: quiz_bank_items quiz_bank_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_bank_items
    ADD CONSTRAINT quiz_bank_items_pkey PRIMARY KEY (id);


--
-- Name: quiz_drafts quiz_drafts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_drafts
    ADD CONSTRAINT quiz_drafts_pkey PRIMARY KEY (id);


--
-- Name: quiz_packages quiz_packages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_packages
    ADD CONSTRAINT quiz_packages_pkey PRIMARY KEY (id);


--
-- Name: quiz_version_questions quiz_version_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_questions
    ADD CONSTRAINT quiz_version_questions_pkey PRIMARY KEY (id);


--
-- Name: quiz_version_sections quiz_version_sections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_sections
    ADD CONSTRAINT quiz_version_sections_pkey PRIMARY KEY (id);


--
-- Name: quiz_versions quiz_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_versions
    ADD CONSTRAINT quiz_versions_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: school_academic_years school_academic_years_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_academic_years
    ADD CONSTRAINT school_academic_years_pkey PRIMARY KEY (id);


--
-- Name: school_grades school_grades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_grades
    ADD CONSTRAINT school_grades_pkey PRIMARY KEY (id);


--
-- Name: school_operational_profiles school_operational_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_operational_profiles
    ADD CONSTRAINT school_operational_profiles_pkey PRIMARY KEY (school_id);


--
-- Name: security_events security_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT security_events_pkey PRIMARY KEY (id);


--
-- Name: student_group_members student_group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_group_members
    ADD CONSTRAINT student_group_members_pkey PRIMARY KEY (group_id, student_id);


--
-- Name: student_groups student_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_groups
    ADD CONSTRAINT student_groups_pkey PRIMARY KEY (id);


--
-- Name: student_progress student_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT student_progress_pkey PRIMARY KEY (id);


--
-- Name: teaching_schedule_batches teaching_schedule_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_batches
    ADD CONSTRAINT teaching_schedule_batches_pkey PRIMARY KEY (id);


--
-- Name: teaching_schedule_conflicts teaching_schedule_conflicts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_conflicts
    ADD CONSTRAINT teaching_schedule_conflicts_pkey PRIMARY KEY (id);


--
-- Name: teaching_schedule_event_teachers teaching_schedule_event_teachers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_event_teachers
    ADD CONSTRAINT teaching_schedule_event_teachers_pkey PRIMARY KEY (id);


--
-- Name: teaching_schedule_events teaching_schedule_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT teaching_schedule_events_pkey PRIMARY KEY (id);


--
-- Name: teaching_schedule_rooms teaching_schedule_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_rooms
    ADD CONSTRAINT teaching_schedule_rooms_pkey PRIMARY KEY (id);


--
-- Name: topic_content_items topic_content_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_content_items
    ADD CONSTRAINT topic_content_items_pkey PRIMARY KEY (id);


--
-- Name: school_grades uk_school_grade_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_grades
    ADD CONSTRAINT uk_school_grade_code UNIQUE (tenant_id, school_id, academic_year_id, grade_code);


--
-- Name: school_operational_profiles uk_school_profile_tenant_school; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_operational_profiles
    ADD CONSTRAINT uk_school_profile_tenant_school UNIQUE (tenant_id, school_id);


--
-- Name: school_academic_years uk_school_year_start; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_academic_years
    ADD CONSTRAINT uk_school_year_start UNIQUE (tenant_id, school_id, start_year);


--
-- Name: content_item_assets uq_content_item_assets_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_item_assets
    ADD CONSTRAINT uq_content_item_assets_role UNIQUE (tenant_id, content_item_id, content_asset_id, asset_role);


--
-- Name: curriculum_levels uq_curriculum_levels_tenant_subject_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_levels
    ADD CONSTRAINT uq_curriculum_levels_tenant_subject_code UNIQUE (tenant_id, subject_id, code);


--
-- Name: curriculum_subjects uq_curriculum_subjects_tenant_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_subjects
    ADD CONSTRAINT uq_curriculum_subjects_tenant_code UNIQUE (tenant_id, code);


--
-- Name: curriculum_topics uq_curriculum_topics_tenant_level_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_topics
    ADD CONSTRAINT uq_curriculum_topics_tenant_level_code UNIQUE (tenant_id, level_id, code);


--
-- Name: employee_profiles uq_employee_profiles_tenant_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_profiles
    ADD CONSTRAINT uq_employee_profiles_tenant_user UNIQUE (tenant_id, user_id);


--
-- Name: topic_content_items uq_topic_content_items_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_content_items
    ADD CONSTRAINT uq_topic_content_items_item UNIQUE (tenant_id, topic_id, content_item_id, content_role);


--
-- Name: teaching_schedule_event_teachers uq_tset_event_teacher_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_event_teachers
    ADD CONSTRAINT uq_tset_event_teacher_role UNIQUE (event_id, teacher_user_id, role);


--
-- Name: teaching_schedule_rooms uq_tsr_school_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_rooms
    ADD CONSTRAINT uq_tsr_school_name UNIQUE (school_id, name);


--
-- Name: user_access_scopes user_access_scopes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_access_scopes
    ADD CONSTRAINT user_access_scopes_pkey PRIMARY KEY (id);


--
-- Name: user_activities user_activities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_activities
    ADD CONSTRAINT user_activities_pkey PRIMARY KEY (id);


--
-- Name: user_permissions user_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_pkey PRIMARY KEY (id);


--
-- Name: user_role_assignments user_role_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT user_role_assignments_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: user_sessions user_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: work_shifts work_shifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT work_shifts_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_academic_classes_read_school_status_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_academic_classes_read_school_status_name ON public.academic_classes USING btree (tenant_id, school_id, status, name);


--
-- Name: idx_academic_classes_tenant_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_academic_classes_tenant_school ON public.academic_classes USING btree (tenant_id, school_id);


--
-- Name: idx_academic_classes_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_academic_classes_tenant_status ON public.academic_classes USING btree (tenant_id, status);


--
-- Name: idx_academic_classes_tenant_teacher; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_academic_classes_tenant_teacher ON public.academic_classes USING btree (tenant_id, homeroom_teacher_id);


--
-- Name: idx_account_verification_codes_user_purpose; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_account_verification_codes_user_purpose ON public.account_verification_codes USING btree (user_id, purpose, expires_at DESC);


--
-- Name: idx_assignment_recipients_tenant_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assignment_recipients_tenant_class ON public.assignment_recipients USING btree (tenant_id, class_id);


--
-- Name: idx_assignment_recipients_tenant_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assignment_recipients_tenant_student ON public.assignment_recipients USING btree (tenant_id, student_id);


--
-- Name: idx_assignment_resources_tenant_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assignment_resources_tenant_quiz ON public.assignment_resources USING btree (tenant_id, quiz_id);


--
-- Name: idx_attempt_answers_attempt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attempt_answers_attempt ON public.attempt_answers USING btree (attempt_id);


--
-- Name: idx_attempt_answers_question; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attempt_answers_question ON public.attempt_answers USING btree (quiz_version_question_id);


--
-- Name: idx_attempt_events_attempt_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attempt_events_attempt_created ON public.attempt_events USING btree (attempt_id, created_at);


--
-- Name: idx_auth_login_attempts_email_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_login_attempts_email_created ON public.auth_login_attempts USING btree (attempted_email, created_at);


--
-- Name: idx_auth_login_attempts_email_result_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_login_attempts_email_result_created ON public.auth_login_attempts USING btree (attempted_email, result, created_at DESC);


--
-- Name: idx_auth_login_attempts_ip_result; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_login_attempts_ip_result ON public.auth_login_attempts USING btree (ip_address, result);


--
-- Name: idx_auth_login_attempts_ip_result_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_login_attempts_ip_result_created ON public.auth_login_attempts USING btree (ip_address, result, created_at DESC);


--
-- Name: idx_auth_login_attempts_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_login_attempts_user_created ON public.auth_login_attempts USING btree (user_id, created_at);


--
-- Name: idx_certificates_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_certificates_user_id ON public.certificates USING btree (user_id);


--
-- Name: idx_content_assets_tenant_checksum; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_assets_tenant_checksum ON public.content_assets USING btree (tenant_id, checksum_sha256);


--
-- Name: idx_content_assets_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_assets_tenant_status ON public.content_assets USING btree (tenant_id, status, updated_at DESC);


--
-- Name: idx_content_item_assets_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_item_assets_asset ON public.content_item_assets USING btree (tenant_id, content_asset_id);


--
-- Name: idx_content_item_assets_item_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_item_assets_item_sort ON public.content_item_assets USING btree (tenant_id, content_item_id, sort_order);


--
-- Name: idx_content_items_tenant_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_items_tenant_owner ON public.content_items USING btree (tenant_id, owner_id);


--
-- Name: idx_content_items_tenant_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_items_tenant_status_sort ON public.content_items USING btree (tenant_id, status, updated_at DESC);


--
-- Name: idx_content_items_tenant_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_items_tenant_type_status ON public.content_items USING btree (tenant_id, content_type, status);


--
-- Name: idx_crm_referrals_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_referrals_school ON public.crm_referrals USING btree (school_id);


--
-- Name: idx_crm_referrals_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_referrals_user ON public.crm_referrals USING btree (referrer_user_id);


--
-- Name: idx_crm_school_activities_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_activities_actor ON public.crm_school_activities USING btree (actor_user_id);


--
-- Name: idx_crm_school_activities_follow_up; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_activities_follow_up ON public.crm_school_activities USING btree (next_follow_up_at);


--
-- Name: idx_crm_school_activities_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_activities_school ON public.crm_school_activities USING btree (school_id);


--
-- Name: idx_crm_school_contacts_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_contacts_phone ON public.crm_school_contacts USING btree (phone);


--
-- Name: idx_crm_school_contacts_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_contacts_school ON public.crm_school_contacts USING btree (school_id);


--
-- Name: idx_crm_school_contacts_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_contacts_type ON public.crm_school_contacts USING btree (school_id, contact_type);


--
-- Name: idx_crm_school_deals_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_deals_created ON public.crm_school_deals USING btree (owner_user_id, created_at);


--
-- Name: idx_crm_school_deals_owner_stage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_deals_owner_stage ON public.crm_school_deals USING btree (owner_user_id, stage);


--
-- Name: idx_crm_school_deals_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_deals_school ON public.crm_school_deals USING btree (school_id);


--
-- Name: idx_crm_school_profiles_lead_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_profiles_lead_owner ON public.crm_school_profiles USING btree (lead_owner_user_id);


--
-- Name: idx_crm_school_profiles_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_profiles_manager ON public.crm_school_profiles USING btree (manager_user_id);


--
-- Name: idx_crm_school_profiles_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_profiles_owner ON public.crm_school_profiles USING btree (owner_user_id);


--
-- Name: idx_crm_school_profiles_referrer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_profiles_referrer ON public.crm_school_profiles USING btree (introduced_by_user_id);


--
-- Name: idx_crm_school_profiles_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crm_school_profiles_school ON public.crm_school_profiles USING btree (school_id);


--
-- Name: idx_curriculum_levels_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_levels_tenant_code ON public.curriculum_levels USING btree (tenant_id, code);


--
-- Name: idx_curriculum_levels_tenant_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_levels_tenant_status_sort ON public.curriculum_levels USING btree (tenant_id, status, sort_order);


--
-- Name: idx_curriculum_levels_tenant_subject_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_levels_tenant_subject_status ON public.curriculum_levels USING btree (tenant_id, subject_id, status);


--
-- Name: idx_curriculum_levels_tenant_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_levels_tenant_updated ON public.curriculum_levels USING btree (tenant_id, updated_at);


--
-- Name: idx_curriculum_subjects_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_subjects_tenant_code ON public.curriculum_subjects USING btree (tenant_id, code);


--
-- Name: idx_curriculum_subjects_tenant_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_subjects_tenant_status_sort ON public.curriculum_subjects USING btree (tenant_id, status, sort_order);


--
-- Name: idx_curriculum_subjects_tenant_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_subjects_tenant_updated ON public.curriculum_subjects USING btree (tenant_id, updated_at);


--
-- Name: idx_curriculum_topics_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_topics_tenant_code ON public.curriculum_topics USING btree (tenant_id, code);


--
-- Name: idx_curriculum_topics_tenant_level_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_topics_tenant_level_status ON public.curriculum_topics USING btree (tenant_id, level_id, status);


--
-- Name: idx_curriculum_topics_tenant_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_topics_tenant_status_sort ON public.curriculum_topics USING btree (tenant_id, status, sort_order);


--
-- Name: idx_curriculum_topics_tenant_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_topics_tenant_updated ON public.curriculum_topics USING btree (tenant_id, updated_at);


--
-- Name: idx_education_units_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_education_units_created_by ON public.education_units USING btree (created_by);


--
-- Name: idx_education_units_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_education_units_name ON public.education_units USING btree (name);


--
-- Name: idx_education_units_owner_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_education_units_owner_user_id ON public.education_units USING btree (owner_user_id);


--
-- Name: idx_education_units_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_education_units_parent ON public.education_units USING btree (parent_id);


--
-- Name: idx_education_units_tenant_type_status_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_education_units_tenant_type_status_name ON public.education_units USING btree (tenant_id, type, status, name);


--
-- Name: idx_education_units_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_education_units_type_status ON public.education_units USING btree (type, status);


--
-- Name: idx_employee_profiles_tenant_department; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employee_profiles_tenant_department ON public.employee_profiles USING btree (tenant_id, department_id, employee_code);


--
-- Name: idx_employee_profiles_tenant_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employee_profiles_tenant_manager ON public.employee_profiles USING btree (tenant_id, manager_id);


--
-- Name: idx_lms_assignments_read_class_status_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_assignments_read_class_status_due ON public.lms_assignments USING btree (tenant_id, academic_class_id, status, due_at, created_at DESC);


--
-- Name: idx_lms_assignments_read_teacher_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_assignments_read_teacher_status_created ON public.lms_assignments USING btree (tenant_id, assigned_by, status, created_at DESC);


--
-- Name: idx_lms_assignments_tenant_academic_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_assignments_tenant_academic_class ON public.lms_assignments USING btree (tenant_id, academic_class_id);


--
-- Name: idx_lms_assignments_tenant_assigned_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_assignments_tenant_assigned_by ON public.lms_assignments USING btree (tenant_id, assigned_by);


--
-- Name: idx_lms_assignments_tenant_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_assignments_tenant_idempotency ON public.lms_assignments USING btree (tenant_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);


--
-- Name: idx_lms_assignments_tenant_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_assignments_tenant_quiz ON public.lms_assignments USING btree (tenant_id, quiz_id);


--
-- Name: idx_lms_attempts_read_assignment_status_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_read_assignment_status_updated ON public.lms_attempts USING btree (tenant_id, assignment_id, status, updated_at DESC);


--
-- Name: idx_lms_attempts_read_student_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_read_student_updated ON public.lms_attempts USING btree (tenant_id, student_id, updated_at DESC);


--
-- Name: idx_lms_attempts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_status ON public.lms_attempts USING btree (tenant_id, status);


--
-- Name: idx_lms_attempts_tenant_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_tenant_assignment ON public.lms_attempts USING btree (tenant_id, assignment_id);


--
-- Name: idx_lms_attempts_tenant_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_tenant_quiz ON public.lms_attempts USING btree (tenant_id, quiz_id);


--
-- Name: idx_lms_attempts_tenant_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_tenant_student ON public.lms_attempts USING btree (tenant_id, student_id);


--
-- Name: idx_lms_attempts_tenant_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_attempts_tenant_version ON public.lms_attempts USING btree (tenant_id, quiz_id, quiz_version_id);


--
-- Name: idx_lms_levels_subject_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_levels_subject_id ON public.lms_levels USING btree (subject_id);


--
-- Name: idx_lms_levels_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_levels_tenant_status ON public.lms_levels USING btree (tenant_id, status);


--
-- Name: idx_lms_levels_tenant_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_levels_tenant_subject ON public.lms_levels USING btree (tenant_id, subject_id);


--
-- Name: idx_lms_questions_bank_filters_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_bank_filters_active ON public.lms_questions USING btree (tenant_id, subject_id, level_id, topic_id, lower((status)::text), updated_at DESC) WHERE (archived_at IS NULL);


--
-- Name: idx_lms_questions_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_deleted_at ON public.lms_questions USING btree (deleted_at) WHERE (deleted_at IS NOT NULL);


--
-- Name: idx_lms_questions_level_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_level_id ON public.lms_questions USING btree (level_id);


--
-- Name: idx_lms_questions_stem_fts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_stem_fts ON public.lms_questions USING gin (to_tsvector('simple'::regconfig, (COALESCE(stem, ''::character varying))::text));


--
-- Name: idx_lms_questions_stem_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_stem_trgm ON public.lms_questions USING gin (lower((COALESCE(stem, ''::character varying))::text) public.gin_trgm_ops) WHERE (archived_at IS NULL);


--
-- Name: idx_lms_questions_subject_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_subject_id ON public.lms_questions USING btree (subject_id);


--
-- Name: idx_lms_questions_tags; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_tags ON public.lms_questions USING gin (tags jsonb_path_ops);


--
-- Name: idx_lms_questions_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_tenant_status ON public.lms_questions USING btree (tenant_id, status);


--
-- Name: idx_lms_questions_tenant_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_tenant_subject ON public.lms_questions USING btree (tenant_id, subject_id);


--
-- Name: idx_lms_questions_tenant_taxonomy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_tenant_taxonomy ON public.lms_questions USING btree (tenant_id, subject_id, level_id, category_id);


--
-- Name: idx_lms_questions_tenant_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_tenant_topic ON public.lms_questions USING btree (tenant_id, topic_id);


--
-- Name: idx_lms_questions_tenant_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_tenant_type_status ON public.lms_questions USING btree (tenant_id, type, status);


--
-- Name: idx_lms_questions_topic_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_questions_topic_id ON public.lms_questions USING btree (topic_id);


--
-- Name: idx_lms_quiz_questions_question; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quiz_questions_question ON public.lms_quiz_questions USING btree (tenant_id, question_id);


--
-- Name: idx_lms_quiz_questions_quiz_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quiz_questions_quiz_order ON public.lms_quiz_questions USING btree (tenant_id, quiz_id, order_index);


--
-- Name: idx_lms_quizzes_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_deleted_at ON public.lms_quizzes USING btree (deleted_at) WHERE (deleted_at IS NOT NULL);


--
-- Name: idx_lms_quizzes_lcms_bank_filters; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_lcms_bank_filters ON public.lms_quizzes USING btree (tenant_id, subject_id, level_id, category_id, lower((kind)::text), lower((status)::text), updated_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_lms_quizzes_level_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_level_id ON public.lms_quizzes USING btree (level_id);


--
-- Name: idx_lms_quizzes_tenant_portal_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_tenant_portal_status ON public.lms_quizzes USING btree (tenant_id, portal, status);


--
-- Name: idx_lms_quizzes_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_tenant_status ON public.lms_quizzes USING btree (tenant_id, status);


--
-- Name: idx_lms_quizzes_tenant_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_tenant_subject ON public.lms_quizzes USING btree (tenant_id, subject_id);


--
-- Name: idx_lms_quizzes_tenant_taxonomy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_tenant_taxonomy ON public.lms_quizzes USING btree (tenant_id, subject_id, level_id, category_id);


--
-- Name: idx_lms_quizzes_tenant_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_tenant_updated ON public.lms_quizzes USING btree (tenant_id, updated_at DESC);


--
-- Name: idx_lms_quizzes_title_fts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_title_fts ON public.lms_quizzes USING gin (to_tsvector('simple'::regconfig, (COALESCE(title, ''::character varying))::text));


--
-- Name: idx_lms_quizzes_title_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_quizzes_title_trgm ON public.lms_quizzes USING gin (lower((COALESCE(title, ''::character varying))::text) public.gin_trgm_ops) WHERE (deleted_at IS NULL);


--
-- Name: idx_lms_students_read_class_status_name_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_students_read_class_status_name_code ON public.lms_students USING btree (tenant_id, academic_class_id, status, full_name, student_code);


--
-- Name: idx_lms_students_tenant_academic_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_students_tenant_academic_class ON public.lms_students USING btree (tenant_id, academic_class_id);


--
-- Name: idx_lms_students_tenant_auth_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_students_tenant_auth_user ON public.lms_students USING btree (tenant_id, auth_user_id);


--
-- Name: idx_lms_students_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_students_tenant_code ON public.lms_students USING btree (tenant_id, student_code);


--
-- Name: idx_lms_students_tenant_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_students_tenant_school ON public.lms_students USING btree (tenant_id, school_id);


--
-- Name: idx_lms_subjects_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_subjects_tenant_code ON public.lms_subjects USING btree (tenant_id, code);


--
-- Name: idx_lms_subjects_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_subjects_tenant_status ON public.lms_subjects USING btree (tenant_id, status);


--
-- Name: idx_lms_topics_level_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_topics_level_id ON public.lms_topics USING btree (level_id);


--
-- Name: idx_lms_topics_tenant_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_topics_tenant_level ON public.lms_topics USING btree (tenant_id, level_id);


--
-- Name: idx_lms_topics_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_topics_tenant_status ON public.lms_topics USING btree (tenant_id, status);


--
-- Name: idx_media_assets_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_assets_tenant_status ON public.media_assets USING btree (tenant_id, status);


--
-- Name: idx_media_references_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_references_owner ON public.media_references USING btree (tenant_id, owner_type, owner_id);


--
-- Name: idx_notification_delivery_device; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_delivery_device ON public.notification_delivery_attempts USING btree (device_registration_id);


--
-- Name: idx_notification_delivery_notification; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_delivery_notification ON public.notification_delivery_attempts USING btree (notification_id, created_at DESC);


--
-- Name: idx_notification_delivery_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_delivery_status ON public.notification_delivery_attempts USING btree (tenant_id, status, created_at DESC);


--
-- Name: idx_notification_devices_stale; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_devices_stale ON public.notification_device_registrations USING btree (enabled, last_seen_at, token_updated_at);


--
-- Name: idx_notification_devices_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_devices_user_active ON public.notification_device_registrations USING btree (tenant_id, user_id, portal, enabled, last_seen_at DESC);


--
-- Name: idx_notification_messages_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_messages_created_by ON public.notification_messages USING btree (created_by);


--
-- Name: idx_notification_messages_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_messages_source ON public.notification_messages USING btree (tenant_id, source_type, source_id);


--
-- Name: idx_notification_messages_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_messages_tenant_created ON public.notification_messages USING btree (tenant_id, created_at DESC);


--
-- Name: idx_notification_outbox_notification; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_outbox_notification ON public.notification_outbox USING btree (notification_id);


--
-- Name: idx_notification_outbox_pickup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_outbox_pickup ON public.notification_outbox USING btree (status, next_attempt_at, created_at);


--
-- Name: idx_notification_preferences_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_preferences_user ON public.notification_preferences USING btree (tenant_id, user_id, portal);


--
-- Name: idx_notification_recipients_inbox; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_recipients_inbox ON public.notification_recipients USING btree (tenant_id, user_id, portal, status, created_at DESC);


--
-- Name: idx_notification_recipients_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_recipients_unread ON public.notification_recipients USING btree (tenant_id, user_id, portal, status);


--
-- Name: idx_notification_topic_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_topic_name ON public.notification_topic_memberships USING btree (tenant_id, topic_name, membership_status);


--
-- Name: idx_permission_bindings_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_bindings_permission ON public.permission_bindings USING btree (permission_id, enforced);


--
-- Name: idx_permissions_catalog_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permissions_catalog_lookup ON public.permissions USING btree (portal, group_name, status, name);


--
-- Name: idx_profiles_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_user_id ON public.profiles USING btree (user_id);


--
-- Name: idx_question_bank_categories_tenant_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_question_bank_categories_tenant_scope ON public.question_bank_categories USING btree (tenant_id, subject_id, level_id);


--
-- Name: idx_question_bank_levels_tenant_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_question_bank_levels_tenant_subject ON public.question_bank_levels USING btree (tenant_id, subject_id, order_index);


--
-- Name: idx_question_bank_subjects_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_question_bank_subjects_tenant ON public.question_bank_subjects USING btree (tenant_id, label);


--
-- Name: idx_question_types_runtime_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_question_types_runtime_kind ON public.question_types USING btree (runtime_kind);


--
-- Name: idx_quiz_bank_items_questions; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_bank_items_questions ON public.quiz_bank_items USING gin (question_ids jsonb_path_ops);


--
-- Name: idx_quiz_bank_items_tenant_subject_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_bank_items_tenant_subject_level ON public.quiz_bank_items USING btree (tenant_id, subject_id, level_id);


--
-- Name: idx_quiz_drafts_tenant_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_drafts_tenant_quiz ON public.quiz_drafts USING btree (tenant_id, quiz_id);


--
-- Name: idx_quiz_drafts_tree_paths; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_drafts_tree_paths ON public.quiz_drafts USING gin (tree jsonb_path_ops);


--
-- Name: idx_quiz_packages_quiz_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_packages_quiz_version ON public.quiz_packages USING btree (quiz_id, quiz_version);


--
-- Name: idx_quiz_packages_tenant_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_packages_tenant_quiz ON public.quiz_packages USING btree (tenant_id, quiz_id);


--
-- Name: idx_quiz_version_sections_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_version_sections_version ON public.quiz_version_sections USING btree (quiz_version_id, order_index);


--
-- Name: idx_quiz_versions_tenant_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quiz_versions_tenant_quiz ON public.quiz_versions USING btree (tenant_id, quiz_id, version_number DESC);


--
-- Name: idx_qvq_bank_question; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qvq_bank_question ON public.quiz_version_questions USING btree (bank_question_id) WHERE (bank_question_id IS NOT NULL);


--
-- Name: idx_qvq_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qvq_kind ON public.quiz_version_questions USING btree (kind);


--
-- Name: idx_qvq_version_autogradable; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qvq_version_autogradable ON public.quiz_version_questions USING btree (quiz_version_id, is_auto_gradable);


--
-- Name: idx_qvq_version_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qvq_version_order ON public.quiz_version_questions USING btree (quiz_version_id, order_index);


--
-- Name: idx_roles_updated_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_roles_updated_by ON public.roles USING btree (updated_by);


--
-- Name: idx_school_grades_tenant_school_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_grades_tenant_school_year ON public.school_grades USING btree (tenant_id, school_id, academic_year_id, status, sort_order);


--
-- Name: idx_school_year_tenant_school_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_year_tenant_school_status ON public.school_academic_years USING btree (tenant_id, school_id, status, start_year DESC);


--
-- Name: idx_security_events_tenant_session_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_tenant_session_time ON public.security_events USING btree (tenant_id, session_id, occurred_at DESC);


--
-- Name: idx_security_events_tenant_user_risk_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_tenant_user_risk_time ON public.security_events USING btree (tenant_id, user_id, risk_code, occurred_at DESC);


--
-- Name: idx_security_events_tenant_user_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_tenant_user_time ON public.security_events USING btree (tenant_id, user_id, occurred_at DESC);


--
-- Name: idx_security_events_type_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_type_time ON public.security_events USING btree (event_type, occurred_at DESC);


--
-- Name: idx_student_group_members_tenant_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_group_members_tenant_student ON public.student_group_members USING btree (tenant_id, student_id);


--
-- Name: idx_student_groups_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_groups_tenant ON public.student_groups USING btree (tenant_id, class_id);


--
-- Name: idx_student_progress_class_quiz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_progress_class_quiz ON public.student_progress USING btree (class_id, quiz_id);


--
-- Name: idx_student_progress_quiz_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_progress_quiz_status ON public.student_progress USING btree (quiz_id, status);


--
-- Name: idx_topic_content_items_content_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_topic_content_items_content_item ON public.topic_content_items USING btree (tenant_id, content_item_id);


--
-- Name: idx_topic_content_items_topic_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_topic_content_items_topic_sort ON public.topic_content_items USING btree (tenant_id, topic_id, status, sort_order);


--
-- Name: idx_tsb_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_created_by ON public.teaching_schedule_batches USING btree (created_by);


--
-- Name: idx_tsb_published_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_published_by ON public.teaching_schedule_batches USING btree (published_by);


--
-- Name: idx_tsb_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_school ON public.teaching_schedule_batches USING btree (school_id);


--
-- Name: idx_tsb_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_tenant_created ON public.teaching_schedule_batches USING btree (tenant_id, created_at DESC);


--
-- Name: idx_tsb_tenant_idempotency_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tsb_tenant_idempotency_key ON public.teaching_schedule_batches USING btree (tenant_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);


--
-- Name: idx_tsb_tenant_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_tenant_school ON public.teaching_schedule_batches USING btree (tenant_id, school_id);


--
-- Name: idx_tsb_tenant_school_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_tenant_school_status_created ON public.teaching_schedule_batches USING btree (tenant_id, school_id, status, created_at DESC);


--
-- Name: idx_tsb_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsb_tenant_status ON public.teaching_schedule_batches USING btree (tenant_id, status);


--
-- Name: idx_tsc_tenant_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsc_tenant_batch ON public.teaching_schedule_conflicts USING btree (tenant_id, batch_id);


--
-- Name: idx_tsc_tenant_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsc_tenant_event ON public.teaching_schedule_conflicts USING btree (tenant_id, event_id);


--
-- Name: idx_tsc_tenant_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsc_tenant_type ON public.teaching_schedule_conflicts USING btree (tenant_id, conflict_type);


--
-- Name: idx_tsc_tenant_unresolved; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsc_tenant_unresolved ON public.teaching_schedule_conflicts USING btree (tenant_id, resolved) WHERE (resolved = false);


--
-- Name: idx_tse_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_batch ON public.teaching_schedule_events USING btree (batch_id);


--
-- Name: idx_tse_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_created_by ON public.teaching_schedule_events USING btree (created_by);


--
-- Name: idx_tse_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_school ON public.teaching_schedule_events USING btree (school_id);


--
-- Name: idx_tse_school_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_school_room ON public.teaching_schedule_events USING btree (school_id, room_name) WHERE (room_name IS NOT NULL);


--
-- Name: idx_tse_tenant_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_batch ON public.teaching_schedule_events USING btree (tenant_id, batch_id);


--
-- Name: idx_tse_tenant_class_status_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_class_status_timerange ON public.teaching_schedule_events USING btree (tenant_id, academic_class_id, status, start_at, end_at);


--
-- Name: idx_tse_tenant_class_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_class_timerange ON public.teaching_schedule_events USING btree (tenant_id, academic_class_id, start_at, end_at);


--
-- Name: idx_tse_tenant_level_status_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_level_status_timerange ON public.teaching_schedule_events USING btree (tenant_id, level_id, status, start_at, end_at) WHERE (level_id IS NOT NULL);


--
-- Name: idx_tse_tenant_school_room_timerange_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_school_room_timerange_active ON public.teaching_schedule_events USING btree (tenant_id, school_id, lower((room_name)::text), start_at, end_at) WHERE ((room_name IS NOT NULL) AND ((status)::text <> 'CANCELLED'::text));


--
-- Name: idx_tse_tenant_school_status_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_school_status_timerange ON public.teaching_schedule_events USING btree (tenant_id, school_id, status, start_at, end_at);


--
-- Name: idx_tse_tenant_school_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_school_timerange ON public.teaching_schedule_events USING btree (tenant_id, school_id, start_at, end_at);


--
-- Name: idx_tse_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_status ON public.teaching_schedule_events USING btree (tenant_id, status);


--
-- Name: idx_tse_tenant_subject_status_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_subject_status_timerange ON public.teaching_schedule_events USING btree (tenant_id, subject_id, status, start_at, end_at);


--
-- Name: idx_tse_tenant_subject_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_subject_timerange ON public.teaching_schedule_events USING btree (tenant_id, subject_id, start_at, end_at);


--
-- Name: idx_tse_tenant_timerange; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_tenant_timerange ON public.teaching_schedule_events USING btree (tenant_id, start_at, end_at);


--
-- Name: idx_tse_updated_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tse_updated_by ON public.teaching_schedule_events USING btree (updated_by);


--
-- Name: idx_tset_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tset_event ON public.teaching_schedule_event_teachers USING btree (event_id);


--
-- Name: idx_tset_teacher; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tset_teacher ON public.teaching_schedule_event_teachers USING btree (tenant_id, teacher_user_id);


--
-- Name: idx_tset_teacher_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tset_teacher_user ON public.teaching_schedule_event_teachers USING btree (teacher_user_id);


--
-- Name: idx_tset_tenant_event_teacher; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tset_tenant_event_teacher ON public.teaching_schedule_event_teachers USING btree (tenant_id, event_id, teacher_user_id);


--
-- Name: idx_tset_tenant_teacher_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tset_tenant_teacher_event ON public.teaching_schedule_event_teachers USING btree (tenant_id, teacher_user_id, event_id);


--
-- Name: idx_tsr_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsr_created_by ON public.teaching_schedule_rooms USING btree (created_by);


--
-- Name: idx_tsr_tenant_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsr_tenant_school ON public.teaching_schedule_rooms USING btree (tenant_id, school_id);


--
-- Name: idx_tsr_tenant_school_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsr_tenant_school_status ON public.teaching_schedule_rooms USING btree (tenant_id, school_id, status);


--
-- Name: idx_user_access_scopes_education_unit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_access_scopes_education_unit ON public.user_access_scopes USING btree (education_unit_id);


--
-- Name: idx_user_access_scopes_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_access_scopes_user ON public.user_access_scopes USING btree (user_id);


--
-- Name: idx_user_access_scopes_user_education_unit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_access_scopes_user_education_unit ON public.user_access_scopes USING btree (user_id, education_unit_id);


--
-- Name: idx_user_activities_request_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_activities_request_id ON public.user_activities USING btree (request_id) WHERE (request_id IS NOT NULL);


--
-- Name: idx_user_activities_target_action_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_activities_target_action_time ON public.user_activities USING btree (tenant_id, target_user_id, action, created_at DESC);


--
-- Name: idx_user_activities_target_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_activities_target_user_id ON public.user_activities USING btree (target_user_id);


--
-- Name: idx_user_activities_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_activities_user_id ON public.user_activities USING btree (user_id);


--
-- Name: idx_user_permission_overrides_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_permission_overrides_user_id ON public.user_permissions USING btree (user_id);


--
-- Name: idx_user_permissions_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_permissions_created_by ON public.user_permissions USING btree (created_by);


--
-- Name: idx_user_permissions_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_permissions_permission ON public.user_permissions USING btree (permission);


--
-- Name: idx_user_permissions_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_permissions_user_id ON public.user_permissions USING btree (user_id);


--
-- Name: idx_user_role_assignments_effective; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_role_assignments_effective ON public.user_role_assignments USING btree (tenant_id, user_id, scope_type, scope_id, expires_at) WHERE (revoked_at IS NULL);


--
-- Name: idx_user_role_assignments_role_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_role_assignments_role_active ON public.user_role_assignments USING btree (tenant_id, role_id, user_id) WHERE (revoked_at IS NULL);


--
-- Name: idx_user_sessions_access_token_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_access_token_tenant ON public.user_sessions USING btree (access_token_hash, tenant_id);


--
-- Name: idx_user_sessions_active_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_active_expiry ON public.user_sessions USING btree (user_id, tenant_id, revoked_at, expires_at);


--
-- Name: idx_user_sessions_platform_slot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_platform_slot ON public.user_sessions USING btree (user_id, tenant_id, platform, revoked_at);


--
-- Name: idx_user_sessions_refresh_token_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_refresh_token_tenant ON public.user_sessions USING btree (refresh_token_hash, tenant_id);


--
-- Name: idx_user_sessions_session_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_session_tenant ON public.user_sessions USING btree (session_id, tenant_id);


--
-- Name: idx_user_sessions_user_tenant_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_user_tenant_active ON public.user_sessions USING btree (user_id, tenant_id, revoked_at);


--
-- Name: idx_user_sessions_user_tenant_device_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_user_tenant_device_active ON public.user_sessions USING btree (user_id, tenant_id, device_fingerprint, device_id, revoked_at);


--
-- Name: idx_users_lower_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_lower_email ON public.users USING btree (lower((email)::text));


--
-- Name: idx_users_lower_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_lower_username ON public.users USING btree (lower((username)::text));


--
-- Name: idx_users_tenant_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_tenant_created_at ON public.users USING btree (tenant_id, created_at DESC);


--
-- Name: idx_users_tenant_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_tenant_id_id ON public.users USING btree (tenant_id, id);


--
-- Name: idx_work_shifts_confirmed_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_shifts_confirmed_by ON public.work_shifts USING btree (confirmed_by);


--
-- Name: idx_work_shifts_school_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_shifts_school_id ON public.work_shifts USING btree (school_id);


--
-- Name: idx_work_shifts_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_shifts_user_id ON public.work_shifts USING btree (user_id);


--
-- Name: uk_education_units_tenant_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_education_units_tenant_slug ON public.education_units USING btree (tenant_id, lower((slug)::text)) WHERE (slug IS NOT NULL);


--
-- Name: uk_profiles_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_profiles_user_id ON public.profiles USING btree (user_id);


--
-- Name: uk_school_year_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_school_year_current ON public.school_academic_years USING btree (tenant_id, school_id) WHERE ((status)::text = 'CURRENT'::text);


--
-- Name: uq_account_verification_codes_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_account_verification_codes_active ON public.account_verification_codes USING btree (user_id, purpose) WHERE (consumed_at IS NULL);


--
-- Name: uq_assignment_recipients_assignment_student; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_assignment_recipients_assignment_student ON public.assignment_recipients USING btree (assignment_id, student_id);


--
-- Name: uq_lms_attempts_start_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_lms_attempts_start_idempotency ON public.lms_attempts USING btree (tenant_id, assignment_id, quiz_id, student_id, start_idempotency_key) WHERE (start_idempotency_key IS NOT NULL);


--
-- Name: uq_lms_quiz_questions_quiz_question; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_lms_quiz_questions_quiz_question ON public.lms_quiz_questions USING btree (tenant_id, quiz_id, question_id);


--
-- Name: uq_media_assets_tenant_sha256; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_media_assets_tenant_sha256 ON public.media_assets USING btree (tenant_id, sha256);


--
-- Name: uq_media_references_media_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_media_references_media_owner ON public.media_references USING btree (media_id, owner_type, owner_id);


--
-- Name: uq_notification_devices_fid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_devices_fid ON public.notification_device_registrations USING btree (tenant_id, firebase_installation_id);


--
-- Name: uq_notification_devices_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_devices_token_hash ON public.notification_device_registrations USING btree (tenant_id, registration_token_hash);


--
-- Name: uq_notification_messages_tenant_dedupe; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_messages_tenant_dedupe ON public.notification_messages USING btree (tenant_id, dedupe_key);


--
-- Name: uq_notification_preferences_user_event; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_preferences_user_event ON public.notification_preferences USING btree (tenant_id, user_id, portal, event_type);


--
-- Name: uq_notification_recipients_message_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_recipients_message_user ON public.notification_recipients USING btree (notification_id, user_id, portal);


--
-- Name: uq_notification_retention_config_table; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_retention_config_table ON public.notification_retention_config USING btree (table_name);


--
-- Name: uq_notification_topic_membership; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_topic_membership ON public.notification_topic_memberships USING btree (device_registration_id, topic_name);


--
-- Name: uq_quiz_packages_version; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_quiz_packages_version ON public.quiz_packages USING btree (quiz_version_id);


--
-- Name: uq_quiz_versions_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_quiz_versions_active ON public.quiz_versions USING btree (quiz_id) WHERE is_active;


--
-- Name: uq_quiz_versions_quiz_label; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_quiz_versions_quiz_label ON public.quiz_versions USING btree (quiz_id, version_label);


--
-- Name: uq_quiz_versions_quiz_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_quiz_versions_quiz_number ON public.quiz_versions USING btree (quiz_id, version_number);


--
-- Name: uq_student_progress_attempt_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_student_progress_attempt_scope ON public.student_progress USING btree (tenant_id, student_user_id, quiz_id, assignment_id);


--
-- Name: uq_user_role_assignments_active_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_user_role_assignments_active_scope ON public.user_role_assignments USING btree (user_id, role_id, tenant_id, scope_type, COALESCE(scope_id, ''::character varying)) WHERE (revoked_at IS NULL);


--
-- Name: uq_users_recovery_email_normalized; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_recovery_email_normalized ON public.users USING btree (lower((recovery_email)::text)) WHERE (recovery_email IS NOT NULL);


--
-- Name: uq_users_username_normalized; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_username_normalized ON public.users USING btree (lower((username)::text)) WHERE (username IS NOT NULL);


--
-- Name: ux_permission_bindings_surface; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_permission_bindings_surface ON public.permission_bindings USING btree (permission_id, surface_type, surface_identifier);


--
-- Name: ux_roles_name_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_roles_name_lower ON public.roles USING btree (lower((name)::text)) WHERE (name IS NOT NULL);


--
-- Name: academic_classes fk_academic_classes_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_classes
    ADD CONSTRAINT fk_academic_classes_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: academic_classes fk_academic_classes_teacher; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_classes
    ADD CONSTRAINT fk_academic_classes_teacher FOREIGN KEY (homeroom_teacher_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: assignment_recipients fk_assignment_recipients_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_assignment FOREIGN KEY (assignment_id) REFERENCES public.lms_assignments(id) ON DELETE CASCADE;


--
-- Name: assignment_recipients fk_assignment_recipients_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_class FOREIGN KEY (class_id) REFERENCES public.academic_classes(id) ON DELETE SET NULL;


--
-- Name: assignment_recipients fk_assignment_recipients_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_student FOREIGN KEY (student_id) REFERENCES public.lms_students(id) ON DELETE CASCADE;


--
-- Name: assignment_recipients fk_assignment_recipients_student_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_student_user FOREIGN KEY (student_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: assignment_resources fk_assignment_resources_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_resources
    ADD CONSTRAINT fk_assignment_resources_assignment FOREIGN KEY (assignment_id) REFERENCES public.lms_assignments(id) ON DELETE CASCADE;


--
-- Name: assignment_resources fk_assignment_resources_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_resources
    ADD CONSTRAINT fk_assignment_resources_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE RESTRICT;


--
-- Name: assignment_resources fk_assignment_resources_quiz_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_resources
    ADD CONSTRAINT fk_assignment_resources_quiz_version FOREIGN KEY (quiz_version_id) REFERENCES public.quiz_versions(id) ON DELETE SET NULL;


--
-- Name: attempt_answers fk_attempt_answers_attempt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_answers
    ADD CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES public.lms_attempts(id) ON DELETE CASCADE;


--
-- Name: attempt_answers fk_attempt_answers_version_question; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_answers
    ADD CONSTRAINT fk_attempt_answers_version_question FOREIGN KEY (quiz_version_question_id) REFERENCES public.quiz_version_questions(id) ON DELETE CASCADE;


--
-- Name: attempt_events fk_attempt_events_attempt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_events
    ADD CONSTRAINT fk_attempt_events_attempt FOREIGN KEY (attempt_id) REFERENCES public.lms_attempts(id) ON DELETE CASCADE;


--
-- Name: attempt_events fk_attempt_events_package; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_events
    ADD CONSTRAINT fk_attempt_events_package FOREIGN KEY (package_id) REFERENCES public.quiz_packages(id) ON DELETE SET NULL;


--
-- Name: attempt_events fk_attempt_events_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attempt_events
    ADD CONSTRAINT fk_attempt_events_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE SET NULL;


--
-- Name: auth_login_attempts fk_auth_login_attempts_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_attempts
    ADD CONSTRAINT fk_auth_login_attempts_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: certificates fk_certificates_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.certificates
    ADD CONSTRAINT fk_certificates_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: content_item_assets fk_content_item_assets_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_item_assets
    ADD CONSTRAINT fk_content_item_assets_asset FOREIGN KEY (content_asset_id) REFERENCES public.content_assets(id) ON DELETE RESTRICT;


--
-- Name: content_item_assets fk_content_item_assets_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_item_assets
    ADD CONSTRAINT fk_content_item_assets_item FOREIGN KEY (content_item_id) REFERENCES public.content_items(id) ON DELETE RESTRICT;


--
-- Name: crm_referrals fk_crm_referrals_referrer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_referrals
    ADD CONSTRAINT fk_crm_referrals_referrer FOREIGN KEY (referrer_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: crm_referrals fk_crm_referrals_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_referrals
    ADD CONSTRAINT fk_crm_referrals_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE CASCADE;


--
-- Name: crm_school_activities fk_crm_school_activities_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_activities
    ADD CONSTRAINT fk_crm_school_activities_actor FOREIGN KEY (actor_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: crm_school_activities fk_crm_school_activities_deal; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_activities
    ADD CONSTRAINT fk_crm_school_activities_deal FOREIGN KEY (deal_id) REFERENCES public.crm_school_deals(id) ON DELETE SET NULL;


--
-- Name: crm_school_activities fk_crm_school_activities_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_activities
    ADD CONSTRAINT fk_crm_school_activities_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE CASCADE;


--
-- Name: crm_school_contacts fk_crm_school_contacts_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_contacts
    ADD CONSTRAINT fk_crm_school_contacts_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE CASCADE;


--
-- Name: crm_school_deals fk_crm_school_deals_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_deals
    ADD CONSTRAINT fk_crm_school_deals_owner FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: crm_school_deals fk_crm_school_deals_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_deals
    ADD CONSTRAINT fk_crm_school_deals_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE CASCADE;


--
-- Name: crm_school_profiles fk_crm_school_profiles_introduced_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_introduced_by FOREIGN KEY (introduced_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: crm_school_profiles fk_crm_school_profiles_lead_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_lead_owner FOREIGN KEY (lead_owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: crm_school_profiles fk_crm_school_profiles_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_manager FOREIGN KEY (manager_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: crm_school_profiles fk_crm_school_profiles_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_owner FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: crm_school_profiles fk_crm_school_profiles_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE CASCADE;


--
-- Name: curriculum_levels fk_curriculum_levels_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_levels
    ADD CONSTRAINT fk_curriculum_levels_subject FOREIGN KEY (subject_id) REFERENCES public.curriculum_subjects(id) ON DELETE RESTRICT;


--
-- Name: curriculum_topics fk_curriculum_topics_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_topics
    ADD CONSTRAINT fk_curriculum_topics_level FOREIGN KEY (level_id) REFERENCES public.curriculum_levels(id) ON DELETE RESTRICT;


--
-- Name: education_units fk_education_units_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.education_units
    ADD CONSTRAINT fk_education_units_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: education_units fk_education_units_owner_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.education_units
    ADD CONSTRAINT fk_education_units_owner_user FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: education_units fk_education_units_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.education_units
    ADD CONSTRAINT fk_education_units_parent FOREIGN KEY (parent_id) REFERENCES public.education_units(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: employee_profiles fk_employee_profiles_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_profiles
    ADD CONSTRAINT fk_employee_profiles_manager FOREIGN KEY (manager_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: employee_profiles fk_employee_profiles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_profiles
    ADD CONSTRAINT fk_employee_profiles_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: lms_assignments fk_lms_assignments_assigned_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_assignments
    ADD CONSTRAINT fk_lms_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: lms_assignments fk_lms_assignments_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_assignments
    ADD CONSTRAINT fk_lms_assignments_class FOREIGN KEY (academic_class_id) REFERENCES public.academic_classes(id) ON DELETE RESTRICT;


--
-- Name: lms_assignments fk_lms_assignments_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_assignments
    ADD CONSTRAINT fk_lms_assignments_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE RESTRICT;


--
-- Name: lms_assignments fk_lms_assignments_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_assignments
    ADD CONSTRAINT fk_lms_assignments_subject FOREIGN KEY (subject_id) REFERENCES public.lms_subjects(id) ON DELETE RESTRICT;


--
-- Name: lms_attempts fk_lms_attempts_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT fk_lms_attempts_assignment FOREIGN KEY (assignment_id) REFERENCES public.lms_assignments(id) ON DELETE RESTRICT;


--
-- Name: lms_attempts fk_lms_attempts_class_context; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT fk_lms_attempts_class_context FOREIGN KEY (class_id) REFERENCES public.academic_classes(id) ON DELETE SET NULL;


--
-- Name: lms_attempts fk_lms_attempts_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT fk_lms_attempts_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE RESTRICT;


--
-- Name: lms_attempts fk_lms_attempts_quiz_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT fk_lms_attempts_quiz_version FOREIGN KEY (quiz_version_id) REFERENCES public.quiz_versions(id) ON DELETE SET NULL;


--
-- Name: lms_attempts fk_lms_attempts_school_context; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT fk_lms_attempts_school_context FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE SET NULL;


--
-- Name: lms_attempts fk_lms_attempts_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_attempts
    ADD CONSTRAINT fk_lms_attempts_student FOREIGN KEY (student_id) REFERENCES public.lms_students(id) ON DELETE RESTRICT;


--
-- Name: lms_levels fk_lms_levels_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_levels
    ADD CONSTRAINT fk_lms_levels_subject FOREIGN KEY (subject_id) REFERENCES public.lms_subjects(id) ON DELETE RESTRICT;


--
-- Name: lms_questions fk_lms_questions_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT fk_lms_questions_category FOREIGN KEY (category_id) REFERENCES public.curriculum_topics(id) ON DELETE RESTRICT;


--
-- Name: lms_questions fk_lms_questions_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT fk_lms_questions_level FOREIGN KEY (level_id) REFERENCES public.curriculum_levels(id) ON DELETE RESTRICT;


--
-- Name: lms_questions fk_lms_questions_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT fk_lms_questions_owner FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: lms_questions fk_lms_questions_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT fk_lms_questions_subject FOREIGN KEY (subject_id) REFERENCES public.curriculum_subjects(id) ON DELETE RESTRICT;


--
-- Name: lms_questions fk_lms_questions_topic; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT fk_lms_questions_topic FOREIGN KEY (topic_id) REFERENCES public.curriculum_topics(id) ON DELETE RESTRICT;


--
-- Name: lms_questions fk_lms_questions_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_questions
    ADD CONSTRAINT fk_lms_questions_type FOREIGN KEY (type) REFERENCES public.question_types(id) ON DELETE RESTRICT;


--
-- Name: lms_quiz_questions fk_lms_quiz_questions_question; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quiz_questions
    ADD CONSTRAINT fk_lms_quiz_questions_question FOREIGN KEY (question_id) REFERENCES public.lms_questions(id) ON DELETE RESTRICT;


--
-- Name: lms_quiz_questions fk_lms_quiz_questions_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quiz_questions
    ADD CONSTRAINT fk_lms_quiz_questions_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE CASCADE;


--
-- Name: lms_quizzes fk_lms_quizzes_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_category FOREIGN KEY (category_id) REFERENCES public.curriculum_topics(id) ON DELETE RESTRICT;


--
-- Name: lms_quizzes fk_lms_quizzes_current_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_current_version FOREIGN KEY (current_version_id) REFERENCES public.quiz_versions(id) ON DELETE SET NULL;


--
-- Name: lms_quizzes fk_lms_quizzes_latest_draft; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_latest_draft FOREIGN KEY (latest_draft_id) REFERENCES public.quiz_drafts(id) ON DELETE SET NULL;


--
-- Name: lms_quizzes fk_lms_quizzes_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_level FOREIGN KEY (level_id) REFERENCES public.curriculum_levels(id) ON DELETE RESTRICT;


--
-- Name: lms_quizzes fk_lms_quizzes_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_owner FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: lms_quizzes fk_lms_quizzes_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_subject FOREIGN KEY (subject_id) REFERENCES public.curriculum_subjects(id) ON DELETE RESTRICT;


--
-- Name: lms_students fk_lms_students_auth_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_students
    ADD CONSTRAINT fk_lms_students_auth_user FOREIGN KEY (auth_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: lms_students fk_lms_students_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_students
    ADD CONSTRAINT fk_lms_students_class FOREIGN KEY (academic_class_id) REFERENCES public.academic_classes(id) ON DELETE RESTRICT;


--
-- Name: lms_students fk_lms_students_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_students
    ADD CONSTRAINT fk_lms_students_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: lms_topics fk_lms_topics_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lms_topics
    ADD CONSTRAINT fk_lms_topics_level FOREIGN KEY (level_id) REFERENCES public.lms_levels(id) ON DELETE RESTRICT;


--
-- Name: notification_delivery_attempts fk_notification_delivery_device; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT fk_notification_delivery_device FOREIGN KEY (device_registration_id) REFERENCES public.notification_device_registrations(id) ON DELETE SET NULL;


--
-- Name: notification_delivery_attempts fk_notification_delivery_notification; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id) REFERENCES public.notification_messages(id) ON DELETE CASCADE;


--
-- Name: notification_device_registrations fk_notification_devices_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_device_registrations
    ADD CONSTRAINT fk_notification_devices_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notification_messages fk_notification_messages_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_messages
    ADD CONSTRAINT fk_notification_messages_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: notification_preferences fk_notification_preferences_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notification_recipients fk_notification_recipients_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT fk_notification_recipients_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: permission_bindings fk_permission_bindings_permission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT fk_permission_bindings_permission FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: permissions fk_permissions_group_name; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT fk_permissions_group_name FOREIGN KEY (group_name) REFERENCES public.permission_groups(name) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: profiles fk_profiles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: question_bank_categories fk_question_bank_categories_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_bank_categories
    ADD CONSTRAINT fk_question_bank_categories_level FOREIGN KEY (level_id) REFERENCES public.question_bank_levels(id) ON DELETE CASCADE;


--
-- Name: question_bank_categories fk_question_bank_categories_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_bank_categories
    ADD CONSTRAINT fk_question_bank_categories_subject FOREIGN KEY (subject_id) REFERENCES public.question_bank_subjects(id) ON DELETE CASCADE;


--
-- Name: question_bank_levels fk_question_bank_levels_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_bank_levels
    ADD CONSTRAINT fk_question_bank_levels_subject FOREIGN KEY (subject_id) REFERENCES public.question_bank_subjects(id) ON DELETE CASCADE;


--
-- Name: quiz_bank_items fk_quiz_bank_items_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_bank_items
    ADD CONSTRAINT fk_quiz_bank_items_level FOREIGN KEY (level_id) REFERENCES public.question_bank_levels(id) ON DELETE SET NULL;


--
-- Name: quiz_bank_items fk_quiz_bank_items_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_bank_items
    ADD CONSTRAINT fk_quiz_bank_items_owner FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: quiz_bank_items fk_quiz_bank_items_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_bank_items
    ADD CONSTRAINT fk_quiz_bank_items_subject FOREIGN KEY (subject_id) REFERENCES public.question_bank_subjects(id) ON DELETE SET NULL;


--
-- Name: quiz_drafts fk_quiz_drafts_base_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_drafts
    ADD CONSTRAINT fk_quiz_drafts_base_version FOREIGN KEY (base_version_id) REFERENCES public.quiz_versions(id) ON DELETE SET NULL;


--
-- Name: quiz_drafts fk_quiz_drafts_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_drafts
    ADD CONSTRAINT fk_quiz_drafts_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE CASCADE;


--
-- Name: quiz_drafts fk_quiz_drafts_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_drafts
    ADD CONSTRAINT fk_quiz_drafts_updated_by FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: quiz_packages fk_quiz_packages_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_packages
    ADD CONSTRAINT fk_quiz_packages_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE CASCADE;


--
-- Name: quiz_packages fk_quiz_packages_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_packages
    ADD CONSTRAINT fk_quiz_packages_version FOREIGN KEY (quiz_version_id) REFERENCES public.quiz_versions(id) ON DELETE CASCADE;


--
-- Name: quiz_version_questions fk_quiz_version_questions_bank_question; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_bank_question FOREIGN KEY (bank_question_id) REFERENCES public.lms_questions(id) ON DELETE SET NULL;


--
-- Name: quiz_version_questions fk_quiz_version_questions_section; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_section FOREIGN KEY (section_id) REFERENCES public.quiz_version_sections(id) ON DELETE SET NULL;


--
-- Name: quiz_version_questions fk_quiz_version_questions_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_type FOREIGN KEY (kind) REFERENCES public.question_types(id) ON DELETE RESTRICT;


--
-- Name: quiz_version_questions fk_quiz_version_questions_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_version FOREIGN KEY (quiz_version_id) REFERENCES public.quiz_versions(id) ON DELETE CASCADE;


--
-- Name: quiz_version_sections fk_quiz_version_sections_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_version_sections
    ADD CONSTRAINT fk_quiz_version_sections_version FOREIGN KEY (quiz_version_id) REFERENCES public.quiz_versions(id) ON DELETE CASCADE;


--
-- Name: quiz_versions fk_quiz_versions_published_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_versions
    ADD CONSTRAINT fk_quiz_versions_published_by FOREIGN KEY (published_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: quiz_versions fk_quiz_versions_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_versions
    ADD CONSTRAINT fk_quiz_versions_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE CASCADE;


--
-- Name: role_permissions fk_role_permissions_permission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;


--
-- Name: role_permissions fk_role_permissions_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: school_grades fk_school_grade_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_grades
    ADD CONSTRAINT fk_school_grade_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: school_grades fk_school_grade_year; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_grades
    ADD CONSTRAINT fk_school_grade_year FOREIGN KEY (academic_year_id) REFERENCES public.school_academic_years(id) ON DELETE RESTRICT;


--
-- Name: school_operational_profiles fk_school_profile_current_year; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_operational_profiles
    ADD CONSTRAINT fk_school_profile_current_year FOREIGN KEY (current_academic_year_id) REFERENCES public.school_academic_years(id) ON DELETE SET NULL;


--
-- Name: school_operational_profiles fk_school_profile_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_operational_profiles
    ADD CONSTRAINT fk_school_profile_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: school_academic_years fk_school_year_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_academic_years
    ADD CONSTRAINT fk_school_year_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: student_group_members fk_student_group_members_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_group_members
    ADD CONSTRAINT fk_student_group_members_group FOREIGN KEY (group_id) REFERENCES public.student_groups(id) ON DELETE CASCADE;


--
-- Name: student_group_members fk_student_group_members_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_group_members
    ADD CONSTRAINT fk_student_group_members_student FOREIGN KEY (student_id) REFERENCES public.lms_students(id) ON DELETE CASCADE;


--
-- Name: student_groups fk_student_groups_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_groups
    ADD CONSTRAINT fk_student_groups_class FOREIGN KEY (class_id) REFERENCES public.academic_classes(id) ON DELETE CASCADE;


--
-- Name: student_groups fk_student_groups_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_groups
    ADD CONSTRAINT fk_student_groups_owner FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: student_progress fk_student_progress_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT fk_student_progress_assignment FOREIGN KEY (assignment_id) REFERENCES public.lms_assignments(id) ON DELETE SET NULL;


--
-- Name: student_progress fk_student_progress_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT fk_student_progress_class FOREIGN KEY (class_id) REFERENCES public.academic_classes(id) ON DELETE SET NULL;


--
-- Name: student_progress fk_student_progress_quiz; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT fk_student_progress_quiz FOREIGN KEY (quiz_id) REFERENCES public.lms_quizzes(id) ON DELETE CASCADE;


--
-- Name: student_progress fk_student_progress_student_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_progress
    ADD CONSTRAINT fk_student_progress_student_user FOREIGN KEY (student_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: topic_content_items fk_topic_content_items_content_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_content_items
    ADD CONSTRAINT fk_topic_content_items_content_item FOREIGN KEY (content_item_id) REFERENCES public.content_items(id) ON DELETE RESTRICT;


--
-- Name: topic_content_items fk_topic_content_items_topic; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_content_items
    ADD CONSTRAINT fk_topic_content_items_topic FOREIGN KEY (topic_id) REFERENCES public.curriculum_topics(id) ON DELETE RESTRICT;


--
-- Name: teaching_schedule_batches fk_tsb_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_batches
    ADD CONSTRAINT fk_tsb_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: teaching_schedule_batches fk_tsb_published_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_batches
    ADD CONSTRAINT fk_tsb_published_by FOREIGN KEY (published_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: teaching_schedule_batches fk_tsb_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_batches
    ADD CONSTRAINT fk_tsb_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: teaching_schedule_events fk_tse_batch; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_batch FOREIGN KEY (batch_id) REFERENCES public.teaching_schedule_batches(id) ON DELETE CASCADE;


--
-- Name: teaching_schedule_events fk_tse_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_class FOREIGN KEY (academic_class_id) REFERENCES public.academic_classes(id) ON DELETE RESTRICT;


--
-- Name: teaching_schedule_events fk_tse_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: teaching_schedule_events fk_tse_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_level FOREIGN KEY (level_id) REFERENCES public.lms_levels(id) ON DELETE RESTRICT;


--
-- Name: teaching_schedule_events fk_tse_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: teaching_schedule_events fk_tse_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_subject FOREIGN KEY (subject_id) REFERENCES public.lms_subjects(id) ON DELETE RESTRICT;


--
-- Name: teaching_schedule_events fk_tse_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_events
    ADD CONSTRAINT fk_tse_updated_by FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: teaching_schedule_event_teachers fk_tset_event; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_event_teachers
    ADD CONSTRAINT fk_tset_event FOREIGN KEY (event_id) REFERENCES public.teaching_schedule_events(id) ON DELETE CASCADE;


--
-- Name: teaching_schedule_event_teachers fk_tset_teacher_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_event_teachers
    ADD CONSTRAINT fk_tset_teacher_user FOREIGN KEY (teacher_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: teaching_schedule_rooms fk_tsr_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_rooms
    ADD CONSTRAINT fk_tsr_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: teaching_schedule_rooms fk_tsr_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_schedule_rooms
    ADD CONSTRAINT fk_tsr_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE RESTRICT;


--
-- Name: user_access_scopes fk_user_access_scopes_unit; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_access_scopes
    ADD CONSTRAINT fk_user_access_scopes_unit FOREIGN KEY (education_unit_id) REFERENCES public.education_units(id) ON DELETE CASCADE;


--
-- Name: user_access_scopes fk_user_access_scopes_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_access_scopes
    ADD CONSTRAINT fk_user_access_scopes_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_activities fk_user_activities_target_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_activities
    ADD CONSTRAINT fk_user_activities_target_user FOREIGN KEY (target_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: user_activities fk_user_activities_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_activities
    ADD CONSTRAINT fk_user_activities_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_permissions fk_user_permissions_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fk_user_permissions_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: user_permissions fk_user_permissions_permission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission) REFERENCES public.permissions(name) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: user_permissions fk_user_permissions_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_role_assignments fk_user_role_assignments_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT fk_user_role_assignments_actor FOREIGN KEY (assigned_by) REFERENCES public.users(id);


--
-- Name: user_role_assignments fk_user_role_assignments_revoker; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT fk_user_role_assignments_revoker FOREIGN KEY (revoked_by) REFERENCES public.users(id);


--
-- Name: user_role_assignments fk_user_role_assignments_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT fk_user_role_assignments_role FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: user_role_assignments fk_user_role_assignments_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT fk_user_role_assignments_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_roles fk_user_roles_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: user_roles fk_user_roles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_sessions fk_user_sessions_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: work_shifts fk_work_shifts_confirmed_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT fk_work_shifts_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: work_shifts fk_work_shifts_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT fk_work_shifts_school FOREIGN KEY (school_id) REFERENCES public.education_units(id) ON DELETE SET NULL;


--
-- Name: work_shifts fk_work_shifts_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT fk_work_shifts_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notification_delivery_attempts notification_delivery_attempts_outbox_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT notification_delivery_attempts_outbox_id_fkey FOREIGN KEY (outbox_id) REFERENCES public.notification_outbox(id) ON DELETE SET NULL;


--
-- Name: notification_outbox notification_outbox_notification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_outbox
    ADD CONSTRAINT notification_outbox_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES public.notification_messages(id) ON DELETE CASCADE;


--
-- Name: notification_recipients notification_recipients_notification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT notification_recipients_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES public.notification_messages(id) ON DELETE CASCADE;


--
-- Name: notification_topic_memberships notification_topic_memberships_device_registration_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_topic_memberships
    ADD CONSTRAINT notification_topic_memberships_device_registration_id_fkey FOREIGN KEY (device_registration_id) REFERENCES public.notification_device_registrations(id) ON DELETE CASCADE;


--
-- Name: assignment_recipients; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assignment_recipients ENABLE ROW LEVEL SECURITY;

--
-- Name: assignment_recipients assignment_recipients_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY assignment_recipients_tenant_isolation ON public.assignment_recipients USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: assignment_resources; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assignment_resources ENABLE ROW LEVEL SECURITY;

--
-- Name: assignment_resources assignment_resources_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY assignment_resources_tenant_isolation ON public.assignment_resources USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: attempt_answers; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.attempt_answers ENABLE ROW LEVEL SECURITY;

--
-- Name: attempt_answers attempt_answers_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY attempt_answers_tenant_isolation ON public.attempt_answers USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: attempt_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.attempt_events ENABLE ROW LEVEL SECURITY;

--
-- Name: attempt_events attempt_events_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY attempt_events_tenant_isolation ON public.attempt_events USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: media_assets; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.media_assets ENABLE ROW LEVEL SECURITY;

--
-- Name: media_assets media_assets_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY media_assets_tenant_isolation ON public.media_assets USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: media_references; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.media_references ENABLE ROW LEVEL SECURITY;

--
-- Name: media_references media_references_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY media_references_tenant_isolation ON public.media_references USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: question_bank_categories; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.question_bank_categories ENABLE ROW LEVEL SECURITY;

--
-- Name: question_bank_categories question_bank_categories_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY question_bank_categories_tenant_isolation ON public.question_bank_categories USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: question_bank_levels; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.question_bank_levels ENABLE ROW LEVEL SECURITY;

--
-- Name: question_bank_levels question_bank_levels_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY question_bank_levels_tenant_isolation ON public.question_bank_levels USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: question_bank_subjects; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.question_bank_subjects ENABLE ROW LEVEL SECURITY;

--
-- Name: question_bank_subjects question_bank_subjects_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY question_bank_subjects_tenant_isolation ON public.question_bank_subjects USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: quiz_bank_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.quiz_bank_items ENABLE ROW LEVEL SECURITY;

--
-- Name: quiz_bank_items quiz_bank_items_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY quiz_bank_items_tenant_isolation ON public.quiz_bank_items USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: quiz_drafts; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.quiz_drafts ENABLE ROW LEVEL SECURITY;

--
-- Name: quiz_drafts quiz_drafts_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY quiz_drafts_tenant_isolation ON public.quiz_drafts USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: quiz_packages; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.quiz_packages ENABLE ROW LEVEL SECURITY;

--
-- Name: quiz_packages quiz_packages_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY quiz_packages_tenant_isolation ON public.quiz_packages USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: quiz_version_questions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.quiz_version_questions ENABLE ROW LEVEL SECURITY;

--
-- Name: quiz_version_questions quiz_version_questions_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY quiz_version_questions_tenant_isolation ON public.quiz_version_questions USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: quiz_version_sections; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.quiz_version_sections ENABLE ROW LEVEL SECURITY;

--
-- Name: quiz_version_sections quiz_version_sections_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY quiz_version_sections_tenant_isolation ON public.quiz_version_sections USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: quiz_versions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.quiz_versions ENABLE ROW LEVEL SECURITY;

--
-- Name: quiz_versions quiz_versions_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY quiz_versions_tenant_isolation ON public.quiz_versions USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: student_group_members; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.student_group_members ENABLE ROW LEVEL SECURITY;

--
-- Name: student_group_members student_group_members_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY student_group_members_tenant_isolation ON public.student_group_members USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: student_groups; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.student_groups ENABLE ROW LEVEL SECURITY;

--
-- Name: student_groups student_groups_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY student_groups_tenant_isolation ON public.student_groups USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- Name: student_progress; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.student_progress ENABLE ROW LEVEL SECURITY;

--
-- Name: student_progress student_progress_tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY student_progress_tenant_isolation ON public.student_progress USING (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true)))) WITH CHECK (((current_setting('app.tenant_id'::text, true) IS NULL) OR (current_setting('app.tenant_id'::text, true) = ''::text) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))));


--
-- PostgreSQL database dump complete
--

\unrestrict AeELXRsYDuSodcrZvAfiAoRbGjZNHFZXYH3ZGdSrCWi8J27WPDvXmVMcbfnOyhD

