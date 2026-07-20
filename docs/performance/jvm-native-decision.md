# JVM versus Native decision record

No production choice is made from startup time alone. The decision remains
**JVM by default** until the same seeded workload is run against both artifacts
and records cold start, RSS, steady-state RPS, p95/p99, CPU, heap/GC, database
pool wait and error rate. Native is preferred for scale-to-zero or memory-bound
workers only when those measurements meet the workload gates; JVM remains the
default for long-running CPU-heavy traffic when JIT throughput is better.

The native smoke already proves packaging, Flyway and readiness. It does not
close this performance decision. Benchmark results must be attached to this
record with commit SHA, image digest and workload manifest.
