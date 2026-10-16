# ELEARNING module boundary

`elearning` owns the persistent student dashboard, discussion, reaction and
announcement-read flows. `ElearningOperations` is the only public application
facade used by the GraphQL controllers; its command/query records live under
`elearning.api` and carry no persistence type.

`StudentDashboardReadService` and `StudentEngagementService` are deprecated
compatibility adapters around the existing tenant-scoped repositories/JDBC
projection. They retain self-scope and permission checks, idempotency-key
replay, class membership checks and transactional writes. A future slice may
move their internals under `elearning.internal` without changing GraphQL
operation names or response records.

The legacy `CatalogService`, `ElearningController`, `CourseController` and
`EngagementController` remain `prototype` profile surfaces backed by
process-local state. Production cannot construct them; they are not used by
the persistent ELEARNING facade and must not be treated as real catalog or
engagement data.
