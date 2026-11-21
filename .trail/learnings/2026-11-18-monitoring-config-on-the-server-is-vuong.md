---
title: "Monitoring config on the server is overwritten by every backend deploy, so ops changes must be committed first"
author: Vuong
date: 2026-11-18
branch: feat/role-consolidation
id: l-ca7fb950
scope: repo
status: active
src: agent
touches:
  - ops/docker/compose.monitor.yml
  - ops/docker/nginx/native.conf
  - ops/docker/deploy.sh
---

## Decided
- Monitoring changes are applied to the production server by copying files in place and are only durable once committed: each backend deploy replaces the server's ops folder with the commit's copy.
- Grafana datasources are deleted and re-created at every start, because Grafana refuses to change the uid of an already provisioned datasource and then fails to start provisioning ("data source not found").
- Dashboards are plain JSON generated once by a throwaway script; the JSON files are the source of truth.

## Tried and ruled out
- cAdvisor v0.52.1: on Docker 29 with the containerd image store it logs "failed to identify the read-write layer ID" for every container and exports no container names. v0.55.1 works.
- Promtail json stage + regex on `source: log`: with docker_sd the line is already the raw log line, so nothing matched and no `level` label ever existed.
- An access_log directive at http level in the nginx conf.d file: it adds a second log next to the image's default one; it must sit inside the server block.

## Watch out
- Config files are single-file bind mounts: overwrite them with `cp` (same inode), not `mv`/`tar` over the path, or the container keeps seeing the old file.
- The deploy rehearsal used to leave an anonymous Postgres volume behind on every run (fixed with `docker rm -f -v`).
- Queries were validated against the Prometheus/Loki APIs only; nobody has looked at the new dashboards in the Grafana UI yet.

In the code:
- ops/docker/compose.monitor.yml, ops/docker/monitor/**, ops/docker/nginx/native.conf, ops/docker/deploy.sh
- Check before applying: `promtool check config`, `promtail -check-syntax`, `amtool check-config`, `nginx -t`
