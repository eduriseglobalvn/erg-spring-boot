-- Questions and quizzes must reference the same canonical curriculum used by learning content.
INSERT INTO curriculum_subjects (id, tenant_id, code, name, description, status, sort_order, created_at, updated_at)
VALUES
    ('sub_python', 'erg', 'python', 'Python', 'Python programming curriculum', 'active', 900, now(), now()),
    ('sub_mos', 'erg', 'mos', 'MOS', 'Microsoft Office Specialist curriculum', 'active', 910, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO curriculum_levels (id, tenant_id, subject_id, code, name, description, status, sort_order, created_at, updated_at)
VALUES
    ('lvl_python_foundation', 'erg', 'sub_python', 'python-foundation', 'Python Foundation', 'Python foundation level', 'active', 10, now(), now()),
    ('lvl_python_intermediate', 'erg', 'sub_python', 'python-intermediate', 'Python Intermediate', 'Python intermediate level', 'active', 20, now(), now()),
    ('lvl_mos_word', 'erg', 'sub_mos', 'mos-word', 'MOS Word', 'Microsoft Word level', 'active', 10, now(), now()),
    ('lvl_mos_excel', 'erg', 'sub_mos', 'mos-excel', 'MOS Excel', 'Microsoft Excel level', 'active', 20, now(), now()),
    ('lvl_mos_powerpoint', 'erg', 'sub_mos', 'mos-powerpoint', 'MOS PowerPoint', 'Microsoft PowerPoint level', 'active', 30, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO curriculum_topics (id, tenant_id, level_id, code, name, description, status, sort_order, created_at, updated_at)
VALUES
    ('top_python_foundation', 'erg', 'lvl_python_foundation', 'python-foundation', 'Python Foundation', 'Python foundation questions', 'active', 10, now(), now()),
    ('top_python_intermediate', 'erg', 'lvl_python_intermediate', 'python-intermediate', 'Python Intermediate', 'Python intermediate questions', 'active', 10, now(), now()),
    ('top_mos_word', 'erg', 'lvl_mos_word', 'mos-word', 'Microsoft Word', 'Microsoft Word questions', 'active', 10, now(), now()),
    ('top_mos_excel', 'erg', 'lvl_mos_excel', 'mos-excel', 'Microsoft Excel', 'Microsoft Excel questions', 'active', 10, now(), now()),
    ('top_mos_powerpoint', 'erg', 'lvl_mos_powerpoint', 'mos-powerpoint', 'Microsoft PowerPoint', 'Microsoft PowerPoint questions', 'active', 10, now(), now())
ON CONFLICT (id) DO NOTHING;

ALTER TABLE lms_questions DROP CONSTRAINT IF EXISTS fk_lms_questions_subject;
ALTER TABLE lms_questions DROP CONSTRAINT IF EXISTS fk_lms_questions_level;
ALTER TABLE lms_questions DROP CONSTRAINT IF EXISTS fk_lms_questions_topic;
ALTER TABLE lms_questions DROP CONSTRAINT IF EXISTS fk_lms_questions_category;
ALTER TABLE lms_quizzes DROP CONSTRAINT IF EXISTS fk_lms_quizzes_subject;
ALTER TABLE lms_quizzes DROP CONSTRAINT IF EXISTS fk_lms_quizzes_level;
ALTER TABLE lms_quizzes DROP CONSTRAINT IF EXISTS fk_lms_quizzes_category;

UPDATE lms_questions SET
  subject_id = CASE subject_id WHEN 'ic3-gs6' THEN 'sub_ic3' WHEN 'python' THEN 'sub_python' WHEN 'mos' THEN 'sub_mos' ELSE subject_id END,
  level_id = CASE
    WHEN level_id LIKE 'ic3-l%' THEN 'lvl_ic3_gs6_l1'
    WHEN level_id = 'python-foundation' THEN 'lvl_python_foundation'
    WHEN level_id = 'python-intermediate' THEN 'lvl_python_intermediate'
    WHEN level_id = 'mos-word' THEN 'lvl_mos_word'
    WHEN level_id = 'mos-excel' THEN 'lvl_mos_excel'
    WHEN level_id = 'mos-powerpoint' THEN 'lvl_mos_powerpoint'
    ELSE level_id END,
  topic_id = CASE
    WHEN topic_id LIKE 'ic3-%' THEN 'top_ic3_safety'
    WHEN topic_id LIKE 'python-foundation%' THEN 'top_python_foundation'
    WHEN topic_id LIKE 'python-intermediate%' THEN 'top_python_intermediate'
    WHEN topic_id LIKE 'mos-word%' THEN 'top_mos_word'
    WHEN topic_id LIKE 'mos-excel%' THEN 'top_mos_excel'
    WHEN topic_id LIKE 'mos-powerpoint%' THEN 'top_mos_powerpoint'
    ELSE topic_id END,
  category_id = CASE
    WHEN category_id LIKE 'ic3-%' THEN 'top_ic3_safety'
    WHEN category_id LIKE 'python-foundation%' THEN 'top_python_foundation'
    WHEN category_id LIKE 'python-intermediate%' THEN 'top_python_intermediate'
    WHEN category_id LIKE 'mos-word%' THEN 'top_mos_word'
    WHEN category_id LIKE 'mos-excel%' THEN 'top_mos_excel'
    WHEN category_id LIKE 'mos-powerpoint%' THEN 'top_mos_powerpoint'
    ELSE category_id END;

UPDATE lms_quizzes SET
  subject_id = CASE subject_id WHEN 'ic3-gs6' THEN 'sub_ic3' WHEN 'python' THEN 'sub_python' WHEN 'mos' THEN 'sub_mos' ELSE subject_id END,
  level_id = CASE
    WHEN level_id LIKE 'ic3-l%' THEN 'lvl_ic3_gs6_l1'
    WHEN level_id = 'python-foundation' THEN 'lvl_python_foundation'
    WHEN level_id = 'python-intermediate' THEN 'lvl_python_intermediate'
    WHEN level_id = 'mos-word' THEN 'lvl_mos_word'
    WHEN level_id = 'mos-excel' THEN 'lvl_mos_excel'
    WHEN level_id = 'mos-powerpoint' THEN 'lvl_mos_powerpoint'
    ELSE level_id END,
  category_id = CASE
    WHEN category_id LIKE 'ic3-%' THEN 'top_ic3_safety'
    WHEN category_id LIKE 'python-foundation%' THEN 'top_python_foundation'
    WHEN category_id LIKE 'python-intermediate%' THEN 'top_python_intermediate'
    WHEN category_id LIKE 'mos-word%' THEN 'top_mos_word'
    WHEN category_id LIKE 'mos-excel%' THEN 'top_mos_excel'
    WHEN category_id LIKE 'mos-powerpoint%' THEN 'top_mos_powerpoint'
    ELSE category_id END;

ALTER TABLE lms_questions ADD CONSTRAINT fk_lms_questions_subject FOREIGN KEY (subject_id) REFERENCES curriculum_subjects(id) ON DELETE RESTRICT;
ALTER TABLE lms_questions ADD CONSTRAINT fk_lms_questions_level FOREIGN KEY (level_id) REFERENCES curriculum_levels(id) ON DELETE RESTRICT;
ALTER TABLE lms_questions ADD CONSTRAINT fk_lms_questions_topic FOREIGN KEY (topic_id) REFERENCES curriculum_topics(id) ON DELETE RESTRICT;
ALTER TABLE lms_questions ADD CONSTRAINT fk_lms_questions_category FOREIGN KEY (category_id) REFERENCES curriculum_topics(id) ON DELETE RESTRICT;
ALTER TABLE lms_quizzes ADD CONSTRAINT fk_lms_quizzes_subject FOREIGN KEY (subject_id) REFERENCES curriculum_subjects(id) ON DELETE RESTRICT;
ALTER TABLE lms_quizzes ADD CONSTRAINT fk_lms_quizzes_level FOREIGN KEY (level_id) REFERENCES curriculum_levels(id) ON DELETE RESTRICT;
ALTER TABLE lms_quizzes ADD CONSTRAINT fk_lms_quizzes_category FOREIGN KEY (category_id) REFERENCES curriculum_topics(id) ON DELETE RESTRICT;
