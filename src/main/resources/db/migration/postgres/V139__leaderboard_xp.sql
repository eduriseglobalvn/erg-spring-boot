-- "Thi đua" (design H03): a student may hide themselves from classmates; they still see their own rank.
ALTER TABLE lms_students
    ADD COLUMN IF NOT EXISTS leaderboard_hidden boolean NOT NULL DEFAULT false;
