# Enterprise connectors boundary (BE-060)

`enterpriseconnectors` owns the registration and bounded run lifecycle for
SIS, HRIS and SCIM providers. Callers use `EnterpriseConnectorOperations` and
never import a provider SDK, JPA entity, repository, credential or upstream
payload.

The application service stores only tenant-scoped metadata, a server-side
`secret://` reference, mapping version/hash and bounded run counters. Every
mutation requires an idempotency key, an optimistic version and a typed
permission. PostgreSQL V97 enables and forces RLS on connector, run and receipt
tables.

Provider adapters implement `EnterpriseConnectorProviderPort` and are selected
by `EnterpriseConnectorProviderRegistry`. The checked-in `REFERENCE` adapter is
deterministic and network-free, so local development and conformance tests do
not depend on a vendor or a secret. A future SIS/HRIS/SCIM adapter only adds a
provider implementation and capability tests; it does not change the facade,
tables or controller contract.

Dry-run returns a bounded diff/sample, sync and retry return scalar counts, and
deprovision requires an explicit bounded ID list. Events contain IDs, versions,
mode and counts only; no secret reference or upstream payload is published.
