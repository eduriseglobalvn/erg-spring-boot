#!/usr/bin/env bash
# The only thing the CI deploy key may run. In ~erg/.ssh/authorized_keys:
#   command="bash /opt/erg/ops/docker/ci-entry.sh",restrict ssh-ed25519 AAAA... github-actions-deploy
# The key's holder (GitHub Actions) sends one of:
#   status backend|frontend              -> prints the deployed commit, or "none"
#   deploy backend|frontend <sha> [force]   (source tar of that commit on stdin)
# Anything else is refused, so a leaked key cannot open a shell or read .env.production.
set -Eeuo pipefail
read -r action target sha force _ <<<"${SSH_ORIGINAL_COMMAND:-}"
case "${target:-}" in
  backend) container=erg-backend-1 ;;
  frontend) container=erg-system-fe ;;
  *) echo "ci-entry: unknown target" >&2; exit 64 ;;
esac
case "$action" in
  status)
    rev=$(docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$container" 2>/dev/null) || rev=
    echo "${rev:-none}"
    ;;
  deploy)
    [[ ${sha:-} =~ ^[0-9a-f]{40}$ ]] || { echo "ci-entry: sha must be 40 hex characters" >&2; exit 64; }
    [ "${force:-}" = force ] && export FORCE=1
    # One deploy at a time on this machine; the second (backend and frontend merged together) waits.
    exec 9>/opt/erg/.deploy.lock
    flock -w 3600 9 || { echo "ci-entry: another deploy held the lock for an hour" >&2; exit 75; }
    if [ "$target" = backend ]; then
      bash /opt/erg/ops/docker/deploy.sh "$sha"
    else
      rm -rf /opt/erg/fe-src && mkdir -p /opt/erg/fe-src && tar xf - -C /opt/erg/fe-src
      bash /opt/erg/fe-src/deploy/docker/deploy.sh "$sha"
    fi
    ;;
  *) echo "ci-entry: unknown action" >&2; exit 64 ;;
esac
