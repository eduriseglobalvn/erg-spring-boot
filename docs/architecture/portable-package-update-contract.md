# Portable exam package and update-feed contract

This contract is the backend boundary used by the Teacher/Student Tauri
surfaces. The browser and desktop clients share the same quiz package model;
the desktop runtime only trusts a package after it verifies the digest and
Ed25519 signature locally.

## Endpoints

| Endpoint | Purpose | Cache/authorization |
| --- | --- | --- |
| `GET /api/v1/lcms/quizzes/{quizId}/bundle` | Export the newest usable package, including runtime payload for a controlled export | Authenticated LCMS read; tenant comes from security context; private ETag cache |
| `GET /api/v1/lcms/quiz-versions/{quizVersionId}/bundle` | Export one immutable published version | Same policy as above |
| `GET /api/v1/lcms/quiz-packages/{packageId}/manifest` | Metadata-only manifest before download; never includes package JSON, credentials or answer keys | Authenticated LCMS read; private ETag cache |
| `GET /api/v1/lcms/quiz-packages/{packageId}/download` | Validates the manifest and redirects to an HTTPS content-addressed object | Authenticated LCMS read; no URL token is accepted from the client |
| `GET /api/v1/lcms/quiz-packages/updates?quizId=...` | Bounded update feed filtered by channel, current version and runtime version | Revoked, expired, unsigned and incompatible packages are omitted |
| `POST /api/v1/lcms/quiz-packages/{packageId}/revoke` | Idempotently revoke a package and invalidate its update eligibility | Publish permission; tenant is server-derived |

The OpenAPI snapshot is the source consumed by the FE contract sync. ETags are
stable for the package/feed inputs and clients may send `If-None-Match` to
receive `304 Not Modified`.

## Manifest invariants

- `packageId`, `contentHash`, `publicKeyId`, signature algorithm, minimum
  runtime, release channel, expiry, size and HTTPS download URL are explicit.
- `portableReady=true` is fail-closed: the package must have a valid SHA-256
  digest, Ed25519 signature/key id and HTTPS artifact URL, and must not be
  expired or revoked.
- Server-authoritative packages are recursively stripped of `answerKey` and
  sensitive credential-like keys before export. The package aggregate remains
  tenant-scoped and published-lineage checked.
- Asset manifests are bounded to 10,000 nodes and 1 GiB. Invalid or oversized
  packages are not exported or advertised.
- The backend never stores or returns the Ed25519 private key. Configure it
  using `SECRET_QUIZ_PACKAGE_ED25519_PRIVATE_KEY` in the release environment;
  use `QUIZ_PACKAGE_PUBLIC_KEY_ID` for key rotation metadata.

Desktop binary releases (`.exe`/`.dmg`) use the same signed-manifest and
ETag rules. The release artifact URL is intentionally external/content-
addressed; signing and publication are performed by the release pipeline, not
by an HTTP request that accepts arbitrary URLs.
