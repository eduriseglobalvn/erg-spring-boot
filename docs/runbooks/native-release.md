# Native release

Build on Linux/CI with the production profile and external secrets. Verify
Flyway reaches the expected version, `/ready` is HTTP 200, PostgreSQL is
healthy, and the container has no test/Insight runtime artifact.

Native smoke is a release gate for startup/health/resource behavior. It is not
evidence that native has higher steady-state throughput; use the JVM/native
benchmark worksheet before selecting the production target.
