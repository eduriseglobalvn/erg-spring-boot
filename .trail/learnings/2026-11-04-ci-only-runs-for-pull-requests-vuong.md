---
title: "CI only runs for pull requests and main or codex branches, and the Maven wrapper must stay executable"
author: Vuong
date: 2026-11-04
branch: claude/fix-audit-findings
cost: { minutes: 14, tokens: 85780 }
id: l-74304ca5
scope: repo
status: active
src: agent
touches:
  - mvnw
  - ".github/workflows/ci.yml"
---

## Decided
- Pushing to a `claude/*` branch never starts CI: the workflow triggers on pull requests, pushes to `main` / `codex/**`, tags, the nightly schedule and manual dispatch. To get CI without opening a PR, push the same commit to `codex/<name>`.
- Every Maven job failed with exit code 126 because the wrapper was stored in git as a plain file (mode 100644). Commits made on Windows do not keep the executable bit, so it has to be set with git's chmod.

## Watch out
- Without `gh` login you can still read run status, job steps and failure annotations through the public GitHub API, but job logs and test-report artifacts return 403. To see which test failed, re-run the same matrix filter locally.

In the code:
- `.github/workflows/ci.yml` `on:` block; portal matrix `tests` / `itTests` filters.
- `git update-index --chmod=+x mvnw` (commit 5612a09); check with `git ls-files -s mvnw` → 100755.
- `curl https://api.github.com/repos/eduriseglobalvn/erg-spring-boot/actions/runs/<id>/jobs` and `/check-runs/<job id>/annotations`.
