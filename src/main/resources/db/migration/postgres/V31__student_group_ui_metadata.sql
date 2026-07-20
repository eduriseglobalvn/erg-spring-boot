-- Persist FE student-group metadata that is part of the current LMS grouping UI.
ALTER TABLE student_groups ADD COLUMN IF NOT EXISTS note text;
ALTER TABLE student_groups ADD COLUMN IF NOT EXISTS color varchar(64);
