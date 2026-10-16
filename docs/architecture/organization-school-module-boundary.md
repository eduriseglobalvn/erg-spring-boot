# Organization and School module boundary

BE-048 establishes explicit `organization` and `school` Modulith modules. Controllers call public
operations and receive DTO/projection responses; they do not construct repositories or return JPA
entities. `OrganizationEducationUnitQuery` is the first organization-owned internal use case and
keeps the tenant/scope-aware repository read behind `OrganizationOperations`.

School already has the matching public command/query facades for school lifecycle, academic years,
grades and operational projections. Its internal services and repositories remain compatibility
adapters during the incremental migration; new callers must use `SchoolOperations` or
`SchoolQueryOperations`. The source ratchet prevents public boundary classes from importing
horizontal persistence packages. Later slices can move one aggregate/repository family at a time
without changing REST/GraphQL payloads, RLS, cache names or tenant semantics.
