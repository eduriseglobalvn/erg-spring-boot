# API contract rules for erg-spring

## REST response

All production REST controllers should return:

```java
ApiResponse<T>
```

Where `T` is:

- typed record response.
- `PageResponse<ItemResponse>` for paginated lists.
- module-specific response record with `items` and `meta` if compatibility requires.

Do not return:

- JPA entities.
- raw `Map<String,Object>` for stable public contracts.
- unbounded lists for data that can grow.

`ApiResponse<T>` naming convention:

- Java component/accessor name is `requestId`.
- Wire field name remains `request_id`.
- Use `@JsonProperty("request_id")` on the Java component to keep both conventions explicit.
- New response DTO Java components stay camelCase; add `@JsonProperty` only when the public wire contract intentionally uses snake_case.

## DTO vs Projection

DTO:

- Public API/GraphQL/REST contract.
- Stable for frontend consumers.
- Lives in `common/response/<domain>` or GraphQL DTO package.
- Is still required even when repository projections are used.
- Owns public field names, compatibility, validation shape, and client-generation stability.

Projection:

- Internal DB/read-model selection contract.
- Used to select only needed columns/aggregates.
- Lives near repository/read-model code.
- Is mapped to DTO before returning.
- Is required for heavy LMS/LCMS/HocLieu screens, dashboards, workspaces, and reports.
- Owns query shape and read performance; it is not a public API type.

Rule:

- Small detail endpoint can use Entity -> DTO.
- Heavy list/workspace/report must use Projection -> DTO.
- GraphQL read-model should use Projection + batch query/DataLoader + DTO.
- Projection does not replace DTO; DTO does not replace Projection.
- Do not expose Projection records directly from REST controllers or GraphQL resolvers.
- Do not create one projection per tiny GraphQL field; create projections by screen/workspace/card/summary to avoid N+1.
- Use `@EntityGraph` for small detail reads with known relations, not for large workspaces.
- Hibernate batch fetch size is a safety net, not the primary N+1 fix.

Decision matrix:

| Case | Standard |
|---|---|
| Command/write/create/update/delete | Request DTO + Response DTO + service transaction |
| Small detail by id | Entity or explicit `@EntityGraph` -> DTO |
| Simple paginated list | Projection -> DTO |
| Dashboard/workspace/report | Page Projection + aggregate Projection + batch query -> Workspace DTO |
| Metric/count/summary | Aggregate Projection required |
| GraphQL nested read | Base Projection first, then DataLoader/batch `IN (...)` only for remaining nested data |

For 5,000 concurrency, a DTO-only implementation is not enough if it still loads full entities or lazy collections. Heavy screens must use projection queries that select only required columns, compute aggregates in the database, and keep query count bounded.

Projection checklist:

- Query includes tenant filter.
- Query includes scope/class/school filter in SQL/JPQL, not only Java filtering.
- Query is paged or hard-limited.
- Projection contains no password, token, raw credential, raw quiz answer, or private note unless a service permission check explicitly gates it.
- Aggregate/count/avg/max/min values are computed by DB query, not after broad entity loading.
- There is a matching composite index for the main `WHERE` and `ORDER BY`.
- Workspace reads have query-count tests.

## Request DTOs

Use Java records:

```java
public record CreateAssignmentRequest(
    @NotBlank String classId,
    @NotEmpty List<String> quizIds
) {}
```

Rules:

- Add validation annotations.
- Normalize page/size through `PageRequestDto` or equivalent.
- Validate dynamic payload depth/size if `Object` or `Map` is unavoidable.
- Separate command request DTOs from read filter DTOs.

## Error response

Use `GlobalExceptionHandler` and typed exceptions:

- `BadRequestException`
- `ForbiddenException`
- `UnauthorizedException`
- `ResourceNotFoundException`
- `ConflictException`

Validation should return 422 with field details.

## Pagination

Defaults:

- REST default size: 20.
- REST hard max: 100 unless a specific endpoint has a documented reason.
- GraphQL default size: 20-50.
- GraphQL hard max: 200.

Contract:

- Incoming REST `page` is zero-based.
- `PageRequestDto.normalizedPage()` clamps missing/negative page values to `0`.
- `PageRequestDto.normalizedSize()` defaults missing/non-positive size to `20` and clamps above max to `100`.
- Paginated REST responses should use `PageResponse<T>` with `items`, `page`, `size`, `totalItems`, `totalPages`, `hasNext`, and `hasPrevious`.
- Legacy/module-specific pagination shapes can remain only as documented compatibility adapters; new public production endpoints should not introduce another pagination envelope.

Any list endpoint must either:

- be static/small by design, or
- have pagination/hard max.

## Naming

- Keep Java field names camelCase.
- If wire contract needs snake_case, use `@JsonProperty`.
- Do not mix multiple names for same concept unless backward compatibility requires an alias.

## Compatibility

When replacing mock/placeholder endpoints:

- Keep old endpoint only as deprecated adapter if FE still depends on it.
- Document replacement endpoint.
- Add tests for both adapter and new contract until legacy is removed.
