# JPA / Hibernate Issues

## 1. User Entity Has 11 Lazy Collections — Potential N+1 Nightmare — 🔴 HIGH

**File:** `model/users/User.java`

The `User` entity has 11 lazy-loaded collections:
- `roles` (ManyToMany)
- `permissionOverrides` (OneToMany)
- `sessions` (OneToMany)
- `socialAccounts` (OneToMany)
- `activities` (OneToMany)
- `targetedActivities` (OneToMany)
- `profile` (OneToOne)
- `certificates` (OneToMany)
- `workShifts` (OneToMany)
- `accessScopes` (OneToMany)
- `loginAttempts` (OneToMany)

**Problem:** When `ErgPrincipalService.fromBearerToken()` loads a User via `@EntityGraph`, it only loads 5 of these (`roles`, `roles.permissions`, `permissionOverrides`, `accessScopes`). But any code that later accesses the remaining collections within the same Hibernate session triggers N+1 queries.

The `StudentDashboardReadService` calls `userRepository.findByIdAndTenantId()` **without** an `@EntityGraph`, so it triggers N+1 on the first collection access.

**Fix:** Use dedicated projections instead of loading the full `User` entity when only a subset of fields is needed.

## 2. Hibernate DDL Auto = Validate, But No Flyway Active — 🟡 MEDIUM

**File:** `application.yml` line 52

```yaml
jpa:
  hibernate:
    ddl-auto: validate
```

```yaml
flyway:
  enabled: false
```

Schema validation is enabled (`ddl-auto: validate`) but Flyway is disabled by default. This means:
- In dev, there must be another mechanism to create/migrate the schema (maybe manual SQL scripts)
- In production, the schema must already exist and match the entity model exactly

**Risk:** If schema migration is manual, it's easy for the entity model and DB schema to diverge, causing startup failures.

**Fix:** Enable Flyway in production and create proper migration scripts.

## 3. `open-in-view: false` Is Correct — 🟢 GOOD

**File:** `application.yml` line 51

```yaml
open-in-view: false
```

This is correctly set to `false`. OSIV (Open Session In View) would cause lazy-loading N+1 queries in the view/controller layer. With it disabled, all data must be fetched within the service layer, which is the correct pattern.

However, this means any lazy-loaded collection accessed outside a `@Transactional` context will throw a `LazyInitializationException`. The services use `@Transactional(readOnly = true)`, which is correct.

---

## Summary

| # | Issue | Severity | Effort |
|---|-------|----------|--------|
| 1 | User entity N+1 with 11 lazy collections | High | Medium |
| 2 | Flyway disabled, manual schema management | Medium | Large |
| 3 | OSIV disabled (correct) | Good | N/A |