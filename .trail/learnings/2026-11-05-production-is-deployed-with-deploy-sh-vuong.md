---
title: "Production is deployed with deploy.sh behind Cloudflare Tunnel, not by watchtower"
author: Vuong
date: 2026-11-05
branch: claude/fix-audit-findings
cost: { minutes: 1, tokens: 3947 }
id: l-5fe6e65f
scope: repo
status: active
src: agent
touches:
  - ops/docker/deploy.sh
  - ops/docker/nginx/native.conf
  - src/main/resources/db/migration/postgres/V110__rotate_super_admin_password.sql
---

## Decided
- Production is deployed with `ops/docker/deploy.sh <sha>` (usage in its header): it builds an immutable `erg-spring:<sha>-jvm` image on the server, dumps Postgres, rehearses migrations and startup on a restored copy (isolated network, workers off), then switches backend and nginx. Watchtower and the mutable `:latest` Docker Hub tag were dropped: nothing in CI built that tag.
- Production runs the JVM image, not native: native is only built nightly and never tested.
- V110 only rotates `usr_super_admin` while it still has the public V7 hash, because the owner had already rotated it in production.

## Watch out
- cloudflared is the only intended client, so nginx sees every request from the Docker gateway and takes `CF-Connecting-IP` as the client. That is only safe while the nginx and Grafana ports stay bound to 127.0.0.1.
- Prometheus, Grafana and Loki run as non-root users: their config files must stay world-readable. Removing other-read on ops/docker put Prometheus in a restart loop.
- Single-file bind mounts (nginx conf, prometheus.yml) keep the old inode after files are replaced, so those containers must be recreated.
- Keep server audit findings out of this repository: it is public.

In the code: ops/docker/deploy.sh, ops/docker/nginx/native.conf, src/main/resources/db/migration/postgres/V110__rotate_super_admin_password.sql
