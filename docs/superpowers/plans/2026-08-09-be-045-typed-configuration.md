# BE-045 Typed Configuration Plan

Date: 2026-08-09
Task: BE-045 — Chuẩn hóa typed configuration và loại feature flag vô hiệu
Owner: ERG-SPRING / Platform
Status: Activated

## Objective

Replace scattered raw configuration access with small validated
`@ConfigurationProperties` namespaces while preserving every existing key and
default. Remove dead `erg.modules.*.new-handler-enabled` flags unless a real
consumer/canary test proves the flag is still needed.

## Scope

- inventory `@Value`, environment lookups and module flags in production code;
- define focused typed properties for module events, bounded read execution,
  HTTP/CORS and any other existing namespace that has runtime behavior;
- keep names/defaults backward-compatible and validate bounds at startup;
- ensure native-image friendly immutable records/no reflective secret handling;
- remove dead feature flags from configuration/docs, or document a real
  consumer and canary test when retaining one;
- add `ApplicationContextRunner` validation tests for valid/default/invalid
  values plus JVM/native configuration smoke evidence;
- update configuration catalog/runbook without changing external contracts.

## Invariants

1. No raw `@Value` remains for a typed namespace.
2. Invalid pool, timeout, concurrency, CORS or event bounds fail startup.
3. Existing environment variable names and defaults remain compatible.
4. No monolithic `ErgProperties` or generic feature-flag framework is added.
5. Secrets are never logged or exposed through actuator/config responses.

## Acceptance evidence

- source inventory and ratchet tests cover all migrated namespaces;
- context-runner tests prove defaults, validation and unknown/dead flag policy;
- focused suite, contract gates and full `./mvnw.cmd -q -DcodexOwner=primary verify`
  are green;
- JVM/native configuration smoke remains deterministic and documented.
