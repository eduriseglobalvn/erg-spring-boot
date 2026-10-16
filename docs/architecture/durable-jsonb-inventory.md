# Durable JSONB inventory

`JsonbDurabilityCatalog` is the executable inventory for every `jsonb` field on
the current entity model. It is intentionally metadata only: it does not create
a generic serializer, repository or schema registry. Each owner chooses its own
tolerant reader and forward-only migration chain.

## Classification

- `DURABLE_VERSIONED`: immutable or authoring documents with an explicit
  `schemaVersion` contract. Quiz draft/version/package documents are the first
  migrated owner and keep old versions readable.
- `DURABLE_OWNER_READER`: durable IDs, settings, metadata and operational
  snapshots whose owner must add a versioned reader before changing shape.
  Existing data is read as-is until that owner publishes a migration step.
- `TRANSIENT_EVENT`: append-only event, outbox or delivery payload. The event
  envelope/version and replay contract own compatibility; it is not treated as
  a domain document.
- `EXEMPT_AUDIT_OR_PII`: audit metadata or PII-owned payloads with retention and
  masking rules; the owning module must not expose them as arbitrary public JSON.

## Owner inventory

| Owner | Fields | Current evolution rule |
| --- | --- | --- |
| `quiz` | `QuizDraft.settings`, `QuizDraft.playerTemplate`, `QuizDraft.tree`; `QuizPackage.assetsManifest`, `QuizPackage.packageJson`; `QuizVersion.authoringSnapshot`, `runtimeQuiz`, `answerKey`; `QuizVersionQuestion.body`, `answerKey` | Scalar `schema_version` plus payload `schemaVersion` where applicable; use `JsonbSchemaMigrator.readSafely` for reads and keep immutable historical versions. |
| `lms` | Assignment recipients; attempt answers/events/client; question scope/tags/choices/answer/content/settings/metadata; quiz scope/topicIds/questionIds/slides/settings/result/theme/contentScope; quiz-question settings/metadata; student metrics; subject scope | Owner readers preserve unknown fields and must add an explicit version before changing shape. Attempt events/client remain transient. |
| `learningcontent` | ContentAsset.metadata, ContentItemAsset.metadata | Metadata merge is owner-internal; additive changes only until a content migration chain is published. |
| `crm` | Handover checklist; deal revenue/cost lines; school-profile risks | Operational snapshots; new versions require a CRM reader/migrator, never a global base entity. |
| `notifications` | Message/outbox payloads and quiet hours | Delivery payloads follow notification/provider contract; quiet-hours changes are additive. |
| `platform.events` | OutboxEvent.payload | Event envelope and replay version own compatibility; no domain document assumptions. |
| `reporting` | ReportJob.requestJson/resultJson | Job worker owns tolerant reads and stable terminal states. |
| `users` | EmployeeProfile.emergencyContact, UserActivity.metadata | PII/audit retention, masking and owner-only access; no public raw-map contract. |

## Read and rollout policy

`JsonbSchemaMigrator.readSafely` returns a copied current payload for legacy,
migrated and current versions. A future or malformed version is not downgraded
to the latest shape: it returns an unavailable result with a bounded metric label
(`erg_jsonb_<owner>_<status>`). The caller must surface an unavailable/capability
state or retry after deployment. Reads never write back to JSONB; backfills, when
needed, are separate forward-only migrations with an observe/rollback-compatible
reader.

The inventory test is a ratchet: new JSONB fields must be classified and given an
owner before they can be added to the model. A common migration utility may be
extracted only after at least two owners demonstrate identical mechanics.
