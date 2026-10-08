# Vercel + DigitalOcean: production migration runbook

This is a planned maintenance migration of a **live** Railway installation. Do not
run its cutover/cleanup commands against production merely to test this document.
Requirements: DEPLOY-01–06; decision: ADR-0027. First merge and deploy the private
storage PR on Railway. Complete and verify its document migration before hosting
cutover; retain original database bytes and a Blob-compatible rollback release.

## Inventory and go/no-go record

Record these privately with the operator, not in Git:

- Railway deployed commit/image and release configuration; database MySQL version,
  character set/collation, table engines, data/index size, largest BLOB, disk use,
  Flyway history and pending AI/intake/grading/storage tasks.
- Public hostname, all Railway-generated links already sent, OAuth callbacks,
  webhook endpoints, mail/calendar connections, provider retry windows, current
  timed assessments/coding rooms/interviews and expected activity.
- Record counts by table, document IDs/kinds/size/checksums, storage migration
  status and retained-byte counts. Counts alone do not prove content integrity.
- Authorized operator, maintenance window, measured restore duration, acceptable
  recovery point/time, success checks, rollback owner and observation period.
- Retention and restore arrangements for **both** database and private objects.
  A database backup containing Blob references cannot restore deleted objects.

Do not assume Railway data is fake. Never upload exports, logs containing tokens,
or candidate documents to Git/PRs. Restrict and later destroy rehearsal copies.

## Prepare infrastructure (no production routing change)

1. Provision an x86 Droplet with Docker Engine/Compose v2 and Python 3. Install OS
   updates, restrict SSH to administrators, allow inbound 80/443, leave 3306/8080/
   3001 closed. Docker can bypass UFW; inspect actual listening ports and use the
   DigitalOcean cloud firewall too. Keep Judge0 on a separate host.
2. Checkout the reviewed commit at `/opt/ats`. Copy
   `deploy/digitalocean/.env.example` to `.env` in that directory and
   `backend.env.example` to `.env.backend`, and `mysql-backup.cnf.example` to
   `.env.mysql-backup`; make the backup client file root-owned and chmod all three
   600. The backup mount is required at first MySQL boot; provision its read-only
   dump user later as described below. No dev/demo profile.
   The checked-in defaults keep rehearsal and maintenance on, workers off.
3. Select reviewed MySQL/Caddy image **digests**. Match Railway's actual MySQL
   release first. Do not combine an untested 8.0→8.4 upgrade with the hosting move.
   Run `scripts/deploy/test_proxy.py` with `CADDY_TEST_IMAGE` set to the exact chosen
   Caddy digest; the default `caddy:2` CI test does not certify a different pin.
   Pin these manually; container image replacement must never delete volumes.
4. Before publishing, a GitHub administrator must create the `production-images`
   environment with **selected deployment branches: `main` only**, required
   reviewers and self-review disabled. Ensure these server-side protections are
   available/enforced on the repository plan; do not publish without them. Protect
   `main` and changes to deployment workflows with company review requirements.
   An environment name in YAML alone does not install any of those protections.
   After merging the stack, dispatch `Build deployment images` using **workflow
   branch `main`**, with an exact reviewed main SHA as the `revision` input.
   Verify the run's branch/ref is main and its environment approval record before
   trusting the recorded digests. The workflow's ref/ancestry checks are defense in
   depth; writable branch YAML is not a server-side permission boundary.
   It runs backend tests on MySQL, frontend checks and bridge tests before publishing
   images. Record both image digests from its summary in `.env`. GHCR packages must
   be private; authenticate the Droplet with a read-only package credential.
5. Configure real origin/public hostnames; point only the **origin** DNS to the
   Droplet. Caddy obtains TLS. It overwrites Host and all forwarded identity with
   the configured public HTTPS host; never expose the backend port around Caddy.
6. Configure the private Blob bridge per the storage PR. Token stays only in the
   bridge environment; Java receives an internal URL and shared secret. The bridge
   has no host port and no public proxy route. Rehearsal should not possess a
   production write token: use a separate empty rehearsal store/token initially,
   and a separate copied store only for controlled document checks. Do not
   rotate/delete live objects.
7. From `deploy/digitalocean`, start only `docker compose up -d mysql`. Never start
   the app before restoring the copied database: Flyway needs its actual history.
   Then run `scripts/deploy/deploy.sh` from the repo after configuring all digests.
   This replaces only app/bridge/proxy containers; it does not upgrade MySQL.

The example caps containers at 1728 MiB total, leaving only ~320 MiB for host
services. Java has a 384 MiB heap and one AI/coding worker; MySQL has a 192 MiB
buffer pool. This is intentionally a **capacity trial**, not a production promise.
Monitor OOM restarts, RSS, swap, disk/temp usage, latency, DB connections and load
under concurrent 10 MB uploads, bulk 60 MB requests and PDF rendering. Upgrade to
4 GB if reserve/latency is inadequate. Build images on CI, not the Droplet. Configure
alerts and rotating logs; never enable request/body logging for candidate data.
Caddy handles proxy failures with a generic noncacheable response and removes
request objects/response headers from its default runtime logs. Keep debug and
access logging off. Run `python3 scripts/deploy/test_proxy.py` to verify spoofed
forwarding headers and token-free backend-failure logs in isolated Docker containers.

## Vercel configuration and release

Use a **CLI-only project with no connected Git repository**, owned by the company's
Vercel **Pro team**, not an engineer's personal account. The company's Pro team
should own the production private Blob store and a separate backup store as well.
Use role-based team access and company-managed credentials/billing. Keep the
repository under company control; a GitHub organization is recommended for durable
ownership and review controls, but no repository/account transfer is performed by
this change. A GitHub organization is not required for this CLI-only deployment.

For this procedure, `frontend/` is the uploaded project root and the Vercel project's
**Root Directory setting must be blank (repository root), not `frontend`**. From a
clean reviewed checkout, generate the routing configuration, then link/create the
project through CLI (not Import Git Repository) under the verified company team:

```sh
node scripts/deploy/configure-vercel.mjs https://YOUR-REAL-ORIGIN-HOST production
vercel link --cwd frontend --scope YOUR-COMPANY-TEAM
```

Check `frontend/.vercel/project.json` for the intended project/team and check the
Vercel dashboard: **Settings → Git must show no connected repository**; Root
Directory must be blank. Do not use `vercel link --repo` or import/connect this
repository to the production project. Vercel CLI project linking is local metadata,
not permission to connect the Git integration. Before every release verify team,
project, no Git connection and generated `frontend/vercel.json`, then:

```sh
vercel deploy --cwd frontend --scope YOUR-COMPANY-TEAM --prod
```

The generated configuration is included in this explicit CLI upload. It is ignored
by Git and cannot disable or configure a Git-triggered build. A Git connection would
allow a deployment without the required rewrites and is unsupported by this workflow.
Do not generate routing from the build command: Vercel reads it before the build.
Do not upload the repository root with this setup, or configure another `frontend`
Root Directory inside the already uploaded frontend directory.

For a preview, use a separate CLI-only project with its own database, Blob store,
OAuth client and origin; generate with the `preview` argument and deploy without
`--prod`. That argument is an operator label, not automatic isolation. Never point
an arbitrary Vercel preview at the production backend. Review the team's platform
request-log retention/access settings for OAuth codes and token-bearing URLs;
Caddy's log redaction does not configure Vercel logs.

After promotion, `GET https://PUBLIC-HOST/api/v1/health` **through Vercel** must return
200 with JSON `{"status":"ok"}` and private/no-store headers, not the SPA/404. Check
an ordinary SPA deep link and authenticated requests too; keep maintenance enabled
until this routing gate passes.

Proxy paths: `/api/*`, `/oauth2/*`, `/login/oauth2/*`, `/webhooks/*`. Other application
routes use the SPA; assets retain ordinary static caching. Proxy responses are
private/no-store at Vercel and Caddy. Check actual `Age`, cache status and response
headers using two distinct user sessions: authenticated data must not be shared.
Do not set backend/Blob/database secrets as frontend `VITE_*` variables.

Keep existing production public hostname if owned. Configure Google callbacks:
`https://PUBLIC-HOST/login/oauth2/code/google` and
`https://PUBLIC-HOST/oauth2/callback/google-calendar`. Keep old callbacks during
rollback window. Check Set-Cookie Secure/HttpOnly/SameSite, XSRF cookie/header,
login/logout, redirect host, generated links and uploads through the full chain.
Vercel external proxy has platform timeout/payload constraints: measure long AI
requests and large uploads against the account's actual current limits before go-live.

References: [Vercel rewrites](https://vercel.com/docs/routing/rewrites),
[Vercel configuration](https://vercel.com/docs/project-configuration/vercel-json),
[Vercel CLI deployment](https://vercel.com/docs/cli/deploy),
[GitHub environment protections](https://docs.github.com/en/actions/concepts/workflows-and-actions/deployment-environments),
[Caddy reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy).

## Rehearsal on an isolated database copy

1. Take an authorized encrypted consistent backup. Use the MySQL client matching
   the source version, `--single-transaction --quick --hex-blob --routines
   --triggers --events --no-tablespaces --set-gtid-purged=OFF`. Ensure every relevant
   table is InnoDB; no schema changes during dump. Other table engines need a
   reviewed lock/snapshot approach. Verify Railway export permissions. Flyway's
   schema-history table and all binary data must be included.
2. Restore to a **new disposable target** database. For the provided Compose,
   `MYSQL_DATABASE=ats` creates the target and app user; apply dump to `ats` via
   `docker compose exec -T mysql`, using the MySQL client with
   **`mysql --max-allowed-packet=128M -u root ats`** and `MYSQL_PWD` set inside the
   container from its root environment. The server's matching 128M setting alone
   does not raise the client's packet limit. A 10 MB BLOB is about 20 MB in a hex
   INSERT and exceeds the client's default. Never use `--force` to skip failed
   statements; abort and reconcile every table count and document checksum.
   Never put a password in command arguments/history.
   Decrypt/pipe directly; do not leave plaintext dumps on disks or terminals.
3. Set `ATS_REHEARSAL_ENABLED=true` before the first app start. It overrides other
   flags, blocks **all** HTTP except GET health and stops startup AI/code grading,
   built-in bank writes and storage work. Remove outbound integration credentials;
   restrict egress as defense in depth. Flyway may still migrate the copied schema.
   `ATS_BACKGROUND_WORK_ENABLED=false` alone does not block interactive actions.
   `async=false` executes inline and must never be used as a pause switch.
4. Verify record counts, schema history, document manifests and checksums using
   read-only SQL/tools while the app remains frozen. Read copied objects through
   the controlled migration verification tools, without modifying originals.
5. To exercise login/API flows on copied data, use a tightly restricted test host,
   controlled staff accounts and a **sanitized** database copy (replace candidate
   contact details, webhook targets, pending work and tokens). Use separate test
   provider accounts/credentials; no dev/demo with real data. Only then disable
   rehearsal/maintenance and enable selected workers. A functional test mutates
   its copy, so never reuse that database as the final target.
6. Run the storage permission tests (allow/deny, cross-client, government IDs,
   revoked shares), auth/CSRF, upload/download, AI and coding flows. Verify no
   provider call was made from the frozen startup. Measure export/transfer/restore,
   warmup and verification duration plus headroom; record a realistic outage window.
7. Test restoration of the encrypted backup and matching object set. Destroy the
   rehearsal copy after signoff; reinitialize a clean final target before cutover.

Health is process liveness, not readiness/data validity. Rehearsal flags are read
only on startup. They cannot drain previously admitted work or undo a provider call.

## Final maintenance cutover

1. Announce the measured window and choose a time with no active timed tests or
   coding interviews. Prevent new sessions/assessment starts in advance by normal
   scheduling; application timers do not pause merely because hosting is paused.
   Record how interrupted candidates will be rescheduled.
2. Pause automatic Railway/Vercel deployments. At the **old ingress**, serve
   maintenance before restarting anything. Stop accepting requests (GETs can
   expire/score assessments and record views). Drain in-flight HTTP/background
   work with a bounded wait, then stop the old application and verify no replica
   or job is running. If restarting in maintenance, set maintenance true and
   background false before restart; confirm all paths except health return 503.
3. Webhooks receive retryable 503 during freeze; never acknowledge and discard
   events. Confirm each active provider's retry semantics/window beforehand, or
   route into a tested durable queue. Record unresolved deliveries and reconcile
   IDs after restart to prevent omissions/duplicate effects. Do not depend on a
   browser redirect for webhook POSTs.
4. With every writer stopped (including SQL tools and migration workers), verify
   storage migration has no unresolved/error rows. Take the final binary-safe dump
   plus immutable object manifest, checksum/encrypt it, and copy off-server. Record
   exact cutover time and final counts. Keep old database frozen for rollback.
5. Restore into the clean target, verify table counts/Flyway history/manifests and
   representative private downloads/checksums. Keep target rehearsal/maintenance
   on and workers off during validation. No simultaneous writer is permitted.
6. Configure the verified provider credentials, new webhook origin and public
   host. Prepare/promote the matching Vercel deployment and switch public routing.
   Keep old ingress frozen while DNS caches expire. Never let stale DNS hit an old
   writable app. Use a temporary redirect/proxy on old **owned** hosts as needed.
7. Final go/no-go: acceptable memory/headroom, data reconciled, backups verified,
   Google callbacks correct, exact compatible image versions, no old workers.
   Set all four flags together in `.env`: `ATS_REHEARSAL_ENABLED=false`,
   `ATS_MAINTENANCE_ENABLED=false`, `ATS_BACKGROUND_WORK_ENABLED=true`, and
   `ATS_DOCUMENT_STORAGE_ENABLED=true`, then restart using `scripts/deploy/deploy.sh`.
   That script refuses open HTTP with paused workers or disabled private storage;
   the application also warns at startup if HTTP is open while work is paused.
   Opening HTTP and starting workers is the write boundary: record it. Confirm only the target executes work. New sessions must log in;
   Calendar/Gmail connections are session-held and need reconnection.
8. Smoke-test production using fake records/accounts: login/logout/CSRF, staff
   scope, candidate/client access, document permissions, uploads, integrations and
   pending task retry. Remove only the explicit fake records under normal policy.
   Monitor errors/latency/DB/disk/worker backlog/provider delivery for the agreed
   observation period. Keep old data, compatible image and encrypted backup intact.

## Existing links and rollback

If emails use an owned domain, keep that domain. If they use a Railway-generated
hostname, leave a small redirect service at that exact hostname for browser links,
preserving paths/query strings. Verify job, assessment, reset/share and portal URLs.
Do not log their tokens. Provider callbacks/webhooks require an explicit handoff.
A redirect retention period must cover outstanding links; do not delete Railway
until this inventory is resolved. Do not leave the old ATS running just for redirects.

Before target writes: revert routing to the compatible, frozen Railway release and
reopen it after verifying its retained DB/Blob references. After target writes:
freeze the target, preserve its current database and object manifest, then either
fix forward or restore **that latest state** back onto a compatible Railway release.
Account for new documents, tasks and provider deliveries, then reconcile before
reopening. DNS-only rollback after new writes loses visible changes and is forbidden.
Keep the same compatible schema/app pairing; Flyway does not reverse migrations.

## Backup, retention and later cleanup

`scripts/deploy/backup.sh` runs on a separate trusted backup host with `age`, gzip
and a restricted SSH key. It streams a consistent dump over SSH, encrypts without
plaintext disk files, and creates the final file only after every stage succeeds.
Run it on a schedule appropriate to the agreed recovery point, alert on failures,
and test decryption + restoration. Use age recipients whose private keys are
held outside the Droplet and tested by an authorized recovery operator.

Provision a dedicated local MySQL `ats_backup` user with a unique password matching
`.env.mysql-backup`. Grant only `SELECT, SHOW VIEW, TRIGGER, EVENT ON ats.*`, plus
`SHOW_ROUTINE ON *.*` if required by the source MySQL 8 version for the script's
`--routines` export. Verify grants and an actual dump/restore with the installed
version; do not grant writes, `ALL`, `SUPER` or reuse app/root credentials. If the
inventory confirms no routines, a reviewed variant may omit `--routines` instead
of granting global routine visibility. The file is mounted read-only and root-only.

Install `scripts/deploy/backup-source.sh` as root-owned, non-writable-by-backup-user
`/usr/local/libexec/ats-backup-source` (0755). Its fixed command uses `/usr/bin/docker`
and Compose's `ats-mysql-1` name; verify those on the host. Give a dedicated
`atsbackup` OS account **no Docker-group membership** and no general sudo access.
Allow only the exact argument-free wrapper via a validated sudoers rule:

```text
atsbackup ALL=(root) NOPASSWD: /usr/local/libexec/ats-backup-source ""
```

Restrict its `authorized_keys` entry to the backup host IP and a forced command:

```text
from="BACKUP_HOST_IP",restrict,command="sudo -n /usr/local/libexec/ats-backup-source" ssh-ed25519 REPLACE_WITH_BACKUP_PUBLIC_KEY
```

`restrict` disables forwarding, PTY and user rc; the forced command ignores the SSH
request and runs only the fixed dump. Keep this key file and wrapper protected from
the backup account. Test that arbitrary requested commands still produce only a
backup and cannot run shell/Docker commands. The remote key can read all candidate
data through the dump, so restrict access and rotate it accordingly. The backup
script requests `ats-database-backup`; it no longer invokes arbitrary remote shell
commands or MySQL root. Do not describe an ordinary unrestricted Docker-capable SSH
account as a restricted backup key.

Keep backups outside the Droplet (a separate private Blob backup store is possible
through a separately reviewed upload process). Vercel Blob live storage is not a
backup: retain recoverable object versions/manifests, separate credentials and
retention aligned to the database. Never expose backups via public Blob URLs.
Apply retention only after a newer verified restore point exists. Droplet snapshots
are supplemental; they are not a substitute for consistent DB/object backups.

The third PR only adds explicitly gated legacy-byte cleanup. Do not run it until
the documented observation period, off-server restore drill, complete checksum
verification and owner signoff are recorded. Never auto-drop columns at app startup.
Removing LONGBLOB values may not return filesystem space without a separate table
rebuild; plan any rebuild's temporary disk need and lock behavior independently.
