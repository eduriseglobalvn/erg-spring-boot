ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username varchar(254);

UPDATE users
SET username = lower(trim(email))
WHERE username IS NULL
  AND email IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_normalized
    ON users (lower(username))
    WHERE username IS NOT NULL;

