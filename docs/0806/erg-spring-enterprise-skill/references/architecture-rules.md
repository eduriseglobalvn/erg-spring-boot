# Architecture rules for erg-spring

## Layer responsibilities

Controller:

- Map route, parse path/query/body.
- Build `UserAccessContext` from verified principal/requested scope.
- Call service.
- Wrap result in `ApiResponse`.
- Do not contain permission branching beyond simple route context.
- Do not query repositories.

Service:

- Owns business rules.
- Owns permission/scope checks.
- Owns transaction boundary.
- Calls repositories/read-model services.
- Throws typed API exceptions.

Read-model service:

- Used for heavy dashboards/workspaces.
- Read-only transaction.
- Uses projections and batch repository calls.
- Assembles screen DTOs.
- Applies field-level permissions.
- Can use cache, but cache key must include tenant/scope/user when data is user-specific.

Repository:

- Uses tenant and scope in query method.
- Supports pagination.
- Uses projections for summaries/cards.
- Avoids `findAll()` outside small static/admin reference data.

DTO/mapper:

- Converts entity to public contract.
- Converts projection to public contract for heavy reads.
- Removes sensitive/internal fields.
- Keeps public names stable.

Projection:

- Represents selected DB/read-model columns, not the public API contract.
- Should be record/interface based.
- Should live near repository/read-model code.
- Should be named by read purpose: `*RowProjection`, `*CardProjection`, `*SummaryProjection`, `*OptionProjection`, `*DetailProjection`.
- Should not include sensitive fields unless the read-model service explicitly checks permission before using them.
- Is mandatory for heavy LMS/LCMS/HocLieu workspaces, reports, dashboards, and GraphQL reads.
- Should be designed by screen/read-model shape, not by every tiny nested GraphQL field.

## Package conventions

Prefer:

```text
controller/<domain>
service/<domain>
repository/<domain>
model/<domain>
common/request/<domain>
common/response/<domain>
common/dto/<domain>
platform/security
platform/web
config
```

For new read models, use clear class names:

```text
LmsClassWorkspaceReadService
LmsHomeworkWorkspaceReadService
LcmsQuestionBankReadService
ElearningStudentDashboardReadService
```

## Transactions

- Read endpoints: `@Transactional(readOnly = true)`.
- Commands: `@Transactional`.
- Do not keep transactions open around file streaming or external API calls.
- Do not rely on Open Session in View; it is disabled and should stay disabled.

## JPA fetch strategy

- Prefer projection for heavy list/workspace/report reads.
- Use `@EntityGraph` for detail reads where a small known set of relations is needed.
- Avoid `FetchType.EAGER` on large relations.
- Configure Hibernate batch fetch size only as a secondary guard for remaining lazy relation access.
- Never let a GraphQL nested resolver trigger implicit lazy loading per row.
- Do not solve N+1 by broad entity loading followed by Java filtering.
- Prefer aggregate projections for counts/summaries instead of loading child collections and counting in memory.
- For 5,000 concurrency readiness, every heavy endpoint must pass projection/pagination/query-count integration review before load testing.
- Keep `hibernate.jdbc.timeout` and `jakarta.persistence.query.timeout` configured; add DB slow-query smoke tests because GraphQL deadline alone cannot interrupt a JDBC call already blocked inside the driver/database.
- Treat Virtual Threads as request concurrency support, not as a database scaling fix. Projection, paging, aggregate queries, cache stampede protection, and bounded Hikari usage must be in place first.
- Do not consider a DTO-only implementation enterprise-ready if it loads full entities or lazy collections before mapping.

## Java 25 and concurrency rules

- Virtual Threads can be used to keep Spring MVC synchronous code maintainable while reducing OS-thread pressure.
- Virtual Threads do not increase database capacity; Hikari pool size must stay bounded by DB capacity and instance count.
- If Hikari pending/timeout grows, fix query shape, pagination, cache, read replica routing, or backpressure before increasing pool size.
- Hikari connection timeout protects pool wait time only; it is not a substitute for JPA/JDBC query timeout or database `statement_timeout`.
- Use Redis/distributed cache for multi-instance hot reads; add stampede protection through single-flight/cache lock or `@Cacheable(sync = true)` where appropriate.
- External calls need timeout, Resilience4j circuit breaker, retry limits, and bulkhead policies.
- Native image/GraalVM needs separate benchmark; do not assume JVM GC tuning applies unchanged.

## Cache rules

- Auth principal cache TTL must be short.
- Session/current cache key must include session/user/tenant/current requested scope.
- User-specific resource progress cache must include userId/school/year.
- Taxonomy/static catalog cache can be shared by tenant.
- Evict cache on commands that mutate the cached data.
- For multi-instance deployment, decide Caffeine-local vs Redis-distributed explicitly.

## Placeholder rule

`Map<String,Object>` placeholder services such as workflow/status/accepted are acceptable only as temporary prototype surfaces. Before production:

- replace with typed request/response records.
- persist real state if the operation matters.
- add service-level permission and audit.
- add contract tests.

## Maintainability rule

Favor boring explicit code:

- Typed records over generic maps.
- Projection plus DTO over full entity loading for heavy reads.
- Small services by domain/use case.
- Repository projections over manual object slicing after broad reads.
- Clear method names over hidden magic.
- Tests that prove security behavior rather than comments claiming it.
