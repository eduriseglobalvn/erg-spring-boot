---
title: "A browser's push subscription belongs to whoever is signed in on it now"
author: Vuong
date: 2026-11-24
branch: fix/student-import-identity
cost: { minutes: 13, tokens: 266003 }
id: l-e8a29263
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/common/request/users/UserRequests.java
  - src/main/java/com/erg/ergspringboot/notifications/internal/NotificationDeviceService.java
  - src/test/java/com/erg/ergspringboot/notifications/internal/NotificationDeviceServiceWebPushTest.java
---

## Decided
When a second account signs in on a browser that already has a push subscription, the server moves that device row to the new account instead of refusing. Before, register threw "already registered to another user" and the PWA hook gave up, so the first account (e.g. a teacher who once signed in on a colleague's PC) kept receiving the reminders on that PC.

## Watch out
- Heartbeat must reject a revoked row, and the hook re-registers when a heartbeat is rejected, otherwise the browser heart-beats a dead row forever.
- Logout does not unregister push; the previous account still gets pushes on that browser until someone else signs in and opens the app with permission granted.

In the code: erg-spring NotificationDeviceService.register / findHeartbeatDevice; frontend syncRegistrationWithApi in src/features/lms/mobile/hooks/use-pwa-notifications.ts.
