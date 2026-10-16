# BE-047: production capability and prototype ratchet

## Objective

Prevent generic process-local workflow/CMS/catalog/document helpers and published stub endpoints
from looking like real production capabilities. Prototype controllers/services are absent from the
production profile; published Calendar Excel import routes return a stable `NOT_IMPLEMENTED` code
until a persistent vertical slice exists.

## Constraints

- Do not silently delete legacy routes or invent storage for unrelated products.
- Do not change successful REST/GraphQL contracts for implemented calendar operations.
- Keep the existing prototype profile for local/demo fixtures; production graph must not construct it.
- Stable not-implemented responses must use the normal API/GraphQL exception envelope and a named
  capability code, never `UnsupportedOperationException` or a fake success payload.

## TDD slice

1. Audit prototype services/controllers and the published calendar Excel import path.
2. Replace the two Excel import throws with `NotImplementedException` and gate the unused parser
   service to the prototype profile; preserve authorization and metadata validation before the
   response.
3. Add source contract tests for prototype profile gating and the stable capability code, plus a
   policy document for future stubs.
4. Run focused calendar/architecture tests, compile, Modulith/public API checks and `mvnw verify`.

## Not in scope

No Excel parser, Apache POI dependency, new storage schema, CMS/catalog implementation, route
deletion, or product-specific vertical slice is introduced by this ratchet.
