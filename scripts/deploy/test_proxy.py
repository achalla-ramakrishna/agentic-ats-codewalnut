#!/usr/bin/env python3
"""DEPLOY-05: exercise actual Caddy forwarding and error privacy with fake data only.

Requires Docker; uses isolated containers/network and a random localhost port.
Optional CADDY_TEST_IMAGE / PYTHON_TEST_IMAGE allow reviewed immutable image pins.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
name = "ats-proxy-test-" + uuid.uuid4().hex[:12]
network, proxy, backend = name, name + "-proxy", name + "-backend"


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, stderr=subprocess.STDOUT).strip()


def request(base, path, headers=None):
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + path, headers=headers or {}), timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, dict(response.headers), response.read().decode()


try:
    docker("network", "create", network)
    with tempfile.TemporaryDirectory(prefix="ats-proxy-test-") as temp:
        echo = Path(temp) / "echo.py"
        echo.write_text('''from http.server import BaseHTTPRequestHandler, HTTPServer
import json
class Echo(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(dict(self.headers)).encode())
    def log_message(self, *args): pass
HTTPServer(("0.0.0.0", 8080), Echo).serve_forever()
''')
        docker("run", "-d", "--name", backend, "--network", network, "--network-alias", "backend",
               "-v", str(echo) + ":/echo.py:ro", os.environ.get("PYTHON_TEST_IMAGE", "python:3.12-alpine"),
               "python", "/echo.py")
        docker("run", "-d", "--name", proxy, "--network", network,
               "-p", "127.0.0.1::8080", "-e", "ATS_ORIGIN_HOST=http://:8080",
               "-e", "ATS_PUBLIC_HOST=ats.example.test",
               "-v", str(ROOT / "deploy/digitalocean/Caddyfile") + ":/etc/caddy/Caddyfile:ro",
               os.environ.get("CADDY_TEST_IMAGE", "caddy:2"))
        port = docker("port", proxy, "8080/tcp").rsplit(":", 1)[1]
        base = "http://127.0.0.1:" + port
        for attempt in range(40):
            try:
                if request(base, "/api/echo")[0] == 200:
                    break
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(0.25)
        else:
            raise AssertionError("Proxy did not become ready")
        status, headers, body = request(base, "/api/echo", {
            "Forwarded": 'for=attacker;host=evil.example;proto=http',
            "X-Forwarded-Host": "evil.example",
            "X-Forwarded-Proto": "http",
            "X-Forwarded-Port": "81",
            "X-Forwarded-For": "203.0.113.254",
            "X-Forwarded-Prefix": "/evil",
            "X-Real-IP": "203.0.113.253",
        })
        echoed = {key.lower(): value for key, value in json.loads(body).items()}
        assert status == 200, (status, body)
        assert echoed["host"] == "ats.example.test", echoed
        assert echoed["x-forwarded-host"] == "ats.example.test", echoed
        assert echoed["x-forwarded-proto"] == "https", echoed
        assert echoed["x-forwarded-port"] == "443", echoed
        assert "203.0.113.254" not in echoed.get("x-forwarded-for", ""), echoed
        assert not {"forwarded", "x-forwarded-prefix", "x-real-ip"} & echoed.keys(), echoed
        assert "no-store" in headers.get("Cache-Control", ""), headers
        assert request(base, "/private-bridge-path")[0] == 404

        docker("stop", backend)
        secrets = ["FAKE_OAUTH_CODE_761", "FAKE_OAUTH_STATE_762", "FAKE_CODING_TOKEN_763",
                   "FAKE_REFERER_TOKEN_764", "FAKE_CUSTOM_HEADER_TOKEN_765"]
        paths = ["/login/oauth2/code/google?code=" + secrets[0] + "&state=" + secrets[1],
                 "/api/v1/coding/" + secrets[2]]
        for path in paths:
            status, headers, body = request(base, path, {
                "Referer": "https://ats.example.test/?token=" + secrets[3],
                "X-Candidate-Token": secrets[4],
            })
            assert status in (502, 503), (status, body)
            assert body == "Service temporarily unavailable", body
            assert "no-store" in headers.get("Cache-Control", ""), headers
        logs = docker("logs", proxy)
        assert all(secret not in logs for secret in secrets), "Request token leaked into Caddy runtime logs"
        print("DEPLOY-05 passed: canonical forwarded headers, no-store, route isolation, private proxy errors")
finally:
    for container in (proxy, backend):
        subprocess.run(["docker", "rm", "-f", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["docker", "network", "rm", network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
