# LMS state policy

`LmsStatePolicy` is the single module-owned vocabulary for learner assignment and
attempt state filters. It is deliberately small: persistence keeps the existing
strings for compatibility, while controllers and read use cases normalize and
validate input before it reaches a repository query.

## Assignment states

| State | Meaning | Visible to learner |
| --- | --- | --- |
| `draft` | Authoring has not been released | No |
| `assigned` | Recipients have been selected | Yes |
| `active` | The assignment can be started | Yes |
| `published` | Released/published assignment | Yes |
| `archived` | Retained for history, not active work | No |
| `late` / `overdue` | Derived UI filters for due-date reporting | No direct persisted transition |

## Attempt states

| State | Meaning | Learner can edit |
| --- | --- | --- |
| `in_progress` / `started` | Attempt is open | Yes |
| `submitted` | Submitted and awaiting/finalized grading | No |
| `needs_review` / `review_required` | Manual grading is required | No |
| `graded` / `completed` | Terminal result | No |

Unknown filter values fail closed with a `BadRequestException`; they must not be
silently treated as “all”. `completedAttemptStates()` is also used by attempt
limit enforcement so a new persisted terminal state cannot accidentally bypass
the limit without an explicit policy change and test.

The query ports (`LmsAssignmentQueryPort` and `LmsAttemptQueryPort`) expose
projection-only reads. JPA adapters remain internal compatibility implementations
until the remaining read paths migrate; command code may continue using aggregate
services and repositories while the public LMS boundary is stabilized.
