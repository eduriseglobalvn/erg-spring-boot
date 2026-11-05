---
title: Deploy archives from Windows must turn off autocrlf
author: Vuong
date: 2026-11-05
branch: claude/ops-hardening
id: l-1bc6b12e
scope: repo
status: active
src: agent
touches:
  - ops/docker/deploy.sh
  - ops/backup/backup.sh
---

## Decided
- Deploy with `git -c core.autocrlf=false archive <sha> | ssh ... deploy.sh <sha>`, and `ops/**` plus `*.sh` are `eol=lf` in .gitattributes, because otherwise the shell scripts reach the server with CRLF and fail.

## Watch out
- `git archive` applies the working-tree line-ending conversion. On this Windows checkout (core.autocrlf=true) every deployed file arrived with CRLF; nginx, YAML and Java did not care, but the nightly backup.sh failed with `set: pipefail: invalid option name`, so backups would have silently stopped.
- Comparing server and repo files with `tr -d '\r'` or `--strip-trailing-cr` hides this, because it strips the very difference. Check with `grep -rlI $'\r'` on the server after a deploy.

In the code: .gitattributes, ops/docker/deploy.sh (usage header), ops/backup/backup.sh
