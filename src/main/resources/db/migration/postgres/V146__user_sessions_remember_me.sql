-- Student "ghi nhớ đăng nhập": the session slides 30 days per refresh, capped at 90 days after login.
-- A stored flag replaces the "lifetime above one day" guess, so no other long session starts sliding.
-- A constant default adds the column without rewriting the table.
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS remember_me boolean NOT NULL DEFAULT false;
