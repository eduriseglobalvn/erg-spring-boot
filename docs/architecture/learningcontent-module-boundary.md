# Learning Content module boundary

`learningcontent` owns content items, content assets and topic placements. Its
public boundary is `LearningContentOperations` plus the typed request/response
records under `learningcontent.api`.

The web controller reads tenant and actor identity from the verified
`UserAccessContext`, calls only the public facade and returns module-owned DTOs.
It never accepts a tenant override from a request body/header and never exposes
JPA entities, repositories or legacy service DTOs.

`LegacyLearningContentAdapter` is the single reviewed strangler seam to the
existing transactional persistence service. It converts module requests into
legacy requests and maps entities back into immutable DTOs. The legacy service
is deprecated and must not gain new callers. A future slice can replace the
adapter with internal command/query persistence implementations without changing
the REST/OpenAPI payloads.

The canonical `/api/v1/content/**` routes and existing `/api/content/**`
aliases remain behaviorally identical. The aliases are retained only for
deprecation compatibility and are not a second implementation. Cache eviction
continues to invalidate `learningResourceLibrary` after mutations; all reads
and writes retain tenant-scoped repository predicates and PostgreSQL transaction
boundaries.
