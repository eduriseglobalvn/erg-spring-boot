-- Some long-lived deployments were initialized from a UUID-based authorization
-- schema, while the canonical ERG RBAC model uses stable string IDs (role_* and
-- perm_*). Convert only RBAC identifiers and their links; user IDs are not
-- touched by this repair.
CREATE TEMP TABLE IF NOT EXISTS _erg_rbac_fk_restore (
    table_schema text NOT NULL,
    table_name text NOT NULL,
    constraint_name text NOT NULL,
    constraint_definition text NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    column_record record;
    constraint_record record;
BEGIN
    -- Save and remove only foreign keys connected to a column being converted.
    -- Their exact definitions are restored after all source and target columns
    -- use the same canonical type.
    FOR constraint_record IN
        SELECT DISTINCT
            namespace.nspname AS table_schema,
            relation.relname AS table_name,
            constraint_item.conname AS constraint_name,
            pg_get_constraintdef(constraint_item.oid) AS constraint_definition
        FROM pg_constraint constraint_item
        JOIN pg_class relation ON relation.oid = constraint_item.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE constraint_item.contype = 'f'
          AND (
          EXISTS (
              SELECT 1
              FROM unnest(constraint_item.conkey) AS key_column(attnum)
              JOIN pg_attribute attribute
                ON attribute.attrelid = constraint_item.conrelid
               AND attribute.attnum = key_column.attnum
              WHERE (namespace.nspname, relation.relname, attribute.attname) IN (
                  ('public', 'role_permissions', 'role_id'),
                  ('public', 'role_permissions', 'permission_id'),
                  ('public', 'user_roles', 'role_id'),
                  ('public', 'user_role_assignments', 'role_id'),
                  ('public', 'permission_bindings', 'permission_id')
              )
          )
          OR EXISTS (
              SELECT 1
              FROM unnest(constraint_item.confkey) AS key_column(attnum)
              JOIN pg_attribute attribute
                ON attribute.attrelid = constraint_item.confrelid
               AND attribute.attnum = key_column.attnum
              JOIN pg_class referenced_relation ON referenced_relation.oid = constraint_item.confrelid
              JOIN pg_namespace referenced_namespace ON referenced_namespace.oid = referenced_relation.relnamespace
              WHERE (referenced_namespace.nspname, referenced_relation.relname, attribute.attname) IN (
                  ('public', 'roles', 'id'),
                  ('public', 'permissions', 'id'),
                  ('public', 'permission_groups', 'id')
              )
          )
          )
    LOOP
        INSERT INTO _erg_rbac_fk_restore VALUES (
            constraint_record.table_schema,
            constraint_record.table_name,
            constraint_record.constraint_name,
            constraint_record.constraint_definition
        );
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            constraint_record.table_schema,
            constraint_record.table_name,
            constraint_record.constraint_name
        );
    END LOOP;

    FOR column_record IN
        SELECT table_name, column_name
        FROM (VALUES
            ('roles', 'id'),
            ('permissions', 'id'),
            ('permission_groups', 'id'),
            ('role_permissions', 'role_id'),
            ('role_permissions', 'permission_id'),
            ('user_roles', 'role_id'),
            ('user_role_assignments', 'role_id'),
            ('permission_bindings', 'permission_id')
        ) AS expected_column(table_name, column_name)
        WHERE EXISTS (
            SELECT 1
            FROM information_schema.columns column_info
            WHERE column_info.table_schema = 'public'
              AND column_info.table_name = expected_column.table_name
              AND column_info.column_name = expected_column.column_name
              AND column_info.udt_name = 'uuid'
        )
    LOOP
        EXECUTE format(
            'ALTER TABLE public.%I ALTER COLUMN %I TYPE varchar(36) USING %I::text',
            column_record.table_name,
            column_record.column_name,
            column_record.column_name
        );
    END LOOP;
END $$;

DO $$
DECLARE
    constraint_record record;
BEGIN
    FOR constraint_record IN SELECT * FROM _erg_rbac_fk_restore
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            constraint_record.table_schema,
            constraint_record.table_name,
            constraint_record.constraint_name,
            constraint_record.constraint_definition
        );
    END LOOP;
END $$;
