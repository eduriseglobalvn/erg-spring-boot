---
title: "Students use the LMS API too, so the portal guard lets the student workspace into it"
author: Vuong
date: 2026-11-11
branch: fix/learner-lms-api-access
id: l-159c5c85
scope: repo
status: active
src: agent
touches:
  - contracts/graphql/schema.graphqls.sha256
  - src/main/java/com/erg/ergspringboot/platform/security/AuthorizationCapabilityPolicy.java
  - src/test/java/com/erg/ergspringboot/platform/security/AuthorizationCapabilityPolicyTest.java
---

## Decided

Learners (students) call their own assignments, quiz packages and attempts under `/api/v1/lms`, the same API family teachers use. The portal guard must therefore let a principal that holds only the STUDENT workspace into the `lms` family. What a learner may do inside it is decided by the permission on each endpoint (`lms.*_self`, `lms.quiz.play`, `lms.assignment.submit`), never by the workspace.

Two things are kept apart on purpose:

- The workspaces a principal is *shown* (sidebar, workspace switcher): a learner gets STUDENT only.
- The API families a principal may *enter*: STUDENT may enter `lms` as well as `elearning`.

## Tried and ruled out

- Giving learners the TEACHER workspace so the guard passes. That is what the old `lms.` prefix rule did, and it put a Teacher/Student switcher in the student sidebar.
- Listing the learner endpoints one by one in the guard. One missed path locks every student out of that screen, and the per-endpoint permission already says who may call it.

## Watch out

- Narrowing which grants project TEACHER without checking the portal guard locks students out after login: every `/api/v1/lms` call answers 403 `PORTAL_SESSION_MISMATCH`. `LmsRuntimeControllerContractTest` is the test that shows it; a red run there is a real regression, not a stale test.
- A new teacher-only endpoint under `/api/v1/lms` needs its own permission annotation. The portal guard alone does not keep learners out of it.

In the code: `AuthorizationCapabilityPolicy.hasWorkspace`, `WorkspaceCapabilityMapper.fromLegacyPortal` and `isLearnerLmsToken`, `SecurityUtils.portalFromPath`, callers `ErgAuthenticationFilter.canAccessRequestedPortal`, `ErgPolicyService.decide`, `PermissionAuthorizationManager`.
