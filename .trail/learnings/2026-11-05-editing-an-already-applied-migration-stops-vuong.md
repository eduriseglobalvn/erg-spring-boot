---
title: Editing an already-applied migration stops every database that ran the old version from starting
author: Vuong
date: 2026-11-05
branch: claude/fix-audit-findings
cost: { minutes: 3, tokens: 16614 }
id: l-f33590e2
scope: repo
status: active
src: agent
touches:
  - docs/second-brain/review/README.md
  - ".gitattributes"
  - ops/docker/deploy.sh
---

## Decided
The super-admin rotation migration was rewritten in place so it no longer overwrites a password the owner already rotated. The commit argued this was safe because the migration was not yet on production. Any database that had already applied the old version (dev, staging, CI snapshots) now fails Flyway checksum validation at startup. The fix for those databases is to set the stored checksum to the new file's value (the same thing `flyway repair` does). Do not edit the file back.

## Tried and ruled out
- Reading the Spring error alone is misleading. It reports `jpaSharedEM_postgresEntityManagerFactory` / `ergAuthenticationFilter` unsatisfied dependency, not Flyway. The giveaway is that the Flyway "Database:" log line is followed straight by the failure, and `flyway_schema_history` has no newer versions applied.

## Watch out
- Before deploying to staging or production, compare the stored checksum for version 110 with the new value. A database that ran the old file needs the same checksum update, or startup will fail there too.
- The repo rule is that migrations are append-only. Prefer a new forward migration over editing one that may already be applied anywhere.

In the code:
- V110__rotate_super_admin_password.sql, edited in b78d072.
- Checksum of the old file: -1489781432. Checksum of the new file: 274939407.
- Local fix: `update flyway_schema_history set checksum = 274939407 where version = '110' and checksum = -1489781432;`
- Flyway checksum = signed CRC32 over each line's UTF-8 bytes, with line terminators and BOM stripped.
