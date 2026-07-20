# Spring Modulith modernization baseline

Captured on 2026-07-20 (Asia/Saigon) before behavior or package changes.

## Repository checkpoint

- Accepted source commit: `67ac1791178bfc076b7c52b7d8e52bd6cb614d93` (`docs: document backend API contract workflow`).
- Implementation branch: `codex/spring-modulith-modernization`.
- Worktree: `D:\ERG\erg-spring\.worktrees\spring-modulith-modernization`.
- Baseline HEAD: `0fe43c242a5938d84f609f7547c62381e545bf25` (`chore: isolate modulith modernization worktree`). This commit only ignores `.worktrees/`; it does not change application behavior.
- `git status --short`: clean.
- `git diff --stat`: empty.
- Pre-existing modified/untracked files: none. No user file was staged or absorbed into this branch.

## Toolchain

~~~text
Apache Maven 3.9.15
Java 25.0.3, Oracle GraalVM 25.0.3+9.1
OS: Windows 11 10.0 amd64
Default locale: en_US
Platform encoding: UTF-8
~~~

Commands used:

~~~powershell
.\mvnw.cmd -version
java -version
~~~

## Runtime and test configuration

### Profiles

- Common configuration currently defaults `SPRING_PROFILES_ACTIVE` to `dev`.
- Full-context tests explicitly activate `test` where required.
- The `test` profile disables Spring scheduling, the transactional-outbox worker and the reporting worker.
- A global test context customizer uses PostgreSQL 17 via Testcontainers, or `ERG_TEST_POSTGRES_URL` when supplied. If neither external PostgreSQL nor Docker is available, it deliberately falls back to in-memory H2 with schema creation enabled.

### Datasource (credentials redacted)

- Production/common URL: `${SPRING_DATASOURCE_URL}` (redacted; no value recorded).
- Production/common username/password: environment supplied (redacted; no value recorded).
- Hikari pool: maximum 8, minimum idle 1, connection timeout 1000 ms, validation timeout 5000 ms, idle timeout 300000 ms, max lifetime 1800000 ms, auto-commit disabled.
- PostgreSQL integration-test pool: maximum 4.
- H2 fallback URL: `jdbc:h2:mem:erg_test;MODE=PostgreSQL;...`; no external credential.
- Flyway is disabled by the common default and enabled by the PostgreSQL test customizer before Hibernate validation.

### Bounded read executor

- Core threads: 4.
- Maximum threads: 4.
- Queue capacity: 32.
- Shutdown timeout: 5 seconds.
- Rejection policy: `AbortPolicy`; the implementation does not use the common ForkJoin pool.

### Existing outbox workers

- Transactional outbox: enabled by default outside tests, fixed delay 5000 ms, batch size 50, lease 60 seconds and maximum 8 attempts.
- Notification outbox: enabled by default outside tests, fixed delay 10000 ms, poll size 200 (bounded to 1..500 by code), lease timeout 60 seconds and maximum 8 attempts.
- Both workers are disabled under the test profile. These mechanisms remain legacy owners during the documented cutover; no new Modulith event may be delivered through both registries.

## Baseline cautions

- The default `dev` profile and default-disabled Flyway are production-safety findings reserved for Phase 4.3; they are recorded here and intentionally not changed during baseline capture.
- The test datasource can differ by machine (PostgreSQL when Docker/external DB exists, H2 otherwise). Build evidence must state which path ran.
- Test results and elapsed times are recorded separately in `build-baseline.md` after Task 0.2.
