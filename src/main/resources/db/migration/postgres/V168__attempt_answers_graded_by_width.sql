-- BUG-G-001: a hand grade stores the teacher id (a 36-character UUID); the column only fitted the auto-grader mark "server".
ALTER TABLE attempt_answers ALTER COLUMN graded_by TYPE varchar(64);
