-- Extend the canonical IC3 Digital Literacy roadmap with the next two levels.
-- Topics and content remain authored through the curriculum/content contracts so
-- Course Structure and Resources continue to read the same canonical graph.

INSERT INTO curriculum_levels (
    id, tenant_id, subject_id, code, name, description, status, sort_order,
    created_by, updated_by, created_at, updated_at
)
VALUES
    (
        'lvl_ic3_gs6_l2', 'erg', 'sub_ic3', 'ic3-gs6-level-2',
        'IC3 GS6 Level 2', 'Intermediate IC3 digital literacy level',
        'active', 20, 'seed', 'seed', now(), now()
    ),
    (
        'lvl_ic3_gs6_l3', 'erg', 'sub_ic3', 'ic3-gs6-level-3',
        'IC3 GS6 Level 3', 'Advanced IC3 digital literacy level',
        'active', 30, 'seed', 'seed', now(), now()
    )
ON CONFLICT ON CONSTRAINT uq_curriculum_levels_tenant_subject_code DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_by = EXCLUDED.updated_by,
    updated_at = now();
