-- V19__notification_retention_policy.sql
-- ============================================================================
-- 1. Retention Policy Configuration Table
-- ============================================================================
CREATE TABLE IF NOT EXISTS notification_retention_config (
    id              VARCHAR(36) PRIMARY KEY,
    table_name      VARCHAR(128) NOT NULL,
    retention_days  INTEGER NOT NULL DEFAULT 90,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_nrc_retention_days CHECK (retention_days >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_retention_config_table
    ON notification_retention_config(table_name);

-- ============================================================================
-- 2. Seed Default Retention Policy
-- ============================================================================
INSERT INTO notification_retention_config (id, table_name, retention_days, enabled)
VALUES ('nrc_delivery_attempts', 'notification_delivery_attempts', 90, true)
ON CONFLICT (table_name) DO NOTHING;

-- ============================================================================
-- 3. Cleanup Function
--    Returns the number of deleted rows and the retention days applied.
--    Can be called manually or by a scheduled job.
-- ============================================================================
CREATE OR REPLACE FUNCTION cleanup_stale_delivery_attempts()
RETURNS TABLE(deleted_count BIGINT, retention_days_used INTEGER)
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

-- ============================================================================
-- 4. Schedule Daily Cleanup via pg_cron (if extension is installed)
--    Runs at 02:00 system time every day.
-- ============================================================================
DO $do$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.schedule(
            'cleanup-delivery-attempts',
            '0 2 * * *',
            $$SELECT cleanup_stale_delivery_attempts();$$
        );
    END IF;
END;
$do$;
