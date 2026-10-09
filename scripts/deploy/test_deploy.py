#!/usr/bin/env python3
"""DEPLOY-06–08: verify policy/store gates without contacting Docker or production."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("preflight", ROOT / "scripts/deploy/preflight.py")
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)
HOST = "productionfixture.private.blob.vercel-storage.com"


def configuration(opening=False, policy="dump-only", seconds=0):
    config = {"name": "ats", "services": {name: {"image": "example/image@sha256:" + "a" * 64}
              for name in ("backend", "blob-bridge", "mysql", "caddy")}}
    config["services"]["caddy"]["environment"] = {
        "ATS_ORIGIN_HOST": "origin.example.com", "ATS_PUBLIC_HOST": "ats.example.com"}
    config["services"]["backend"]["environment"] = {
        "ATS_REHEARSAL_ENABLED": str(not opening).lower(), "ATS_MAINTENANCE_ENABLED": str(not opening).lower(),
        "ATS_BACKGROUND_WORK_ENABLED": str(opening).lower(), "ATS_DOCUMENT_STORAGE_ENABLED": str(opening).lower(),
        "ATS_BLOB_BRIDGE_URL": "http://blob-bridge:3001"}
    config["services"]["blob-bridge"]["environment"] = {"ATS_BLOB_STORE_HOST": HOST}
    config["services"]["mysql"]["labels"] = {"com.codewalnut.ats.recovery-policy": policy}
    config["services"]["mysql"]["command"] = ["--max-allowed-packet=128M",
        "--skip-log-bin" if policy == "dump-only" else "--log-bin=mysql-bin",
        "--binlog-expire-logs-seconds=" + str(seconds)]
    return config


def container(service, config):
    env = dict(config["services"][service].get("environment", {}))
    env["ATS_BLOB_BRIDGE_SECRET"] = "fake-shared-secret"
    labels = {"com.docker.compose.project": "ats", "com.docker.compose.service": service,
              **config["services"][service].get("labels", {})}
    return {"State": {"Running": True}, "Config": {"Labels": labels,
            "Cmd": config["services"][service].get("command", []),
            "Env": [key + "=" + value for key, value in env.items()]}}


class DeploymentPreflightTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ats-deploy-gates-")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.pin = self.directory / ".production-blob-host"
        self.pin.write_text(HOST + "\n")
        self.pin.chmod(0o600)

    def validate(self, config, **kwargs):
        preflight.validate(config, self.directory, identity_owner=os.getuid(), **kwargs)

    def test_DEPLOY_07_explicit_policy_and_effective_flags(self):
        self.validate(configuration())
        self.validate(configuration(policy="pitr", seconds=3600))
        cases = [configuration(policy="CHOOSE"), configuration(policy="pitr", seconds=0),
                 configuration(policy="pitr", seconds=-1), configuration(seconds=3600)]
        conflicting = configuration()
        conflicting["services"]["mysql"]["command"].append("--log-bin=override")
        cases.append(conflicting)
        overridden = configuration(policy="pitr", seconds=3600)
        overridden["services"]["mysql"]["command"][1] = "--skip-log-bin"
        cases.append(overridden)
        for config in cases:
            with self.subTest(config=config), self.assertRaises(preflight.PreflightError):
                self.validate(config)

    def test_DEPLOY_07_DEPLOY_08_opening_requires_independent_identity_and_matching_running_mysql(self):
        config = configuration(opening=True)
        with patch.object(preflight, "inspect_running", return_value=container("mysql", config)):
            self.validate(config)
            self.pin.unlink()
            with self.assertRaises(preflight.PreflightError):
                self.validate(config)
        stale = configuration(policy="pitr", seconds=3600)
        with patch.object(preflight, "inspect_running", return_value=container("mysql", stale)):
            with self.assertRaises(preflight.PreflightError):
                self.validate(config)

    def test_DEPLOY_08_pin_permissions_and_symlinks_fail_closed(self):
        config = configuration()
        self.pin.chmod(0o644)
        with self.assertRaises(preflight.PreflightError):
            self.validate(config, require_identity=True)
        self.pin.unlink()
        target = self.directory / "target"
        target.write_text(HOST)
        target.chmod(0o600)
        self.pin.symlink_to(target)
        with self.assertRaises(preflight.PreflightError):
            self.validate(config, require_identity=True)

    def test_DEPLOY_08_pin_owner_is_checked_independently_of_file_contents(self):
        with self.assertRaises(preflight.PreflightError):
            preflight.validate(configuration(), self.directory, require_identity=True,
                               identity_owner=os.getuid() + 1)

    def test_DEPLOY_08_effective_compose_store_wins_over_exported_identity_lookalikes(self):
        config = configuration()
        config["services"]["blob-bridge"]["environment"]["ATS_BLOB_STORE_HOST"] = "rehearsal.private.blob.vercel-storage.com"
        with patch.dict(os.environ, {"ATS_BLOB_STORE_HOST": HOST, "ATS_EXPECTED_PRODUCTION_BLOB_HOST": HOST}):
            with self.assertRaises(preflight.PreflightError):
                self.validate(config, require_identity=True)

    def test_DEPLOY_08_running_identity_detects_stale_host_wrong_endpoint_and_mismatched_secrets(self):
        config = configuration()
        running = {name: container(name, config) for name in ("backend", "blob-bridge")}
        with patch.object(preflight, "inspect_running", side_effect=lambda name: running[name]):
            self.validate(config, check_running=True)
            for service, variable, bad in [
                    ("blob-bridge", "ATS_BLOB_STORE_HOST", "rehearsal.private.blob.vercel-storage.com"),
                    ("backend", "ATS_BLOB_BRIDGE_URL", "http://another-bridge:3001"),
                    ("backend", "ATS_BLOB_BRIDGE_SECRET", "fake-other-secret")]:
                original = copy.deepcopy(running[service])
                env = running[service]["Config"]["Env"]
                env[:] = [entry for entry in env if not entry.startswith(variable + "=")] + [variable + "=" + bad]
                with self.subTest(variable=variable), self.assertRaises(preflight.PreflightError):
                    self.validate(config, check_running=True)
                running[service] = original

    def rehearsal_identity(self, config):
        identity = {"blob_host": config["services"]["blob-bridge"]["environment"]["ATS_BLOB_STORE_HOST"],
                    "public_host": config["services"]["caddy"]["environment"]["ATS_PUBLIC_HOST"],
                    "origin_host": config["services"]["caddy"]["environment"]["ATS_ORIGIN_HOST"]}
        pin = self.directory / ".rehearsal-identity.json"
        pin.write_text(json.dumps(identity))
        pin.chmod(0o600)
        return pin

    def test_DEPLOY_08_explicit_functional_rehearsal_uses_separate_pin_without_weakening_default(self):
        config = configuration(opening=True)
        config["services"]["blob-bridge"]["environment"]["ATS_BLOB_STORE_HOST"] = "rehearsal.private.blob.vercel-storage.com"
        self.rehearsal_identity(config)
        with patch.object(preflight, "inspect_running", return_value=container("mysql", config)):
            with self.assertRaises(preflight.PreflightError):
                self.validate(config)  # Production default still checks production pin.
            with self.assertRaises(preflight.PreflightError):
                self.validate(config, target="functional-rehearsal")  # Cannot repurpose production checkout.
            self.assertEqual(self.pin.read_text().strip(), HOST)
            self.pin.unlink()
            self.validate(config, target="functional-rehearsal")
        self.assertFalse(self.pin.exists())
        with self.assertRaises(preflight.PreflightError):
            self.validate(config, target="functional-rehearsal", require_identity=True)

    def test_DEPLOY_08_rehearsal_requires_exact_store_and_both_approved_hostnames(self):
        self.pin.unlink()
        config = configuration()
        with self.assertRaises(preflight.PreflightError):
            self.validate(config, target="functional-rehearsal")
        self.rehearsal_identity(config)
        self.validate(config, target="functional-rehearsal")
        for service, variable in [("blob-bridge", "ATS_BLOB_STORE_HOST"),
                                  ("caddy", "ATS_PUBLIC_HOST"), ("caddy", "ATS_ORIGIN_HOST")]:
            wrong = copy.deepcopy(config)
            wrong["services"][service]["environment"][variable] = "unexpected.example.com"
            with self.subTest(variable=variable), self.assertRaises(preflight.PreflightError):
                self.validate(wrong, target="functional-rehearsal")

    def test_DEPLOY_08_rehearsal_running_check_also_rejects_stale_proxy_hostname(self):
        self.pin.unlink()
        config = configuration()
        self.rehearsal_identity(config)
        running = {name: container(name, config) for name in ("backend", "blob-bridge", "caddy")}
        with patch.object(preflight, "inspect_running", side_effect=lambda name: running[name]):
            self.validate(config, target="functional-rehearsal", check_running=True)
            running["caddy"]["Config"]["Env"] = [
                "ATS_PUBLIC_HOST=unexpected.example.com" if value.startswith("ATS_PUBLIC_HOST=") else value
                for value in running["caddy"]["Config"]["Env"]]
            with self.assertRaisesRegex(preflight.PreflightError, "Running proxy hostnames differ"):
                self.validate(config, target="functional-rehearsal", check_running=True)

    def test_DEPLOY_08_inspection_rejects_wrong_compose_project_service_and_stopped_container(self):
        config = configuration()
        for key, value in [("com.docker.compose.project", "other"), ("com.docker.compose.service", "other")]:
            instance = container("blob-bridge", config)
            instance["Config"]["Labels"][key] = value
            with patch.object(preflight.subprocess, "check_output", side_effect=["fake-id", json.dumps([instance])]):
                with self.assertRaises(preflight.PreflightError):
                    preflight.inspect_running("blob-bridge")
        instance = container("blob-bridge", config)
        instance["State"]["Running"] = False
        with patch.object(preflight.subprocess, "check_output", side_effect=["fake-id", json.dumps([instance])]):
            with self.assertRaises(preflight.PreflightError):
                preflight.inspect_running("blob-bridge")

    def test_DEPLOY_06_deploy_rejects_bad_flags_before_pulling_containers(self):
        calls = self.directory / "calls"
        docker = self.directory / "docker"
        docker.write_text('''#!/usr/bin/env python3
import os,sys
with open(os.environ["TEST_DOCKER_CALLS"],"a") as log: log.write(" ".join(sys.argv[1:])+"\\n")
if sys.argv[1:] == ["compose","config","--format","json"]: print(os.environ["TEST_COMPOSE_CONFIG"])
''')
        docker.chmod(0o755)
        for opening, arguments, accepted in [
                (False, [], True), (True, [], False),
                (False, ["--target", "functional-rehearsal", "--require-production-identity"], False),
                (False, ["--target", "unknown"], False)]:
            config = configuration(opening=opening)
            config["services"]["backend"]["environment"]["ATS_BACKGROUND_WORK_ENABLED"] = "false"
            calls.write_text("")
            result = subprocess.run(["bash", str(ROOT / "scripts/deploy/deploy.sh"), *arguments],
                env={**os.environ, "PATH": str(self.directory) + os.pathsep + os.environ["PATH"],
                     "TEST_DOCKER_CALLS": str(calls), "TEST_COMPOSE_CONFIG": json.dumps(config)},
                capture_output=True, text=True)
            self.assertEqual(result.returncode == 0, accepted, result.stderr)
            self.assertEqual("compose pull" in calls.read_text(), accepted)


if __name__ == "__main__":
    unittest.main()
