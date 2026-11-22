-- Classroom games (batch 1): rooms live in memory while playing; one batch write at the end of a game.
CREATE TABLE game_room (
    id               varchar(36)  PRIMARY KEY,
    tenant_id        varchar(64)  NOT NULL,
    game_type        varchar(32)  NOT NULL,
    join_code        varchar(8)   NOT NULL,
    quiz_version_id  varchar(64)  NOT NULL,
    class_id         varchar(64)  NOT NULL,
    host_user_id     varchar(64)  NOT NULL,
    status           varchar(16)  NOT NULL,
    duration_minutes integer      NOT NULL,
    question_count   integer      NOT NULL,
    rules_json       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at       timestamptz  NOT NULL,
    started_at       timestamptz,
    ended_at         timestamptz
);
CREATE INDEX idx_game_room_host ON game_room (tenant_id, host_user_id, created_at DESC);
CREATE INDEX idx_game_room_class ON game_room (tenant_id, class_id, created_at DESC);

CREATE TABLE game_player (
    room_id          varchar(36)  NOT NULL REFERENCES game_room (id) ON DELETE CASCADE,
    user_id          varchar(64)  NOT NULL,
    display_name     varchar(255) NOT NULL,
    stars            integer      NOT NULL DEFAULT 0,
    coins            integer      NOT NULL DEFAULT 0,
    rank             integer      NOT NULL DEFAULT 0,
    correct_count    integer      NOT NULL DEFAULT 0,
    answered_count   integer      NOT NULL DEFAULT 0,
    longest_streak   integer      NOT NULL DEFAULT 0,
    hack_wins        integer      NOT NULL DEFAULT 0,
    hack_attempts    integer      NOT NULL DEFAULT 0,
    hacked_count     integer      NOT NULL DEFAULT 0,
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE game_answer (
    id               bigserial    PRIMARY KEY,
    room_id          varchar(36)  NOT NULL REFERENCES game_room (id) ON DELETE CASCADE,
    user_id          varchar(64)  NOT NULL,
    question_id      varchar(128) NOT NULL,
    attempt_no       integer      NOT NULL,
    context          varchar(16)  NOT NULL CHECK (context IN ('play', 'hack', 'defend', 'review')),
    correct          boolean      NOT NULL,
    answered_ms      integer      NOT NULL
);
CREATE INDEX idx_game_answer_room ON game_answer (room_id, user_id);
CREATE INDEX idx_game_answer_question ON game_answer (question_id, correct);

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_lms_games_host', now(), now(), 'lms.games.host', 'lms',
     'Host classroom games', 'Create and run classroom game rooms for managed classes',
     'games', 'host', 'lms', 'tenant,school,class', false, 'ACTIVE'),
    ('perm_lms_games_play', now(), now(), 'lms.games.play', 'lms',
     'Play classroom games', 'Join classroom game rooms of the learner''s own class',
     'games', 'play', 'lms', 'tenant,school,class,self', false, 'ACTIVE')
ON CONFLICT (name) DO UPDATE
SET group_name = EXCLUDED.group_name, label = EXCLUDED.label, description = EXCLUDED.description,
    resource = EXCLUDED.resource, action = EXCLUDED.action, portal = EXCLUDED.portal,
    supported_scope = EXCLUDED.supported_scope, is_system = EXCLUDED.is_system,
    status = EXCLUDED.status, updated_at = now();

WITH game_permissions(name, surface) AS (VALUES
    ('lms.games.host', 'REST /api/v1/lms/games/rooms (host)'),
    ('lms.games.play', 'REST /api/v1/lms/games/rooms/join')
)
INSERT INTO permission_bindings (
    id, permission_id, surface_type, surface_identifier, description, enforced, created_at, updated_at
)
SELECT 'pb_' || substr(md5(permission.name || ':lms-games-rest'), 1, 32), permission.id, 'REST',
       game.surface, 'Classroom games REST contract', true, now(), now()
FROM permissions permission
JOIN game_permissions game ON game.name = permission.name
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true, description = EXCLUDED.description, updated_at = now();

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'lms.games.play'
WHERE role.id = 'role_lms_student'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id);

-- Teachers who can create assignments can host games.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT assigner.role_id, permission.id
FROM role_permissions assigner
JOIN permissions assign_permission ON assign_permission.id = assigner.permission_id
                                  AND assign_permission.name = 'lms.assignment.create'
JOIN permissions permission ON permission.name = 'lms.games.host'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = assigner.role_id AND existing.permission_id = permission.id);
