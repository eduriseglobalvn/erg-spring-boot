CREATE TABLE IF NOT EXISTS lms_subjects (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    scope jsonb,
    name varchar(255),
    code varchar(255),
    status varchar(255)
);

CREATE TABLE IF NOT EXISTS lms_levels (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    subject_id varchar(255),
    name varchar(255),
    code varchar(255),
    sort_order integer,
    status varchar(255)
);

CREATE TABLE IF NOT EXISTS lms_topics (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    level_id varchar(255),
    name varchar(255),
    code varchar(255),
    sort_order integer,
    status varchar(255)
);

CREATE TABLE IF NOT EXISTS lms_questions (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    scope jsonb,
    subject_id varchar(255),
    level_id varchar(255),
    topic_id varchar(255),
    type varchar(255),
    stem varchar(4000),
    choices jsonb,
    answer jsonb,
    metadata jsonb,
    status varchar(255),
    archived_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS lms_quizzes (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    scope jsonb,
    title varchar(255),
    kind varchar(255),
    subject_id varchar(255),
    level_id varchar(255),
    topic_ids jsonb,
    question_ids jsonb,
    slides jsonb,
    settings jsonb,
    result jsonb,
    theme jsonb,
    theme_id varchar(255),
    status varchar(255),
    version integer,
    package_hash varchar(255),
    published_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS lms_students (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    school_id varchar(255),
    academic_class_id varchar(255),
    student_code varchar(255),
    full_name varchar(255),
    username varchar(255),
    auth_user_id varchar(255),
    email varchar(255),
    gender varchar(255),
    birthday timestamp with time zone,
    phone varchar(255),
    address varchar(1000),
    parent_name varchar(255),
    parent_phone varchar(255),
    parent_email varchar(255),
    parent_relationship varchar(255),
    enrollment_date timestamp with time zone,
    note varchar(1000),
    status varchar(255),
    metrics jsonb
);

CREATE TABLE IF NOT EXISTS lms_attempts (
    id varchar(24) PRIMARY KEY,
    tenant_id varchar(255),
    assignment_id varchar(255),
    quiz_id varchar(255),
    student_id varchar(255),
    package_id varchar(255),
    package_hash varchar(255),
    quiz_version varchar(255),
    status varchar(255),
    answers jsonb,
    events jsonb,
    client jsonb,
    score double precision,
    max_score double precision,
    percent double precision,
    passed boolean,
    started_at timestamp with time zone,
    submitted_at timestamp with time zone,
    updated_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_lms_subjects_tenant_status ON lms_subjects(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_lms_subjects_tenant_code ON lms_subjects(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_lms_levels_tenant_subject ON lms_levels(tenant_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_lms_levels_tenant_status ON lms_levels(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_lms_topics_tenant_level ON lms_topics(tenant_id, level_id);
CREATE INDEX IF NOT EXISTS idx_lms_topics_tenant_status ON lms_topics(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_lms_questions_tenant_status ON lms_questions(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_lms_questions_tenant_subject ON lms_questions(tenant_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_lms_questions_tenant_topic ON lms_questions(tenant_id, topic_id);
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_tenant_status ON lms_quizzes(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_tenant_subject ON lms_quizzes(tenant_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_lms_students_tenant_school ON lms_students(tenant_id, school_id);
CREATE INDEX IF NOT EXISTS idx_lms_students_tenant_academic_class ON lms_students(tenant_id, academic_class_id);
CREATE INDEX IF NOT EXISTS idx_lms_students_tenant_auth_user ON lms_students(tenant_id, auth_user_id);
CREATE INDEX IF NOT EXISTS idx_lms_students_tenant_code ON lms_students(tenant_id, student_code);
CREATE INDEX IF NOT EXISTS idx_lms_attempts_tenant_student ON lms_attempts(tenant_id, student_id);
CREATE INDEX IF NOT EXISTS idx_lms_attempts_tenant_assignment ON lms_attempts(tenant_id, assignment_id);
CREATE INDEX IF NOT EXISTS idx_lms_attempts_tenant_quiz ON lms_attempts(tenant_id, quiz_id);
CREATE INDEX IF NOT EXISTS idx_lms_attempts_status ON lms_attempts(tenant_id, status);
