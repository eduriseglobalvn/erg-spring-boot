# HocLieu Library Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build separate HocLieu runtime/admin bootstrap APIs so `/kho-hoc-lieu` renders from one scoped read model instead of stitching taxonomy and resource APIs in the frontend.

**Architecture:** Convert HocLieu domain objects into JPA-backed entities, add focused repositories, and move UI-shaped response assembly into `HocLieuService`. Runtime endpoints return only published/open resources allowed by the user's managed school scope; admin endpoints return studio authoring data and keep CRUD separate.

**Tech Stack:** Spring Boot 4, Java 25, Spring MVC, Spring Security, Spring Data JPA, Hibernate JSON columns, PostgreSQL/H2.

---

### Task 1: Make HocLieu Models Persistable

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/model/hoclieu/TaxonomyNode.java`
- Modify: `src/main/java/com/erg/ergspringboot/model/hoclieu/ResourceRecord.java`
- Modify: `src/main/java/com/erg/ergspringboot/model/hoclieu/ResourceItemRecord.java`
- Modify: `src/main/java/com/erg/ergspringboot/model/hoclieu/AssetRecord.java`

- [ ] Add `@Entity`, `@Table`, `@Column`, and JSON mapping where needed.
- [ ] Keep each model extending `BaseEntity`.
- [ ] Add direct `ResourceRecord -> ResourceItemRecord/AssetRecord` relationships for read model assembly.

### Task 2: Add Repository Layer

**Files:**
- Create: `src/main/java/com/erg/ergspringboot/repository/hoclieu/TaxonomyNodeRepository.java`
- Create: `src/main/java/com/erg/ergspringboot/repository/hoclieu/ResourceRecordRepository.java`
- Create: `src/main/java/com/erg/ergspringboot/repository/hoclieu/ResourceItemRecordRepository.java`
- Create: `src/main/java/com/erg/ergspringboot/repository/hoclieu/AssetRecordRepository.java`

- [ ] Add scoped taxonomy queries by tenant/kind/status.
- [ ] Add scoped resource queries by tenant/status/visibility and optional subject/category/lesson filters.
- [ ] Add asset/item lookup by resource IDs.

### Task 3: Add Typed Response DTOs

**Files:**
- Create: `src/main/java/com/erg/ergspringboot/common/response/hoclieu/HocLieuLibraryResponses.java`

- [ ] Define runtime bootstrap records: `Subject -> Group -> Lesson -> Resource`.
- [ ] Define admin studio bootstrap records: taxonomy buckets, resources, file types, designer presets.

### Task 4: Implement Read Model Service

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/service/hoclieu/HocLieuService.java`

- [ ] Replace in-memory `ConcurrentHashMap` demo data with repository queries.
- [ ] Add `libraryBootstrap(schoolId, academicYear, access)` with strict school scope validation.
- [ ] Add `studioBootstrap(access)` for admin authoring data.
- [ ] Keep resource CRUD methods, but make list/detail scoped by query parameters and user access.
- [ ] Add short comments before non-obvious assembly and authorization blocks.

### Task 5: Refactor Controller Surface

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/controller/hoclieu/HocLieuController.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java`

- [ ] Add `GET /api/v1/hoclieu/library/bootstrap`.
- [ ] Add `GET /api/v1/admin/hoclieu/studio/bootstrap`.
- [ ] Keep admin CRUD paths under `/api/v1/admin/hoclieu`.
- [ ] Keep runtime resource list/detail under `/api/v1/hoclieu`.
- [ ] Stop using a single controller mapping that aliases runtime/admin behavior together.

### Task 6: Verify

**Files:**
- Modify existing controller tests only if signatures change.

- [ ] Run `./mvnw.cmd -q -DskipTests compile`.
- [ ] Run focused tests for HocLieu and security contracts if compile passes.
- [ ] Report deprecated runtime calls and new replacement endpoints.
