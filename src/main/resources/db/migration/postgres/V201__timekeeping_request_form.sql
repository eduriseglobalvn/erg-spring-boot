-- The filled-in leave form (BM01-ĐXNP/ERG, a Word file) a requester uploads; a LEAVE request cannot be submitted without it.
ALTER TABLE tk_requests ADD COLUMN IF NOT EXISTS form_file_name varchar(255);
ALTER TABLE tk_requests ADD COLUMN IF NOT EXISTS form_file bytea;
