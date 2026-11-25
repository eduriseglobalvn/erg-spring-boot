---
title: Async module listeners must set the tenant before any transaction starts
author: Vuong
date: 2026-11-25
branch: fix/module-listener-tenant-scope
id: l-5c3c5950
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/learningstandards/internal/integration/XapiStatementAcceptedConsumer.java
  - src/main/java/com/erg/ergspringboot/notifications/internal/CalendarBatchPublishedConsumer.java
  - src/main/java/com/erg/ergspringboot/notifications/internal/CalendarScheduleChangeConsumer.java
  - src/main/java/com/erg/ergspringboot/notifications/internal/ElearningNoticeConsumer.java
  - src/main/java/com/erg/ergspringboot/notifications/internal/LmsLearningEventConsumer.java
  - src/main/java/com/erg/ergspringboot/timekeeping/internal/teaching/TeachingScheduleListener.java
  - src/test/java/com/erg/ergspringboot/notifications/internal/CalendarBatchPublishedConsumerTest.java
---

## Decided
Every `@ApplicationModuleListener` uses `propagation = Propagation.NOT_SUPPORTED` and wraps its body in `TenantContext.scope(TenantContext.of(event.tenantId(), null, "system"))`. The services it calls (`@Transactional`) then begin inside the tenant.

## Tried and ruled out
The default listener (REQUIRES_NEW) opens its transaction on the async thread before the body runs. There is no TenantContext there, so `TenantAwareJpaTransactionManager` clears `app.tenant_id` and RLS hides every tenant row. Opening the scope inside the body is too late. Production, 25/11/2026: the batch-published consumer logged "batch missing" for batches that were PUBLISHED, so no teacher got a schedule notification.

## Watch out
Integration tests connect as the DB owner, which bypasses RLS. `TeachingSchedulePublishNotificationIT` passed while production failed. `ModuleListenerTenantScopeTest` guards the propagation instead.

In the code: notifications/internal/*Consumer.java, learningstandards/internal/integration/XapiStatementAcceptedConsumer.java, timekeeping/internal/teaching/TeachingScheduleListener.java, platform/events/ModuleListenerTenantScopeTest.java
