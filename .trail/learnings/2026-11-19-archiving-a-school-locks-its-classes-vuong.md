---
title: "Archiving a school locks its classes, students and logins, and restore reopens only what that archive locked"
author: Vuong
date: 2026-11-19
branch: fix/school-archive-locks
id: l-ea7a4225
scope: repo
status: active
src: agent
---

## Decided
- Archiving a school archives its open classes and students and sets the linked student accounts to INACTIVE, with their sessions revoked. Restore puts back the saved status of each locked row, but only if the row is still in the state the archive left it in.
- A paused school locks nothing and its students still log in. The owner decided this on 19/11/2026.
- An account that is also linked to an open student record at another school (a transfer) is not locked.

## Tried and ruled out
- Refusing to archive while classes are open (the old ConflictException). The owner wants one action that locks everything and can be undone.

## Watch out
- Integration tests that insert academic_classes need an education_units row first (FK fk_academic_classes_school).

In the code: table school_archive_holds (V172), SchoolArchiveHolds, SchoolCommandService.archive/restore.
