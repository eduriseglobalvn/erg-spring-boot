# ERG Spring Boot — Full Source Audit Summary
**Date:** 2026-06-16  
**Branch:** codex/lms-graphql-security-audit-plan  
**Auditor:** Claude Code  

---

## Executive Summary

After a thorough scan of the entire codebase (GraphQL controllers, services, repositories, security config, caching, JPA models, and application config), **8 categories of issues** have been identified. The most critical finding is the **GraphQL API latency problem (1–2 seconds)**, which stems from a combination of N+1 authentication queries and the absence of DataLoader in most GraphQL resolvers.

| # | Category | Severity | Issue Count |
|---|----------|----------|-------------|
| 1 | **GraphQL Performance (Root Cause of 1–2s latency)** | 🔴 Critical | 3 |
| 2 | **N+1 Query Patterns** | 🔴 Critical | 2 |
| 3 | **Security Vulnerabilities** | 🟡 Medium | 5 |
| 4 | **JPA / Hibernate Issues** | 🟠 High | 3 |
| 5 | **Data Integrity / Logic Bugs** | 🟠 High | 2 |
| 6 | **Cache Misconfiguration** | 🟢 Low | 1 |
| 7 | **Observability / Logging** | 🟡 Medium | 2 |
| 8 | **Architecture Debt** | 🟡 Medium | 2 |

**Detailed reports:**
- [01-graphql-performance.md](./01-graphql-performance.md) — Root cause analysis of 1–2s API latency
- [02-n-plus-one-queries.md](./02-n-plus-one-queries.md) — N+1 query patterns found
- [03-security-vulnerabilities.md](./03-security-vulnerabilities.md) — Security audit findings
- [04-jpa-hibernate-issues.md](./04-jpa-hibernate-issues.md) — JPA entity & query issues
- [05-data-integrity-bugs.md](./05-data-integrity-bugs.md) — Logic and data integrity bugs
- [06-caching-improvements.md](./06-caching-improvements.md) — Cache tuning recommendations
- [07-observability-improvements.md](./07-observability-improvements.md) — Logging & metrics gaps
- [08-optimization-plan.md](./08-optimization-plan.md) — Prioritized implementation plan with effort estimates

## Top 5 Priority Fixes (Immediate Impact on Latency)

1. **Add `@Cacheable` to `ErgPrincipalService.fromBearerToken`** — Currently only 5s TTL on auth cache, and the `@Cacheable` key expression uses `#root.target.cacheTokenHash()` which can cause cache misses. The JWT/session lookup + N+1 role/permission fetch runs on every single GraphQL request.
2. **Reduce auth cache TTL from 5s to a longer window** (e.g. 30–60s) — The 5-second auth principal cache means every session makes ~12 DB round-trips per minute per active user.
3. **Add `@EntityGraph` to `UserRepository.findByIdAndTenantId`** used in `StudentDashboardReadService` — This triggers 1+ N+1 queries for roles, permissions, and access scopes.
4. **Add DataLoader for non-Calendar GraphQL fields** — Only Calendar has DataLoaders; LMS, HocLieu, eLearning, LCMS do not.
5. **Add `@Cacheable` to frequently-called read model queries** in `LmsReadModelService`, `HocLieuLibraryReadService`, and `StudentDashboardReadService`.