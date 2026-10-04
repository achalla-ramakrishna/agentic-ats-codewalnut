# Set up the code runner (Judge0) for coding tests

Coding questions (ADR-0016) run candidate code in a self-hosted
[Judge0 CE](https://github.com/judge0/judge0) sandbox on its own VM. Railway
can't host it (Judge0 needs privileged containers), so use any small Linux VM
(GCP, AWS, Azure or DigitalOcean): **2 vCPU, 4 GB RAM, Ubuntu 22.04**.

1. **Turn on cgroup v1** (Judge0 1.13 needs it). In `/etc/default/grub` add
   `systemd.unified_cgroup_hierarchy=0` to `GRUB_CMDLINE_LINUX`, then
   `sudo update-grub && sudo reboot`.
2. **Install Docker** and the Compose plugin.
3. **Download Judge0 CE 1.13.1** from its GitHub releases page and unzip it.
4. In `judge0.conf` set strong random values for `REDIS_PASSWORD`,
   `POSTGRES_PASSWORD` and **`AUTHN_TOKEN`** (the app sends it as
   `X-Auth-Token`). Keep `ENABLE_NETWORK` off (the default), so submitted code
   has no network.
5. Start it: `docker compose up -d db redis`, wait 10 seconds, then
   `docker compose up -d`. Check with `curl -H "X-Auth-Token: <token>" http://localhost:2358/languages`.
6. **Lock it down**: put it behind HTTPS (e.g. Caddy or Nginx) and allow
   inbound traffic only from the app (firewall rule), or keep it on a private
   network with the app.
7. **Railway variables** (app service), then Deploy:
   - `ATS_CODE_RUNNER_URL=https://judge0.your-domain.example`
   - `ATS_CODE_RUNNER_TOKEN=<the AUTHN_TOKEN>`
8. Check: Tests → open a test with a coding question → **Try a solution**. The
   red "code runner isn't connected" notice disappears once it works.

Language ids default to Judge0 CE 1.13's Java 62, Python 71, JavaScript 63 and
C++ 54. With a different Judge0 image, override them with
`ATS_CODING_LANGUAGE_IDS_JAVA` (and `_PYTHON`, `_JAVASCRIPT`, `_CPP`).

If the runner goes down, submitted tests wait in "grading the code" and are
graded automatically when it's back (retried every minute, for about 30
minutes); after that, staff click **Grade again** on the result.
