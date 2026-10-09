#!/usr/bin/env bash
# Run on the Droplet from its checked-out deployment directory. No SSH credentials in CI.
set -euo pipefail
cd "$(dirname "$0")/../../deploy/digitalocean"
../../scripts/deploy/preflight.sh "$@"
docker compose pull backend blob-bridge caddy
# Does not touch MySQL: initialize it / upgrade it only at a separate runbook gate.
docker compose up -d --no-deps backend blob-bridge caddy
docker compose ps
