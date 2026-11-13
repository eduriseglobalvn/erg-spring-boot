-- Inbox: when the student last opened a thread. A thread is unread while a teacher message is newer than this (or it is null).
-- Existing table, tenant RLS policy and tenant-leading indexes (idx_student_question_thread_student) come from V142.
ALTER TABLE student_question_threads ADD COLUMN IF NOT EXISTS student_read_at timestamp with time zone;
