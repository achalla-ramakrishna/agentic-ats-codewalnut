#!/usr/bin/env bash
# Read-only checks; use this before first MySQL boot as well as application deployment.
set -euo pipefail
cd "$(dirname "$0")/../../deploy/digitalocean"
# Resolve .env, exported variables and overrides exactly as the subsequent Compose invocation.
# Secrets stay in the pipe; Python never prints the resolved configuration or inspect output.
docker compose config --format json | python3 ../../scripts/deploy/preflight.py "$@"
