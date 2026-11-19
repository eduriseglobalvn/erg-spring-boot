-- BUG-PR-001: staff who tick "ghi nhớ đăng nhập" slide 7 days within 30, students keep 30 within 90.
-- Every remembered session before this was a student's, so false is right for existing rows;
-- a constant default adds the column without rewriting the table.
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS remember_staff boolean NOT NULL DEFAULT false;
