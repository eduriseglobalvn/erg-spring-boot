# CI gates

| Gate | Trigger | Command/evidence | Failure policy |
|---|---|---|---|
| Compile + unit | every PR | `./mvnw -B -ntp test` | block |
| Architecture + Modulith | every PR | included in Surefire | block |
| Integration | PR and release | `./mvnw -B -ntp verify` with PostgreSQL/Testcontainers | block |
| Security/dependencies | PR and release | dependency audit plus secret scan | block on high/critical |
| API inventory | every PR | route/typed-boundary contract tests plus OpenAPI snapshot/checksum and pinned oasdiff | block |
| GraphQL compatibility | every PR | schema checksum, coverage catalog and `GraphQlSchemaCompatibilityTest` against PR base | block |
| Native | nightly/release | `./mvnw -Pnative -DskipTests native:compile` and smoke | block release, advisory PR |
| k6 | benchmark environment only | script-specific thresholds | block performance release |

The k6 gate must receive a seeded dataset and an authenticated test token from
the environment. It must never use production credentials or run a 5,000-VU
scenario on a developer laptop as a release claim.
