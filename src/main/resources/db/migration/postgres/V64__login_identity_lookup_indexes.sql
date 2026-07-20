-- AuthService locks a matching account by case-insensitive username or email.
-- These expression indexes keep that security-critical lookup bounded even as
-- the employee directory grows; PostgreSQL can combine them for the OR query.
CREATE INDEX IF NOT EXISTS idx_users_lower_username
    ON users (lower(username));

CREATE INDEX IF NOT EXISTS idx_users_lower_email
    ON users (lower(email));
