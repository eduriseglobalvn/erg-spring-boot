-- Which questions a room plays: every playable one, or only those the class answered wrong before.
ALTER TABLE game_room ADD COLUMN question_set varchar(16) NOT NULL DEFAULT 'ALL';
