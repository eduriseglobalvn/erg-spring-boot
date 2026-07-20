# Runbook reset va seed lai PostgreSQL

Ngay tao: 2026-06-10  
Pham vi: `D:\ERG\erg-spring`

## Muc dich

Dung khi can xoa toan bo schema cu va dung lai DB moi co relationship/FK day du theo migration `V1..V16`.

Script chinh:

```text
scripts/reset-and-seed-postgres.ps1
```

## Lenh chay day du

```powershell
.\scripts\reset-and-seed-postgres.ps1 -Confirmation I_UNDERSTAND_THIS_DELETES_DATA
```

Script se:

1. Doc `.env` de lay `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
2. Chay Flyway `clean` tren schema `public`, xoa toan bo bang/index/constraint/function trong schema do.
3. Chay Flyway `migrate` tu `src/main/resources/db/migration/postgres`, tao lai bang moi va FK relationship.
4. Chay `DatabaseReseedRunnerTest` de seed LMS/HocLieu bootstrap data.
5. Chay `DatabaseRelationshipMigrationContractTest` va `DatabaseRelationshipConstraintIntegrationTest` de verify migration va FK tren PostgreSQL that.

## Lenh neu muon tach buoc

Chi reset va migrate, khong seed:

```powershell
.\scripts\reset-and-seed-postgres.ps1 -Confirmation I_UNDERSTAND_THIS_DELETES_DATA -SkipSeed
```

Reset, migrate, seed nhung bo qua verify:

```powershell
.\scripts\reset-and-seed-postgres.ps1 -Confirmation I_UNDERSTAND_THIS_DELETES_DATA -SkipVerify
```

## Dieu kien an toan

- Script bat buoc co confirmation `I_UNDERSTAND_THIS_DELETES_DATA`.
- Script khong can `psql` hoac `pg_dump`; no dung Flyway Maven plugin.
- Script dung migration source path `src/main/resources/db/migration/postgres`, tranh loi stale migration trong `target/classes`.
- Khong nen doi `spring.jpa.hibernate.ddl-auto` sang `create-drop`; project nay dung Flyway lam source of truth.

## Dau ra mong doi

Sau khi chay thanh cong:

```text
Flyway schema version: 16
DatabaseReseedRunnerTest: pass
DatabaseRelationshipMigrationContractTest: pass
DatabaseRelationshipConstraintIntegrationTest: pass
```

Seed toi thieu duoc verify:

```text
users >= 6
education_units >= 4
academic_classes >= 14
lms_students >= 2
hoclieu_taxonomy >= 8
hoclieu_resources >= 4
hoclieu_assets >= 4
```

