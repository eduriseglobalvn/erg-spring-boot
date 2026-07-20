# External event boundary decision

The current deployment has no consumer outside this process. Therefore business
events stay in Spring Modulith's JDBC Event Publication Registry and are not
sent to Kafka/AMQP merely because a broker is available. This avoids a second
outbox/dispatcher, extra connection pools and an additional ordering/failure
contract.

Re-open this decision only when an external consumer is approved. That change
must choose one broker and Spring Modulith externalization mode, version the
schema/routing key, define tenant/PII classification, retry/DLQ and consumer
idempotency, and add unavailable-broker plus old/new schema contract tests.
