---
title: On a phone the student quiz page must scroll as a page; only the web player keeps a fixed-height frame
author: Vuong
date: 2026-11-10
branch: feat/timekeeping
cost: { minutes: 34, tokens: 528260 }
id: l-c892fd95
scope: repo
status: active
src: agent
touches:
  - src/test/java/com/erg/ergspringboot/lms/internal/quiz/runtime/QuizRuntimePackagePlayerSettingsTest.java
  - src/main/resources/db/migration/postgres/V136__student_self_password_change.sql
---

## Decided
The student quiz session locks `height: 100vh; overflow: hidden` only from 768px (the web player, which scrolls inside itself). Below that the page scrolls normally.

## Tried and ruled out
Locking the frame at every size: on a phone the question below the fold was clipped, and focusing a matching piece made iOS scroll the hidden frame, which looked like a zoom/broken layout.

## Watch out
- The mobile player's content grid needs `gridTemplateColumns: minmax(0, 1fr)`: a plain `auto` track keeps the widest min-content from the first paint and the page overflows sideways.
- Touch drag (matching, sequence) starts at once only from the grip handle (`data-drag-handle`, `touch-action: none`); elsewhere TouchSensor waits for a press, so the page can still scroll. A plain PointerSensor grabs the finger on iPad and loses it when the page starts scrolling.
- A student whose session shows TEACHER in `workspaces` is a backend mapping problem (learner `lms.*_self` grants were mapped to TEACHER), fixed in erg-spring WorkspaceCapabilityMapper; the FE only hides the switcher when no non-STUDENT workspace exists.

In the code: student-dashboard-quiz-session.tsx, mobile-player-shell.tsx, matching-question/non-touch-pointer-sensor.ts, puzzle-piece.tsx (GripHandle), student-dashboard-top-menu.tsx.
