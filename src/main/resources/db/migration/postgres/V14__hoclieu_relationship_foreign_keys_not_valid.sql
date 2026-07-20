CREATE INDEX IF NOT EXISTS idx_hoclieu_taxonomy_parent_id ON hoclieu_taxonomy(parent_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_taxonomy_subject_id ON hoclieu_taxonomy(subject_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_created_by ON hoclieu_resources(created_by);
CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_updated_by ON hoclieu_resources(updated_by);
CREATE INDEX IF NOT EXISTS idx_hoclieu_progress_state_user_id ON hoclieu_progress_state(user_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_progress_state_school_id ON hoclieu_progress_state(school_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_progress_state_asset_id ON hoclieu_progress_state(asset_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_annotations_user_id ON hoclieu_slide_annotations(user_id);
CREATE INDEX IF NOT EXISTS idx_hoclieu_annotations_school_id ON hoclieu_slide_annotations(school_id);

ALTER TABLE hoclieu_taxonomy
    ADD CONSTRAINT fk_hoclieu_taxonomy_parent
    FOREIGN KEY (parent_id) REFERENCES hoclieu_taxonomy(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE hoclieu_taxonomy
    ADD CONSTRAINT fk_hoclieu_taxonomy_subject
    FOREIGN KEY (subject_id) REFERENCES hoclieu_taxonomy(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE hoclieu_taxonomy
    ADD CONSTRAINT fk_hoclieu_taxonomy_category
    FOREIGN KEY (category_id) REFERENCES hoclieu_taxonomy(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE hoclieu_resources
    ADD CONSTRAINT fk_hoclieu_resources_subject
    FOREIGN KEY (subject_id) REFERENCES hoclieu_taxonomy(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE hoclieu_resources
    ADD CONSTRAINT fk_hoclieu_resources_category
    FOREIGN KEY (category_id) REFERENCES hoclieu_taxonomy(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE hoclieu_resources
    ADD CONSTRAINT fk_hoclieu_resources_section
    FOREIGN KEY (section_id) REFERENCES hoclieu_taxonomy(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE hoclieu_resources
    ADD CONSTRAINT fk_hoclieu_resources_created_by
    FOREIGN KEY (created_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE hoclieu_resources
    ADD CONSTRAINT fk_hoclieu_resources_updated_by
    FOREIGN KEY (updated_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE hoclieu_assets
    ADD CONSTRAINT fk_hoclieu_assets_resource
    FOREIGN KEY (resource_id) REFERENCES hoclieu_resources(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_resource_items
    ADD CONSTRAINT fk_hoclieu_resource_items_resource
    FOREIGN KEY (resource_id) REFERENCES hoclieu_resources(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_resource_items
    ADD CONSTRAINT fk_hoclieu_resource_items_asset
    FOREIGN KEY (asset_id) REFERENCES hoclieu_assets(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE hoclieu_asset_slide_cache
    ADD CONSTRAINT fk_hoclieu_asset_slide_cache_asset
    FOREIGN KEY (asset_id) REFERENCES hoclieu_assets(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_progress_state
    ADD CONSTRAINT fk_hoclieu_progress_state_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_progress_state
    ADD CONSTRAINT fk_hoclieu_progress_state_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_progress_state
    ADD CONSTRAINT fk_hoclieu_progress_state_resource
    FOREIGN KEY (resource_id) REFERENCES hoclieu_resources(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_progress_state
    ADD CONSTRAINT fk_hoclieu_progress_state_asset
    FOREIGN KEY (asset_id) REFERENCES hoclieu_assets(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_slide_annotations
    ADD CONSTRAINT fk_hoclieu_slide_annotations_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_slide_annotations
    ADD CONSTRAINT fk_hoclieu_slide_annotations_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE hoclieu_slide_annotations
    ADD CONSTRAINT fk_hoclieu_slide_annotations_asset
    FOREIGN KEY (asset_id) REFERENCES hoclieu_assets(id)
    ON DELETE CASCADE
    NOT VALID;
