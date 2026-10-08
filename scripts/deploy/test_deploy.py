#!/usr/bin/env python3
"""DEPLOY-06: verify cutover flag gates without contacting Docker or production."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="ats-deploy-gates-") as tmp:
    directory = Path(tmp)
    calls = directory / "calls"
    docker = directory / "docker"
    docker.write_text('''#!/usr/bin/env python3
import os,sys
with open(os.environ["TEST_DOCKER_CALLS"],"a") as log:
    log.write(" ".join(sys.argv[1:])+"\\n")
if sys.argv[1:] == ["compose","config","--format","json"]:
    print(os.environ["TEST_COMPOSE_CONFIG"])
''')
    docker.chmod(0o755)
    for rehearsal, maintenance, work, storage, accepted in [
        ("true", "true", "false", "false", True),
        ("false", "true", "false", "true", True),
        ("false", "false", "true", "true", True),
        ("false", "false", "false", "true", False),
        ("false", "false", "true", "false", False),
        ("typo", "true", "false", "false", False),
    ]:
        config = {"services": {name: {"image": "example/image@sha256:" + "a" * 64}
                               for name in ("backend", "blob-bridge", "mysql", "caddy")}}
        config["services"]["caddy"]["environment"] = {
            "ATS_ORIGIN_HOST": "origin.example.com", "ATS_PUBLIC_HOST": "ats.example.com"}
        config["services"]["backend"]["environment"] = {
            "ATS_REHEARSAL_ENABLED": rehearsal, "ATS_MAINTENANCE_ENABLED": maintenance,
            "ATS_BACKGROUND_WORK_ENABLED": work, "ATS_DOCUMENT_STORAGE_ENABLED": storage}
        calls.write_text("")
        result = subprocess.run(["bash", str(ROOT / "scripts/deploy/deploy.sh")],
                                env={**os.environ, "PATH": tmp + os.pathsep + os.environ["PATH"],
                                     "TEST_DOCKER_CALLS": str(calls), "TEST_COMPOSE_CONFIG": json.dumps(config)},
                                capture_output=True, text=True)
        assert (result.returncode == 0) == accepted, (config, result.stderr)
        assert ("compose pull" in calls.read_text()) == accepted, calls.read_text()
print("DEPLOY-06 passed: inconsistent cutover flags fail before pulling or replacing containers")
