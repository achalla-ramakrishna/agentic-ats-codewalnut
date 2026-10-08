#!/usr/bin/env bash
# Run on the Droplet from its checked-out deployment directory. No SSH credentials in CI.
set -euo pipefail
cd "$(dirname "$0")/../../deploy/digitalocean"
# Check resolved config without printing its secrets. Requires Docker Compose v2 and Python 3.
docker compose config --format json | python3 -c '
import json,re,sys
c=json.load(sys.stdin)
env=c["services"]["backend"]["environment"]
def enabled(key):
    value=str(env.get(key,"" )).lower()
    if value not in ("true","false"):
        raise SystemExit("Explicit true/false required for "+key)
    return value=="true"
rehearsal=enabled("ATS_REHEARSAL_ENABLED")
maintenance=enabled("ATS_MAINTENANCE_ENABLED")
background=enabled("ATS_BACKGROUND_WORK_ENABLED")
storage=enabled("ATS_DOCUMENT_STORAGE_ENABLED")
if not rehearsal and not maintenance and not (background and storage):
    raise SystemExit("Refusing open production HTTP without enabled background work and private document storage")
for name in ("backend","blob-bridge","mysql","caddy"):
    image=c["services"][name]["image"]
    if not re.fullmatch(r"[^\s]+@sha256:[0-9a-f]{64}",image):
        raise SystemExit("Require immutable reviewed image digest for "+name)
for key in ("ATS_ORIGIN_HOST","ATS_PUBLIC_HOST"):
    host=c["services"]["caddy"]["environment"][key]
    if not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)*\.[a-z]{2,}",host) or host.endswith((".invalid",".test")):
        raise SystemExit("Configure real DNS hostname for "+key)
'
docker compose pull backend blob-bridge caddy
# Does not touch MySQL: initialize it / upgrade it only at a separate runbook gate.
docker compose up -d --no-deps backend blob-bridge caddy
docker compose ps
