---
title: "Background workers must write results inside the row's tenant scope, or RLS silently hides the row"
author: Vuong
date: 2026-11-18
branch: feat/role-consolidation
id: l-a564ed8f
scope: repo
status: active
src: agent
---

## Decided
Every DB write a background worker makes for a tenant-owned row runs inside `TenantContext.scope(TenantContext.of(tenantId, null, "system"))`, including the error path. The runtime role `erg_app` has no BYPASSRLS, so with no `app.tenant_id` set a lookup returns nothing, without raising any error.

## Tried and ruled out
- Production showed onboarding verification mails stuck in `processing` with attempts 0, re-claimed every 60 s, and no log lines.
- SMTP env, TLS reachability, the encryption key and RLS visibility under tenant `erg` were all fine.
- The cause was the failure transition. It ran in the `catch` after try-with-resources had already closed the tenant scope, so `findLeasedForUpdate` found no row and `ifPresent` did nothing.

## Watch out
- A try-with-resources `catch` runs after the resource is closed. Do not put tenant-scoped DB work in it.
- The worker now logs failures with the exception type only, because provider messages echo the recipient address. Read the real send error from that log or from `outbox_events.last_error`.

In the code: auth/internal/outbox/TransactionalOutboxWorker.java (dispatch / dispatchInScope / transition); test TransactionalOutboxWorkerTest.transientProviderFailureMovesEventToRetryWithLeaseCleared.
