# Event policy

1. Use a synchronous `CalendarOperations` facade when the caller needs the
   invariant/strong-consistency result in the same request.
2. Publish an immutable in-process event only for a business consequence that
   can be processed after commit. `CalendarBatchPublished` is the first example.
3. `@ApplicationModuleListener` is the only approved listener annotation for
   durable module events. Spring Modulith JDBC Event Publication Registry stores
   the publication in the same PostgreSQL transaction as the command.
4. Delivery is at-least-once. Listener side effects use a stable business
   idempotency key (`listenerName + eventId` or an equivalent business key).
5. Event payloads contain identifiers, versions, tenant/actor and correlation
   metadata; they do not contain JPA entities, large collections or API response
   graphs. Consumers query their own projection when they need more data.
6. External providers are never called from the command transaction. The
   listener/outbox performs that work after commit and can be retried/recovered.
7. The legacy transactional outbox remains only for legacy email delivery during
   the cutover. New module event types must not be inserted into both systems.
