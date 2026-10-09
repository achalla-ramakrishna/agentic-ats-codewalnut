#!/usr/bin/env python3
"""Validate resolved Compose settings without printing secrets; DEPLOY-06–08."""
import argparse
import json
import os
import stat
from pathlib import Path
import re
import subprocess
import sys

DIRECTORY = Path(__file__).resolve().parents[2] / "deploy/digitalocean"
PRIVATE_HOST = re.compile(r"[a-z0-9]+\.private\.blob\.vercel-storage\.com")


class PreflightError(ValueError):
    pass


def fail(message):
    raise PreflightError(message)


def inspect_running(service):
    ids = subprocess.check_output(["docker", "compose", "ps", "-q", service], text=True).split()
    if len(ids) != 1:
        fail("Require exactly one running Compose container for " + service)
    result = subprocess.check_output(["docker", "inspect", ids[0]], text=True)
    containers = json.loads(result)
    if len(containers) != 1:
        fail("Unexpected container inspection result")
    container = containers[0]
    labels = container.get("Config", {}).get("Labels", {})
    if (labels.get("com.docker.compose.project") != "ats"
            or labels.get("com.docker.compose.service") != service
            or not container.get("State", {}).get("Running")):
        fail("Unexpected running Compose identity for " + service)
    return container


def container_environment(container):
    env = {}
    for entry in container.get("Config", {}).get("Env", []):
        key, separator, value = entry.partition("=")
        if separator:
            if key in env:
                fail("Ambiguous running container environment")
            env[key] = value
    return env


def read_identity(path, owner):
    try:
        # Validate and read the same inode; no lstat/read race or symlink traversal.
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(descriptor, "r") as pinned:
            metadata = os.fstat(pinned.fileno())
            if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != owner
                    or stat.S_IMODE(metadata.st_mode) != 0o600):
                fail("Identity pin must be a root-owned regular file with mode 0600")
            return pinned.read().strip()
    except OSError:
        fail("Missing or unreadable independently recorded identity pin for the selected target")


def validate(config, directory=DIRECTORY, require_identity=False, check_running=False, identity_owner=0,
             target="production"):
    if target not in ("production", "functional-rehearsal"):
        fail("Unknown deployment target")
    if require_identity and target != "production":
        fail("Production identity/cleanup checks cannot select functional rehearsal")
    if target == "functional-rehearsal" and os.path.lexists(directory / ".production-blob-host"):
        fail("Functional rehearsal is forbidden in a checkout with a production identity pin")
    if config.get("name") != "ats":
        fail("Compose project must be ats; do not override COMPOSE_PROJECT_NAME")
    services = config["services"]
    env = services["backend"]["environment"]

    def enabled(key):
        value = str(env.get(key, "")).lower()
        if value not in ("true", "false"):
            fail("Explicit true/false required for " + key)
        return value == "true"

    rehearsal = enabled("ATS_REHEARSAL_ENABLED")
    maintenance = enabled("ATS_MAINTENANCE_ENABLED")
    background = enabled("ATS_BACKGROUND_WORK_ENABLED")
    storage = enabled("ATS_DOCUMENT_STORAGE_ENABLED")
    opening = not rehearsal and not maintenance
    if opening and not (background and storage):
        fail("Refusing open production HTTP without enabled background work and private document storage")
    for name in ("backend", "blob-bridge", "mysql", "caddy"):
        if not re.fullmatch(r"[^\s]+@sha256:[0-9a-f]{64}", services[name]["image"]):
            fail("Require immutable reviewed image digest for " + name)
    for key in ("ATS_ORIGIN_HOST", "ATS_PUBLIC_HOST"):
        host = services["caddy"]["environment"][key]
        if (not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)*\.[a-z]{2,}", host)
                or host.endswith((".invalid", ".test"))):
            fail("Configure real DNS hostname for " + key)

    mysql = services["mysql"]
    policy = mysql.get("labels", {}).get("com.codewalnut.ats.recovery-policy")
    if policy not in ("dump-only", "pitr"):
        fail("Select and approve MySQL recovery policy: dump-only or pitr")
    command = mysql.get("command", [])
    if not isinstance(command, list):
        fail("MySQL command must use the reviewed argument list")
    # Reject conflicting aliases/overrides instead of trusting a label or .env declaration.
    controls = [arg for arg in command if any(word in arg.replace("_", "-").lower()
                for word in ("log-bin", "binlog", "expire-logs", "defaults-file", "defaults-extra"))]
    expected_option = "--skip-log-bin" if policy == "dump-only" else "--log-bin=mysql-bin"
    retention = [arg for arg in controls if arg.startswith("--binlog-expire-logs-seconds=")]
    if (len(retention) != 1 or len(controls) != 2 or expected_option not in controls
            or not re.fullmatch(r"--binlog-expire-logs-seconds=[0-9]+", retention[0])):
        fail("MySQL binary-log flags must exactly match the approved recovery policy")
    seconds = int(retention[0].split("=", 1)[1])
    if (policy == "dump-only" and seconds != 0) or (policy == "pitr" and not 1 <= seconds <= 4294967295):
        fail("dump-only requires retention 0; pitr requires explicit positive retention seconds")

    if opening:
        actual_mysql = inspect_running("mysql")
        actual_command = actual_mysql.get("Config", {}).get("Cmd", [])
        actual_controls = [arg for arg in actual_command if any(word in arg.replace("_", "-").lower()
                           for word in ("log-bin", "binlog", "expire-logs", "defaults-file", "defaults-extra"))]
        actual_policy = actual_mysql.get("Config", {}).get("Labels", {}).get("com.codewalnut.ats.recovery-policy")
        if actual_controls != controls or actual_policy != policy:
            fail("Running MySQL recovery policy differs; recreate MySQL in a planned maintenance window first")

    if opening or require_identity or check_running or target == "functional-rehearsal":
        expected_hosts = None
        if target == "production":
            expected = read_identity(directory / ".production-blob-host", identity_owner)
        else:
            identity = json.loads(read_identity(directory / ".rehearsal-identity.json", identity_owner))
            if not isinstance(identity, dict) or set(identity) != {"blob_host", "public_host", "origin_host"}:
                fail("Rehearsal identity must contain exactly blob_host, public_host and origin_host")
            expected = identity["blob_host"]
            expected_hosts = {"ATS_PUBLIC_HOST": identity["public_host"], "ATS_ORIGIN_HOST": identity["origin_host"]}
            for key, host in expected_hosts.items():
                if not isinstance(host, str) or services["caddy"]["environment"].get(key) != host:
                    fail("Effective Compose hostname differs from the independently approved rehearsal target")
        if not isinstance(expected, str) or not PRIVATE_HOST.fullmatch(expected):
            fail("Identity pin must contain a valid private store hostname")
        configured = services["blob-bridge"]["environment"].get("ATS_BLOB_STORE_HOST")
        if configured != expected:
            fail("Effective Compose Blob host differs from the independently recorded target store")
        if env.get("ATS_BLOB_BRIDGE_URL") != "http://blob-bridge:3001":
            fail("Backend must use the reviewed internal Blob bridge endpoint")
        if check_running:
            if expected_hosts:
                caddy = container_environment(inspect_running("caddy"))
                if any(caddy.get(key) != host for key, host in expected_hosts.items()):
                    fail("Running proxy hostnames differ from the independently approved rehearsal target")
            bridge = container_environment(inspect_running("blob-bridge"))
            backend = container_environment(inspect_running("backend"))
            if bridge.get("ATS_BLOB_STORE_HOST") != expected:
                fail("Running Blob bridge store differs from the independently recorded target store")
            if backend.get("ATS_BLOB_BRIDGE_URL") != "http://blob-bridge:3001":
                fail("Running backend uses an unexpected Blob bridge endpoint")
            secret = bridge.get("ATS_BLOB_BRIDGE_SECRET", "")
            if not secret or backend.get("ATS_BLOB_BRIDGE_SECRET") != secret:
                fail("Running backend and Blob bridge authentication configuration differ")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("production", "functional-rehearsal"), default="production")
    parser.add_argument("--require-production-identity", action="store_true")
    parser.add_argument("--check-running-identity", action="store_true")
    args = parser.parse_args()
    try:
        validate(json.load(sys.stdin), require_identity=args.require_production_identity,
                 check_running=args.check_running_identity, target=args.target)
    except PreflightError as error:
        print(str(error), file=sys.stderr)
        return 1
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError):
        # Exception details can include provider/container environment output. Never dump them.
        print("Deployment preflight failed. Check recovery flags, image pins, operation flags and production store binding.", file=sys.stderr)
        return 1
    print("Deployment preflight passed for " + args.target
          + (" (running store binding checked)" if args.check_running_identity else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
