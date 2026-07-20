# Firebase Push Notification -- FE Integration Report

**Date:** 2026-06-14
**Branch:** `codex/lms-graphql-security-audit-plan` (BE)
**Domain:** erg-system (FE)

---

## 1. Summary -- What Exists vs What Needs Build

This report covers the frontend (erg-system) plan for Firebase Cloud Messaging push notification integration. The BE backend (`erg-spring`) already has all required APIs ready. The FE work has not yet been started on this branch -- this report documents the plan, contract, and implementation gaps for the FE team.

### Backend API Contract (Already Live)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/notifications` | GET | List inbox (paginated, portal-scoped) |
| `/api/v1/notifications/unread-count` | GET | Badge count |
| `/api/v1/notifications/{id}` | GET | Detail |
| `/api/v1/notifications/{id}/read` | PATCH | Mark read |
| `/api/v1/notifications/read-all` | PATCH | Mark all read |
| `/api/v1/notifications/{id}` | DELETE | Soft-archive |
| `/api/v1/notifications/preferences` | GET/PUT | Event-type preferences |
| `/api/v1/notification-devices` | POST | Register device (FID-first) |
| `/api/v1/notification-devices/unregister` | POST | Unregister device |
| `/api/v1/notification-devices/heartbeat` | POST | Update last_seen |
| `/api/v1/notification-devices/{id}` | DELETE | Revoke device |

### Current FE State (Pre-FCM Migration)

| File | Status |
|------|--------|
| `public/pwa-push.js` | Custom Web Push listener (not FCM) |
| `src/features/lms/mobile/hooks/use-pwa-notifications.ts` | Uses `PushManager.subscribe()` with custom VAPID key |
| `src/features/lms/mobile/components/lms-pwa-setup-sheet.tsx` | Has enable/install UX |
| `src/features/lms/components/lms-notification-center.tsx` | Uses mock data (`lmsNotifications`) |
| `src/features/lms/components/lms-notification-detail-page.tsx` | Uses mock data |
| `src/features/lms/components/lms-notification-detail-page.tsx` | Student dashboard has local `discussionNotifications` |
| `package.json` | Has React 19, Vite 8, TanStack Query/Router, vite-plugin-pwa -- **no `firebase` dependency** |

---

## 2. Tests Added

**None yet.** All FE tests need to be created as part of the implementation phases described below.

### Planned Test Coverage

- **Unit/Vitest**: env validation, API client request paths, hook states (unsupported/unconfigured/default/enabled/blocked), foreground message invalidation of query keys, click deeplink sanitizer
- **SW tests**: `notificationclick` focus/move-to-existing-tab, `onBackgroundMessage` showNotification with correct payload
- **Manual browser matrix**: Chrome desktop HTTPS, Chrome Android PWA, Edge desktop, Safari iOS (graceful unsupported)
- **Load/UX**: 5,000 concurrent unread count does not poll every second; TanStack Query stale time 30-60s + foreground invalidation

---

## 3. Gap Closure Status -- Per Item from FE Plan

### Phase 0: Firebase Environment Setup

| Item | Status | Notes |
|------|--------|-------|
| Add `firebase` npm dependency | **Not Started** | `firebase ^12.14.0` needed |
| Add `VITE_FIREBASE_*` env variables | **Not Started** | 7 vars + VAPID key |
| Create `firebase-env.ts` (validate + state) | **Not Started** | Graceful `unconfigured` state |
| Create `firebase-app.ts` (singleton) | **Not Started** | No Analytics |
| Create `firebase-messaging.ts` (lazy, isSupported) | **Not Started** | Register/onRegistered/onMessage/onUnregistered |
| Update `.env.example` | **Not Started** | |

### Phase 1: Service Worker FCM-Aware

| Item | Status | Notes |
|------|--------|-------|
| Migrate to `injectManifest` (from `generateSW`) | **Not Started** | Required for bundled Firebase in SW |
| Create `firebase-messaging-sw.ts` | **Not Started** | Modular SDK, custom click handler before FCM init |
| Migrate `pwa-push.js` logic into new SW | **Not Started** | Keep Workbox caching intact |
| Register notificationclick handler before FCM | **Not Started** | Prevents FCM overriding click |

### Phase 2: Registration Lifecycle

| Item | Status | Notes |
|------|--------|-------|
| Replace `use-pwa-notifications.ts` with FID-first hook | **Not Started** | Use `register()` + `onRegistered()` |
| POST device to BE on `onRegistered(fid)` | **Not Started** | `POST /api/v1/notification-devices` |
| Heartbeat on foreground/online | **Not Started** | `POST /api/v1/notification-devices/heartbeat` |
| Disable/unregister flow | **Not Started** | `onUnregistered(fid)` + BE call |
| Compatibility fallback: `getToken()` if needed | **Not Started** | Feature-flag gated with `TODO` |

### Phase 3: Inbox API Integration

| Item | Status | Notes |
|------|--------|-------|
| Create `features/notifications/` module | **Not Started** | API client, hooks, components |
| Replace LMS mock notification center with API | **Not Started** | Fetch paginated inbox |
| Replace LMS mock notification detail with API | **Not Started** | Fetch detail by ID |
| Add unread count badge | **Not Started** | `GET /unread-count`, stale 30-60s |
| Foreground `onMessage` toast + cache invalidation | **Not Started** | `sonner` toast, invalidate unread count |
| Route notification detail marks read | **Not Started** | `PATCH /{id}/read` on mount |
| Remove `lmsNotifications` mock | **Not Started** | |

### Phase 4: Elearning/Student

| Item | Status | Notes |
|------|--------|-------|
| Share notification components for student | **Not Started** | Reuse `features/notifications/` |
| Student inbox for assignments/announcements | **Not Started** | Portal=`elearning` |
| Keep local discussion if no BE event | **Not Started** | Separate from FCM inbox |

### Phase 5: Hardening + Tauri Adapter

| Item | Status | Notes |
|------|--------|-------|
| Create `NotificationRuntimeAdapter` interface | **Not Started** | `isSupported()`, `enable()`, `disable()`, `heartbeat()`, `onForegroundMessage()` |
| Web/PWA adapter as first implementation | **Not Started** | |
| Tauri desktop adapter placeholder | **Not Started** | Inbox polling/SSE/native notification |
| E2E Playwright tests | **Not Started** | Permission mock, foreground message, notification click |

---

## 4. Remaining Items (All -- Nothing Started)

| Priority | Phase | Item | Notes |
|----------|-------|------|-------|
| P0 | 0 | Add `firebase` dependency + env vars | First blockers |
| P0 | 1 | Migrate SW to `injectManifest` + FCM-aware | Needed for background push |
| P0 | 2 | Implement registration lifecycle (FID-first) | Core integration with BE |
| P0 | 3 | Implement inbox API integration (LMS teacher) | Replaces mock data |
| P1 | 3 | Add unread count badge + foreground toast | UX must-have |
| P1 | 4 | Student inbox (portal=elearning) | After LMS phase |
| P2 | 5 | NotificationRuntimeAdapter + Tauri placeholder | Future-proofing |
| P2 | 5 | E2E Playwright tests | Hardening |

---

## 5. Dependencies on BE

The BE team has completed all required endpoints. The FE team can start immediately using:

| BE Requirement | Status | Notes |
|----------------|--------|-------|
| All self notification API endpoints | **Done** | 8 endpoints |
| Device registration API | **Done** | 4 endpoints |
| Admin notification API | **Done** | 3 endpoints |
| Token encryption opt-in | **Done** | Not needed for dev |
| FCM HTTP v1 sender | **Done** | FE uses public FCM JS SDK |
| Outbox worker | **Done** | No FE dependency |

**No BE blockers for FE implementation.**

---

## 6. Overall Readiness Score

**0% (FE work not started)**

The BE is at 85% readiness and all APIs are available. The FE implementation across all 5 phases has not begun on this branch. Estimated effort: 3-4 weeks for a full P0-P1 implementation (Phases 0-3 for LMS teacher), with additional 2 weeks for Phase 4 (student) and Phase 5 (hardening/adapter).
