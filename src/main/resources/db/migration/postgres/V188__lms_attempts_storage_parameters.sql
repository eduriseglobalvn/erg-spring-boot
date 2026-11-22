-- Autosave rewrites attempt rows constantly: leave room for HOT updates and vacuum the churn early.
-- Metadata only; fillfactor applies to newly written pages.
ALTER TABLE lms_attempts SET (fillfactor = 85, autovacuum_vacuum_scale_factor = 0.02);
