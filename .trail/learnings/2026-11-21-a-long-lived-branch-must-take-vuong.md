---
title: "A long-lived branch must take its migration numbers after main's, and renumber before the PR"
author: Vuong
date: 2026-11-21
branch: fix/student-import-identity
cost: { minutes: 13, tokens: 151906 }
id: l-e4efacab
scope: repo
status: active
src: agent
touches:
  - contracts/graphql/schema.graphqls.sha256
  - contracts/openapi/erg-openapi.json
  - contracts/openapi/erg-openapi.sha256
  - docs/architecture/api-inventory.md
  - ops/docker/nginx/native.conf
  - src/main/java/com/erg/ergspringboot/auth/SessionService.java
  - src/main/java/com/erg/ergspringboot/auth/SessionUnitScopeResolver.java
  - src/main/java/com/erg/ergspringboot/calendar/CalendarCatalogSearchResponse.java
  - src/main/java/com/erg/ergspringboot/calendar/CalendarOperations.java
  - src/main/java/com/erg/ergspringboot/calendar/CalendarReadPort.java
---

## Decided
Before opening a PR from a branch that adds Flyway migrations, fetch main and compare version numbers. If main has taken numbers the branch also uses, merge main and move the branch's migrations up so they come after main's. Renumbering is safe only while the branch's migrations have never run on a shared database.

## Tried and ruled out
Keeping the branch's numbers and deploying: Flyway stops at startup with "Migration checksum mismatch" because a database migrated by main already recorded different files under those versions.

## Watch out
- After renaming, delete the stale copies under target/classes/db/migration or the old and new files both load locally.
- Comments in Java and SQL that cite a version number (index names, "validated by Vnnn") must move with it.
- main's tests may stub collaborators the old way; after a merge, a login test failed until it used the branch's stubLogin helper.

In the code: src/main/resources/db/migration/postgres (this branch's V172-V196 became V174-V198; main owns V172 school_archive_holds and V173 user_sessions_remember_staff), src/test/java/com/erg/ergspringboot/auth/AuthOperationsFlowTest.java.
