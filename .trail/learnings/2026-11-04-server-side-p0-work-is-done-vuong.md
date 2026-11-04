---
title: Server-side P0 work is done by the repo owner; code fixes ship via forward migrations
author: Vuong
date: 2026-11-04
branch: claude/fix-audit-findings
cost: { minutes: 1, tokens: 1237 }
id: l-44a4c9c6
scope: repo
status: active
src: agent
touches:
  - ops/postgres/predeploy-p0.sql
  - src/main/resources/db/migration/postgres/V126__force_super_admin_password_change.sql
  - docs/second-brain/review/README.md
---

## Decided
- Server-only remediation (rotating Postgres/Redis/SSH credentials, setting production secrets, redeploying nginx) is done by the repo owner, not by an agent. The agent may run the read-mostly predeploy gate on the server DB when the owner asks.
- The predeploy gate was run on the server on 2026-11-04: 0 duplicate keys for V88, 0 still-active sessions from the leaked dump. The owner confirmed the production DB/Redis passwords were rotated.
- The super admin temporary password set by V110 is forced to change via a separate forward migration that only matches the V110 hash, so an admin who already rotated is not affected.

## Tried and ruled out
- Editing V110 to guard its overwrite: rejected, migrations are append-only and V110 is already applied on dev databases (checksum mismatch, same failure as the earlier V108 incident).
- Agent rotating production credentials or reading production env vars: refused / blocked; credential changes stay with the owner.

## Watch out
- The server DB was at V107. The next deploy runs V108–V126 at once; production refuses to start unless ERG_OUTBOX_ENCRYPTION_KEY and AUDIT_REDACTION_HMAC_SECRET are set (fail-closed checks). Verify both on the server before deploying.
- After that deploy the super admin must change the password at first login.

In the code:
- Gate: `psql "$TARGET_URL" -v ON_ERROR_STOP=1 -f ops/postgres/predeploy-p0.sql` (on the server it was piped into the postgres container's psql).
- V126 forces must_change_password for usr_super_admin; SuperAdminForcedPasswordChangeMigrationContractTest keeps its hash equal to V110's.
