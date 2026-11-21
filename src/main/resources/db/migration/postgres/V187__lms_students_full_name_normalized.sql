-- Student import matches a sheet name to the roster on NFC + collapsed whitespace + lower case (the Java Student.normalizeName rule),
-- which lower(full_name) cannot do for double spaces, NBSP or decomposed (NFD) Vietnamese. The entity keeps the column in sync (@PrePersist/@PreUpdate).
ALTER TABLE lms_students ADD COLUMN IF NOT EXISTS full_name_normalized text;

UPDATE lms_students
   SET full_name_normalized = lower(btrim(regexp_replace(normalize(full_name, NFC), '[ \t\n\r\f\v\u00A0]+', ' ', 'g')))
 WHERE full_name IS NOT NULL AND full_name_normalized IS NULL;
