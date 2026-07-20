# Test strategy

The build has two feedback loops and never disables integration tests to make a
build green.

## Surefire (unit and architecture)

`mvn test` runs fast unit, contract, architecture, security and Modulith tests.
Classes ending in `IT`, `ITCase` or `IntegrationTest` are excluded so the local feedback loop does
not accidentally start containers.

## Failsafe (integration)

Tests that require PostgreSQL/Testcontainers, a full GraphQL context, or an
external adapter use the `IT`, `ITCase` or `IntegrationTest` suffix. They run in Maven's
`integration-test` and `verify` phases. A skipped container is a visible test
result and must be justified; it is not a replacement for the integration gate.

## Module tests

Use `@ApplicationModuleTest` for a single module whenever the behavior does not
need the complete application. Use `Scenario` for transactional/asynchronous
event flows and assert outcomes with a timeout instead of fixed sleeps.

## Release evidence

Every release records the exact command, profile, commit SHA, database image,
test count, skipped count and any external environment required. k6 and native
benchmarks are separate gates and are not silently folded into `mvn verify`.
