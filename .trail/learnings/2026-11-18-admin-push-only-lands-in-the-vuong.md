---
title: Admin push only lands in the inbox until production has push enabled and VAPID keys
author: Vuong
date: 2026-11-18
branch: fix/push-notifications
id: l-4968dffc
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/notifications/config/NotificationPushProperties.java
  - src/test/java/com/erg/ergspringboot/notifications/internal/NotificationDeviceServiceWebPushTest.java
---

## Decided
Diagnosed 2026-11-18 (read-only on production): the .env had push disabled, no VAPID keys and no Firebase project id, but GOOGLE_APPLICATION_CREDENTIALS was set. web-push-config therefore reported disabled, the frontend never asked for permission, no device registered (0 rows), and push campaigns wrote no outbox rows, so messages reached only the in-app inbox.
A blank push provider now falls back to Web Push unless FCM can actually send (fcmReady). Before this, a stray GOOGLE_APPLICATION_CREDENTIALS alone chose FCM and reported push off even with VAPID keys set.

## Watch out
Turning push on for real needs a server change by the owner: generate VAPID keys, set NOTIFICATIONS__PUSH__ENABLED=true, NOTIFICATIONS__PUSH__PROVIDER=webpush, NOTIFICATIONS__WEB_PUSH__PUBLIC_KEY / PRIVATE_KEY / SUBJECT, then redeploy. Rotating VAPID keys invalidates every existing browser subscription.

In the code: NotificationPushProperties.resolvedProvider(), NotificationDeviceService.webPushConfig, test NotificationDeviceServiceWebPushTest.blankProviderFallsBackToWebPushWhenFcmCannotSend.
