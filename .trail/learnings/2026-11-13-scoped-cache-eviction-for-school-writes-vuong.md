---
title: Scoped cache eviction for school writes
author: Vuong
date: 2026-11-13
branch: perf/scoped-cache-eviction
id: l-77cc93d6
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/platform/cache/ErgTieredCache.java
  - src/main/java/com/erg/ergspringboot/lms/internal/core/LmsOperationalCommandService.java
  - src/main/java/com/erg/ergspringboot/lms/internal/core/StudentGroupCommands.java
  - src/main/java/com/erg/ergspringboot/lms/internal/classroom/LmsClassroomQueryUseCase.java
---

@CacheEvict(allEntries = true) on an ErgTieredCache does NOT wipe every tenant: ErgTieredCache.clear() clears only the tenant in TenantContext (L1 + tenant generation), so the PERF-05 finding 'one school's write empties all tenants' was wrong; the real cost is the whole tenant's entries. To narrow further, the cache needs a scopeOf in its descriptor AND every @Cacheable key must carry the resolved scope value: lmsClassesBySchool used to key a blank schoolId as null, which a school-scoped eviction would never reach, so its key now uses the resolved school. Eviction is done programmatically through LmsReadCacheInvalidator.evictScopesAfterCommit inside the service, not by annotation, because bulkEnroll/importStudents call enroll/createStudent on 'this' and proxy annotations do not fire on self-calls. lmsStudentGroups and lcmsSchoolDirectory stay tenant-wide: a group shows in lists keyed by class, owner, allowed-class set or '*', and directory pages span many schools, so no single scope owns an entry.
