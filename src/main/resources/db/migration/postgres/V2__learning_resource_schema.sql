CREATE TABLE IF NOT EXISTS academic_classes (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    school_id varchar(255) NOT NULL,
    name varchar(255),
    grade varchar(255),
    academic_year varchar(255),
    status varchar(255),
    homeroom_teacher_id varchar(255)
);

CREATE TABLE IF NOT EXISTS lms_assignments (
    id varchar(24) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255),
    academic_class_id varchar(255) NOT NULL,
    quiz_id varchar(255),
    subject_id varchar(255),
    student_ids jsonb,
    due_at timestamp with time zone,
    teacher_note varchar(255),
    status varchar(255),
    assigned_by varchar(255),
    recipient_mode varchar(255)
);

CREATE TABLE IF NOT EXISTS hoclieu_taxonomy (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    kind varchar(255) NOT NULL,
    label varchar(255) NOT NULL,
    slug varchar(255),
    parent_id varchar(96),
    subject_id varchar(96),
    grade_id varchar(96),
    category_id varchar(96),
    book_series_id varchar(96),
    level_ids jsonb,
    depth integer,
    sort_order integer,
    status varchar(255),
    description varchar(255)
);

CREATE TABLE IF NOT EXISTS hoclieu_resources (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    program_slug varchar(255),
    subject_id varchar(96) NOT NULL,
    grade_id varchar(96),
    category_id varchar(96),
    section_id varchar(96),
    book_series_id varchar(96),
    topic_id varchar(96),
    level_id varchar(96),
    document_type_id varchar(96),
    title varchar(255) NOT NULL,
    slug varchar(255),
    subtitle varchar(255),
    description varchar(255),
    thumbnail_url varchar(255),
    tags jsonb,
    metadata jsonb,
    visibility varchar(255),
    status varchar(255),
    published_at timestamp with time zone,
    lecture_design jsonb,
    created_by varchar(96),
    updated_by varchar(96)
);

CREATE TABLE IF NOT EXISTS hoclieu_assets (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    resource_id varchar(96) NOT NULL,
    title varchar(255) NOT NULL,
    selected_file_type varchar(255),
    launch_mode varchar(255),
    original_file_name varchar(255),
    detected_mime_type varchar(255),
    file_extension varchar(255),
    file_size_bytes bigint,
    storage_provider varchar(255),
    storage_url varchar(255),
    google_slide_manifest jsonb,
    can_download boolean,
    status varchar(255)
);

CREATE TABLE IF NOT EXISTS hoclieu_resource_items (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    resource_id varchar(96) NOT NULL,
    asset_id varchar(96),
    unit_title varchar(255),
    lesson_title varchar(255),
    sort_order integer,
    page_count integer,
    duration_sec integer
);

CREATE TABLE IF NOT EXISTS hoclieu_asset_slide_cache (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    asset_id varchar(96) NOT NULL,
    source_version varchar(255) NOT NULL,
    slide_index integer NOT NULL,
    title varchar(255),
    image_url varchar(2048),
    thumbnail_url varchar(2048),
    status varchar(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS hoclieu_progress_state (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    user_id varchar(96) NOT NULL,
    school_id varchar(96) NOT NULL,
    academic_year varchar(255) NOT NULL,
    subject_id varchar(96) NOT NULL,
    group_id varchar(96),
    lesson_id varchar(96) NOT NULL,
    resource_id varchar(96) NOT NULL,
    asset_id varchar(96) NOT NULL,
    progress_rate integer
);

CREATE TABLE IF NOT EXISTS hoclieu_slide_annotations (
    id varchar(96) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    user_id varchar(96) NOT NULL,
    school_id varchar(96) NOT NULL,
    academic_year varchar(255) NOT NULL,
    asset_id varchar(96) NOT NULL,
    slide_index integer NOT NULL,
    paths jsonb
);

CREATE INDEX IF NOT EXISTS idx_academic_classes_tenant_school ON academic_classes(tenant_id, school_id);
CREATE INDEX IF NOT EXISTS idx_academic_classes_tenant_teacher ON academic_classes(tenant_id, homeroom_teacher_id);
CREATE INDEX IF NOT EXISTS idx_academic_classes_tenant_status ON academic_classes(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_lms_assignments_tenant_academic_class ON lms_assignments(tenant_id, academic_class_id);
CREATE INDEX IF NOT EXISTS idx_lms_assignments_tenant_assigned_by ON lms_assignments(tenant_id, assigned_by);
CREATE INDEX IF NOT EXISTS idx_lms_assignments_tenant_quiz ON lms_assignments(tenant_id, quiz_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_taxonomy_tenant_kind_status ON hoclieu_taxonomy(tenant_id, kind, status);
CREATE INDEX IF NOT EXISTS idx_hoclieu_taxonomy_subject_parent ON hoclieu_taxonomy(subject_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_taxonomy_category ON hoclieu_taxonomy(category_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_tenant_subject ON hoclieu_resources(tenant_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_tenant_category ON hoclieu_resources(tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_tenant_section ON hoclieu_resources(tenant_id, section_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_tenant_topic ON hoclieu_resources(tenant_id, topic_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_status_visibility ON hoclieu_resources(tenant_id, status, visibility);
CREATE INDEX IF NOT EXISTS idx_hoclieu_assets_resource ON hoclieu_assets(resource_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_assets_status ON hoclieu_assets(status);
CREATE INDEX IF NOT EXISTS idx_hoclieu_items_tenant_resource ON hoclieu_resource_items(tenant_id, resource_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_items_asset ON hoclieu_resource_items(asset_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_slide_cache_asset_version ON hoclieu_asset_slide_cache(tenant_id, asset_id, source_version, slide_index);
CREATE INDEX IF NOT EXISTS idx_hoclieu_slide_cache_status ON hoclieu_asset_slide_cache(tenant_id, asset_id, status);
CREATE INDEX IF NOT EXISTS idx_hoclieu_progress_state_lesson ON hoclieu_progress_state(tenant_id, user_id, school_id, academic_year, lesson_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_progress_state_resource ON hoclieu_progress_state(tenant_id, user_id, school_id, academic_year, resource_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_hoclieu_progress_state_asset_scope
    ON hoclieu_progress_state(tenant_id, user_id, school_id, academic_year, subject_id, lesson_id, resource_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_annotations_asset_slide ON hoclieu_slide_annotations(tenant_id, asset_id, slide_index);
CREATE UNIQUE INDEX IF NOT EXISTS uk_hoclieu_annotation_owner_scope
    ON hoclieu_slide_annotations(tenant_id, user_id, school_id, academic_year, asset_id, slide_index);

