# GraphQL N+1, Cache, and Heavy-Read Latency Design

**Status:** Approved by the user on 2026-07-17

## Goal

Make every supported GraphQL read and the highest-frequency REST reads bounded, tenant-correct, fixture-backed, and measurable. Interactive reads target p95 <= 500 ms and p99 <= 1 second on the accepted local topology. Heavy exports and unbounded reports return an asynchronous job acknowledgement within 500 ms instead of performing the full computation in the request.

## Non-negotiable constraints

- Preserve existing business behavior and public payloads unless a separately documented FE contract change is unavoidable.
- Never cache an authorization allow decision across requests.
- Every cache key includes tenant and every user/scope dimension that changes the result.
- No repository call is permitted inside a response-item mapper or cardinality-dependent loop.
- Query-count slope must remain constant at fixture cardinalities 1, 10, 100, and 1,000.
- Native Image, Redis outage fallback, RLS, and PostgreSQL correctness remain release gates.
- A source change cannot be described as proving sub-second latency; only retained benchmark evidence can do that.

## Architecture

### Observation and budgets

Each GraphQL coordinate is mapped to a stable catalog ID. Request-local observation records business SQL statements, rows, payload bytes, cache hit/miss/load, and resolver latency. Query-slope tests use PostgreSQL and deterministic fixtures. The default budgets are 3 statements for taxonomy trees, 5 for quiz/question-bank reads, 8 for interactive workspaces/reports, and 25 for one expanded GraphQL operation.

### Read models

- Organization/curriculum trees use one flat projection or recursive CTE and build the nested DTO without further database access.
- Learning-content placement and assets use bounded projections and batch `IN` reads; unselected large JSON/media fields are not materialized.
- Quiz workspace, version, section, question, option, and package reads use bounded batches. Immutable published packages are version-keyed.
- Teacher reports and student ability statistics read aggregate projections. Corrections enqueue reconciliation. Large reports remain jobs.
- Student and teacher workspaces authorize first, then load selected sections with bounded repository methods.

### GraphQL cache policy

Redis 8.8 is L2 and optional Caffeine is L1. Cache keys contain `catalogId`, tenant, normalized input, normalized scope/user identity when required, selection-set fingerprint, and data generation. Loads use single-flight. Mutations increment exact generation keys after commit. Security-sensitive reads fall back to PostgreSQL and fail closed when Redis is unavailable. Mutation results, volatile job state, and provider-dependent responses are not business-cached.

TTL classes:

- immutable published quiz package: 24 hours plus explicit version key;
- curriculum/taxonomy/catalog: 15 minutes plus tenant/domain generation;
- workspace/dashboard: 30–60 seconds plus tenant/user/scope generation;
- aggregate report preview: 30 seconds plus assignment/quiz generation;
- authorization/session: no cross-request allow cache.

### Fixture contract

The deterministic local fixture covers ten tenants, organization units/classes, subject-level-topic trees, learning-content placements, quiz versions/sections/questions/options, student attempts and answers, progress corrections, score distributions, question analytics, reports, calendar, and activity history. The manifest contains expected row counts and the seed script is idempotent and non-destructive.

### Acceptance

Every target operation must have PostgreSQL query-budget proof, cold/warm cache proof, tenant isolation, concurrent stampede proof, and an authenticated HTTP/GraphQL smoke. k6 artifacts separate diagnostic smoke from 5,000-VU acceptance. Any operation over one second is named with its measured query/DB/cache cause; it is not hidden by averaging unrelated routes.

## Rollback

Read-model and cache cutovers are per-operation flags. Disabling L1/L2 returns reads to PostgreSQL. Aggregate migrations are additive. No native-specific schema exists. Payload-compatible old read paths remain available until shadow comparison and acceptance pass.

