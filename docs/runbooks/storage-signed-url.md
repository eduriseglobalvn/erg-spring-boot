# Media signed-upload contract

Quiz media presign URLs are tenant-bound HMAC-SHA256 contracts. The signature covers the tenant,
content-addressed storage key, MIME type, byte size and expiry epoch. The local adapter accepts a URL
only when all fields match, the object key is under `quiz-media/<tenant>/`, and the expiry has not passed.

Configure `SECRET_MEDIA_URL_SIGNING_KEY` with a random value of at least 32 characters. It must be
different from the JWT secrets in production. The application falls back to the auth JWT secret only as
a compatibility bridge; production deployments should set the dedicated key explicitly and rotate it
with a dual-key rollout if existing URLs must remain valid.

The current local adapter protects URL issuance/verification but does not persist bytes. Before enabling
the production upload route, the R2/S3 adapter must call the same verification contract, enforce a HEAD
check for size and checksum during commit, and reject expired or cross-tenant PUTs at the storage edge.
