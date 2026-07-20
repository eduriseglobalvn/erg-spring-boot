# N+1 Query Patterns Found in Codebase

## 1. Auth Principal — Eager N+1 via Entity Graph — 🔴 CRITICAL

**File:** `repository/auth/AuthSessionRepository.java:17`

```java
@EntityGraph(attributePaths = {
    "user", "user.roles", "user.roles.permissions", 
    "user.permissionOverrides", "user.accessScopes"
})
Optional<AuthSession> findPrincipalByAccessTokenHashAndTenantId(
    String accessTokenHash, String tenantId);
```

**Impact Analysis:**

While `@EntityGraph` reduces the number of queries vs. pure lazy loading, the resulting SQL is a **5-table LEFT OUTER JOIN** that creates a massive Cartesian product. When a User has:
- 3 roles
- Each role has 10 permissions
- User has 5 permission overrides
- User has 3 access scopes

The result set contains: `1 session × 1 user × 3 roles × 30 role_permissions × 5 overrides × 3 scopes = 1,350 rows` for a **single session lookup**.

This is acceptable for auth (once cached), but the **5-second cache TTL** means this query fires far too often.

**Also affects:** `StudentDashboardReadService` at line 88:
```java
User user = userRepository.findByIdAndTenantId(scope.userId(), scope.tenantId()).orElse(null);
```
This `findByIdAndTenantId` does NOT have an `@EntityGraph`, meaning it triggers N+1 queries when the code later accesses `user.getRoles()`, `user.getPermissionOverrides()`, etc.

**Fix:**
1. Increase auth principal cache TTL to 30–60 seconds
2. Add `@EntityGraph` to `UserRepository.findByIdAndTenantId`
3. Consider using a dedicated projection for `ErgPrincipal` that loads only the needed fields

## 2. Calendar Service — Batch Loading Done Right, But Manual — 🟡 MEDIUM

**File:** `service/calendar/CalendarReadService.java`

The Calendar module correctly uses a batch pattern:
```java
List<CalendarEventTeacher> allAssignments = batchLoadTeacherAssignments(tenantId, filteredEvents);
```

However, this is done manually in the service layer, not via GraphQL DataLoader. This means:
- If GraphQL resolves `teachers` on each event independently, it still causes N+1
- The batch loading only works because the service assembles the entire response before returning

The `CalendarDataLoaderRegistry` exists (with `calendarEventTeacherByEventIdLoader` and `calendarEventByBatchIdLoader` beans), but there's **no evidence of these DataLoaders being registered with the GraphQL runtime** or used in the controller code.

**Fix:** Verify that the DataLoader beans are actually wired into the GraphQL execution. Add a `WebGraphQlHandler` or `GraphQlWebSocketHandler` configuration that registers them.

## 3. StudentDashboard — Multiple Sequential Queries Without Batching — 🟡 MEDIUM

**File:** `service/elearning/readmodel/StudentDashboardReadService.java`

The `studentDashboard()` method (lines 83–153) makes **5+ sequential database calls:**
1. `userRepository.findByIdAndTenantId()` — User entity (triggers N+1 for roles/permissions)
2. `studentRepository.findFirstByTenantIdAndAuthUserId()` — Student record
3. `assignmentRepository.findStudentDashboardAssignments()` — Assignments
4. `assignmentRepository.countActiveStudentAssignments()` — Active count
5. `assignmentRepository.countPendingStudentAssignmentActions()` — Pending count
6. `attemptRepository.summarizeStudentDashboardScores()` — Score summary
7. `announcementReadPort.findStudentDashboardAnnouncements()` — Announcements

These are all independent queries that could run in parallel using `CompletableFuture`.

**Additionally:** `User` entity lookups trigger N+1 lazy loading of `roles`, `roles.permissions`, `permissionOverrides`, and `accessScopes` — the same issue as #1.

## 4. HocLieu Library — Taxonomy Count Queries — 🟡 MEDIUM

**File:** `service/hoclieu/readmodel/HocLieuLibraryReadService.java` lines 228–283

The `resourceCountsByTaxonomyKind()` method makes **3 separate count queries** for subjects, categories, and sections:
```java
countResources(nodes, this::isSubject, ids -> resourceRecordRepository.countResourcesBySubjectIds(...));
countResources(nodes, this::isGroup, ids -> resourceRecordRepository.countResourcesByCategoryIds(...));
countResources(nodes, this::isLesson, ids -> resourceRecordRepository.countResourcesBySectionIds(...));
```

These are called sequentially. They could be combined into a single query with a UNION or GROUP BY approach, or at minimum run in parallel.

## 5. LmsReadModelService — Assignment Progress Multi-Query — 🟡 MEDIUM

**File:** `service/lms/readmodel/LmsReadModelService.java`

The `assignmentProgressWorkspace()` method (lines 80–139) makes these sequential queries:
1. `assignmentRepository.findAssignmentDetail()` — Assignment detail
2. `attemptRepository.summarizeAssignmentProgress()` — Progress summary
3. `attemptRepository.findAssignmentAttemptSummaries()` — Paginated attempts
4. `assignmentRepository.findAssignmentRecipients()` — Recipients
5. `attemptRepository.findLatestAttemptScoresForRecipients()` — Latest scores (depends on #4 recipient IDs)
6. `attemptRepository.findBestAttemptScoresForRecipients()` — Best scores (depends on #4 recipient IDs)
7. `assignmentRepository.findAssignmentMissingLateStudents()` — Missing/late students

Queries #5 and #6 could be combined into a single query returning both latest and best scores per student.
Queries #2, #3, #4, #7 could run in parallel.

**Fix options:**
1. Combine #5 and #6 into a single repository method that returns both latest and best in one query
2. Run #2, #3, #4, #7 in parallel with CompletableFuture
3. Consider a single native query that joins assignment + recipients + attempt scores