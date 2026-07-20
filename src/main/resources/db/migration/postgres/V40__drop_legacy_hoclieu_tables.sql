-- Hard cutover: canonical curriculum/content tables are now the only supported
-- learning-resource storage. V39 backfills content_* before this migration runs.

DELETE FROM legacy_id_mappings
WHERE legacy_table IN ('hoclieu_taxonomy', 'hoclieu_resources', 'hoclieu_assets', 'hoclieu_resource_items')
   OR legacy_table LIKE 'hoclieu\_%' ESCAPE '\';

DROP TABLE IF EXISTS hoclieu_slide_annotations CASCADE;
DROP TABLE IF EXISTS hoclieu_progress_state CASCADE;
DROP TABLE IF EXISTS hoclieu_asset_slide_cache CASCADE;
DROP TABLE IF EXISTS hoclieu_resource_items CASCADE;
DROP TABLE IF EXISTS hoclieu_assets CASCADE;
DROP TABLE IF EXISTS hoclieu_resources CASCADE;
DROP TABLE IF EXISTS hoclieu_taxonomy CASCADE;
