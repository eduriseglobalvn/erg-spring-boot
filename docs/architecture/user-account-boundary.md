# User/account boundary

`users` owns account identity and persistence. Other modules must consume the
small public ports under `com.erg.ergspringboot.users` instead of importing
`model.users.User` or `repository.auth.user.UserRepository`.

The first migrated callers are Calendar teacher discovery and authorization
read queries. Calendar consumes `UserDirectoryPort` and
`UserAccountProjection`; authorization consumes `UserAuthorizationPort` and
`UserAuthorizationProjection`. These records carry only tenant, identity,
display, role and permission values. Credential material, access scopes and
JPA associations are intentionally absent from the public projections.

`users.internal.directory.LegacyUserDirectoryAdapter` and
`users.internal.authorization.LegacyUserAuthorizationAdapter` are the explicit
compatibility seams over the current repositories. The directory adapter
applies the request tenant to every projection and uses tenant-scoped ID
lookup for assignment-only teachers. The authorization adapter requires a
tenant-scoped account before reading role/override rows. These rules prevent a
caller from reintroducing an unscoped `findAllById` query or a cross-tenant
permission snapshot while the persistence model is being migrated.

The legacy Calendar reference-map path still carries JPA users for old service
and test adapters. That path is deprecated and remains isolated from the new
catalog path; it is the next removal target after the legacy Calendar read
adapter is retired.

## Rules

1. Add a narrow query port before a new module consumes account data.
2. Return immutable records; never return entities, repositories or credential
   fields from the public users package.
3. Keep repository knowledge inside a named adapter and add a boundary test for
   the adapter.
4. Require tenant-scoped lookups for IDs supplied by another module.
