# FE guide: curriculum subject-level-topic tree

This is an additive API. No existing payload or GraphQL schema changed.

## Request

`GET /api/curriculum/tree?status=active`

The optional `status` accepts `active`, `draft`, or `archived`; omitted means `active`. Authentication and `X-Tenant-ID` follow the existing API convention.

## Response

The standard `ApiResponse.data` is the existing `CurriculumTreeResponse`: a list of subjects, each containing ordered levels and each level containing ordered topics. The server caps each node type at 500 and returns a bounded-request error instead of materializing an unbounded tenant tree.

## Cache behavior

Reads are tenant-scoped and cached for 15 minutes. Creating, updating, archiving, or deleting a subject, level, or topic invalidates that tenant's tree after the transaction commits.

