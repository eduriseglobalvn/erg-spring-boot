---
title: Quiz editor showing offline while the network is fine means the R2 bucket is blocking image uploads (CORS)
author: Vuong
date: 2026-11-19
branch: fix/student-import-identity
cost: { minutes: 27, tokens: 553648 }
id: l-03e535be
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/learningstandards/internal/storage/R2LearningStandardsArtifactStorage.java
  - src/main/java/com/erg/ergspringboot/learningstandards/internal/storage/FileSystemLearningStandardsArtifactStorage.java
  - src/test/java/com/erg/ergspringboot/platform/security/RuntimePermissionCatalogContractTest.java
  - src/main/java/com/erg/ergspringboot/audit/capture/AuditOutboxWriter.java
  - src/main/java/com/erg/ergspringboot/authorization/web/AssignableRolesController.java
---

## Decided
If the quiz editor says "Mất kết nối" while the network is fine, first check the CORS rules on the R2 bucket `erg`. The browser uploads quiz images straight to R2 with a presigned PUT. When the bucket has no CORS rule for the frontend origin, the browser blocks the PUT, and the upload code reports it as a network error. The sync engine then marks the editor offline and retries forever. The draft is saved only after the images upload, so every pending edit stays stuck.

## Tried and ruled out
- Real connectivity loss, API timeouts, and backend errors. nginx shows `POST /api/v1/lcms/media/presign` returning 200 about every 31s, with no upload ever following.

## Watch out
- A preflight to the R2 endpoint with `Origin: https://lms.erg.edu.vn` returned 403 and no Access-Control headers (2026-11-19).
- No CORS config for the bucket exists in either repo. It lives only in the Cloudflare dashboard.
- The fix needed on R2: a CORS rule allowing PUT and GET with header content-type from the frontend origins.

In the code: src/features/lcms/quiz/quiz-editor/sync/upload-inline-images.ts (fetch failure becomes NETWORK_ERROR, status 0); quiz-draft-sync-engine.ts classifySyncError and sendOnce (uploadImages runs before saveDraft).
