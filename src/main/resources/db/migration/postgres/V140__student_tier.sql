-- Student tiers ("Bậc") and profile frames: one row per student and week, written by the Monday 00:00 (Asia/Ho_Chi_Minh) job.
-- Every table carries tenant_id; the API only reads the authenticated student's own rows and the cutoff table of their grade.

CREATE TABLE IF NOT EXISTS student_tier_week (
    tenant_id       varchar(255) NOT NULL,
    student_id      varchar(24)  NOT NULL,
    school_id       varchar(255) NOT NULL,
    grade           varchar(64)  NOT NULL,
    school_year     varchar(9)   NOT NULL,
    week_start      date         NOT NULL,
    course_xp       integer      NOT NULL,
    submitted_count integer      NOT NULL,
    grade_rank      integer,
    grade_size      integer      NOT NULL,
    tier_key        varchar(8),
    division        smallint,
    below_count     smallint     NOT NULL DEFAULT 0,
    protected       boolean      NOT NULL DEFAULT false,
    created_at      timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, student_id, week_start)
);

CREATE INDEX IF NOT EXISTS idx_student_tier_week_cohort
    ON student_tier_week (tenant_id, school_id, grade, week_start);

-- division = 0: the tier has no divisions (Cao thu).
CREATE TABLE IF NOT EXISTS student_tier_cutoff (
    tenant_id   varchar(255) NOT NULL,
    school_id   varchar(255) NOT NULL,
    grade       varchar(64)  NOT NULL,
    school_year varchar(9)   NOT NULL,
    week_start  date         NOT NULL,
    tier_key    varchar(8)   NOT NULL,
    division    smallint     NOT NULL DEFAULT 0,
    rank_from   integer      NOT NULL,
    rank_to     integer      NOT NULL,
    min_xp      integer      NOT NULL,
    PRIMARY KEY (tenant_id, school_id, grade, week_start, tier_key, division)
);

-- A frame, once earned, stays for good (also across school years).
CREATE TABLE IF NOT EXISTS student_frame (
    tenant_id   varchar(255) NOT NULL,
    student_id  varchar(24)  NOT NULL,
    tier_key    varchar(8)   NOT NULL,
    unlocked_at date         NOT NULL,
    school_year varchar(9)   NOT NULL,
    PRIMARY KEY (tenant_id, student_id, tier_key)
);

-- No row: the frame of the current tier. tier_key NULL: "Không dùng".
CREATE TABLE IF NOT EXISTS student_frame_choice (
    tenant_id  varchar(255) NOT NULL,
    student_id varchar(24)  NOT NULL,
    tier_key   varchar(8),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, student_id)
);

CREATE TABLE IF NOT EXISTS student_tier_seen (
    tenant_id  varchar(255) NOT NULL,
    student_id varchar(24)  NOT NULL,
    week_start date         NOT NULL,
    seen_at    timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, student_id, week_start)
);
