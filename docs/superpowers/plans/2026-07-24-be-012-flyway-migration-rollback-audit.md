# BE-012 — Audit Flyway migration và rollback strategy

## Task
- **ID:** BE-012 (ERG-SPRING, P1, Migration)
- **Goal:** Migration chạy sạch trên database mới **và** database nâng cấp; append-only, có rollback strategy.
- **Dependency:** none.

## Baseline (source hiện tại)
- 76 migration `V1..V76` trong `db/migration/postgres`, không có file undo/down (append-only đúng).
- WIP trong worktree:
  - `FlywayUpgradePathIntegrationTest` (Testcontainers, `disabledWithoutDocker`): migrate tới V75 rồi upgrade in-place lên V76, assert success + 2 unique index tồn tại.
  - `docs/runbooks/flyway-upgrade-rollback.md`: release gate (backup→restore→upgrade test→parity) + rollback (forward-repair, không destructive down, restore-from-backup DR).
- `FlywayConfigTest`: prod dùng filesystem location, migration owner credentials tách runtime datasource.
- Fresh-DB validate: `hbm2ddl.auto=validate` + nhiều `*MigrationContractTest` chạy trên Testcontainers.

## Vấn đề (gap theo DoD)
- Append-only policy (runbook ghi "never destructive down migrations") **chưa được test khóa**. Không có guard chống:
  - version trùng (hai file cùng số V).
  - gap thứ tự (thiếu version → lịch sử không liền mạch, dễ lỗi khi upgrade).
  - file undo/down destructive lọt vào.
- Đây là guard unit-level chạy được trong sandbox (không cần Docker), khác với upgrade-path test (cần Testcontainers).

## Invariants giữ nguyên
- Không sửa migration đã phát hành (append-only).
- Không thêm undo migration.
- Không đổi `FlywayUpgradePathIntegrationTest` (đã đúng).

## File create/modify
1. CREATE `src/test/java/com/erg/ergspringboot/config/persistence/MigrationVersionSequenceContractTest.java`
   - Scan filenames `db/migration/postgres/V*__*.sql`.
   - Assert: version duy nhất (no duplicate), tuần tự 1..N không gap, không có file undo/down (`U*`, `*undo*`, `*rollback*`, `*down*`).

## Các bước
1. Viết test scan filename.
2. Chạy focused test → xanh với 76 migration hiện tại.
3. (Nếu có Docker) `FlywayUpgradePathIntegrationTest` là upgrade evidence; ghi nhận skip nếu sandbox không Docker.

## Command & expected
- `mvnw -Dtest=MigrationVersionSequenceContractTest test` → BUILD SUCCESS.

## Rollback
- Chỉ 1 file test mới. Revert là đủ.

## Definition of Done (đo được)
- Fresh-DB: `hbm2ddl validate` + MigrationContractTests (đã có).
- Upgrade-path: `FlywayUpgradePathIntegrationTest` V75→V76 (WIP, Testcontainers).
- Append-only invariant có test khóa (unique version, no gap, no undo/down).
- Rollback runbook tồn tại (WIP).

## Không làm (chống scope creep)
- Không viết undo migration.
- Không chạy production restore (deploy-time DR).

## Final status — 2026-07-24 (Hoàn thành)

DoD "migration chạy sạch trên DB mới và DB nâng cấp" đã được cover:
- **Fresh DB**: các Testcontainers persistence test (`*PersistenceTest`, `@EnabledIf` Postgres) + `verify` job chạy full migration trên DB mới.
- **Upgrade path**: `FlywayUpgradePathIntegrationTest` (WIP) migrate tới V75 rồi upgrade in-place lên V76, assert success + index tồn tại.
- **Append-only guard** (gap đã đóng): thêm test scan filename enforce policy runbook — version tuần tự 1..76, không trùng, không có file undo/down (U*/undo/down/rollback), đúng format `V<n>__desc.sql`.
- **Rollback runbook**: `docs/runbooks/flyway-upgrade-rollback.md` (WIP) — backup/restore gate, forward-repair thay vì destructive down, evidence checklist.

### Changed
- `test/.../config/persistence/FlywayMigrationAppendOnlyContractTest.java` (mới) — 2 test khóa append-only invariant.

### Evidence
- Focused: `mvnw -Dtest=FlywayMigrationAppendOnlyContractTest test` → 2 tests, 0 failures, BUILD SUCCESS.
- Xác minh source: 76 migration V1..V76, sequential không gap/trùng, 0 undo/down file.
- Upgrade-path + fresh-DB integration tests là Testcontainers-gated (chạy ở CI có Docker); append-only guard chạy pure-unit mọi nơi.

### Intentionally not done
- Không sửa `FlywayUpgradePathIntegrationTest` để assert index thứ 3 của V76 (uq_lms_quizzes) — upgrade success đã đủ cho DoD; index đó đã có contract test riêng ở BE-009.
- Production rollback drill trên bản sao prod = deploy-time evidence trong runbook.
