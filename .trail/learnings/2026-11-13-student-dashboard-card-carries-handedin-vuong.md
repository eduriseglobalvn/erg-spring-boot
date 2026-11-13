---
title: Student dashboard card carries handedIn
author: Vuong
date: 2026-11-13
branch: fix/handed-in-flag
id: l-24138882
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/elearning/internal/readmodel/StudentDashboardCards.java
---

## Decided
- The student dashboard assignment card has handedIn: Boolean!, true when the latest attempt's status is in LmsStatePolicy.completedAttemptStates(). Clients use it instead of keeping their own list of handed-in status strings (the FE list had 'done', which no attempt ever has).
- It follows the latest attempt, not handedInAt: a learner who handed in and opened a retake sees the retake as in progress, as the FE did before with its status list.

## Watch out
- The schema is assembled from src/main/resources/graphql/modules: edit the module, run node scripts/assemble-graphql-schema.cjs --write, then rewrite contracts/graphql/schema.graphqls.sha256 with checksumFile(canonicalSchema(schema)) from scripts/graphql-schema-contract-lib.cjs and run node scripts/check-graphql-schema.cjs.
